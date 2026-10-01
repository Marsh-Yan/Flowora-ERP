-- Stop all business writers before applying this migration. Old tables remain archives.
CREATE VIEW flowora_inventory_summary_v2 AS
SELECT MIN(id) id, organization_id, warehouse_id, item_id,
       SUM(on_hand_quantity) quantity,
       SUM(on_hand_quantity * average_cost) inventory_value,
       CASE WHEN SUM(on_hand_quantity)=0 THEN 0
            ELSE SUM(on_hand_quantity * average_cost)/SUM(on_hand_quantity) END average_cost
FROM flowora_inventory_balance_v2 GROUP BY organization_id, warehouse_id, item_id;

CREATE VIEW flowora_inventory_delta_v2 AS
SELECT CONCAT(line.id, ':IN') id, line.organization_id, line.to_warehouse_id warehouse_id,
       line.item_id, movement.movement_type, movement.source_type document_type,
       movement.source_id document_id, line.quantity quantity_delta, line.unit_cost,
       line.value_amount value_delta, movement.actor_user_id, movement.posted_at created_at
FROM flowora_stock_movement_line line JOIN flowora_stock_movement movement ON movement.id=line.movement_id
WHERE line.to_warehouse_id IS NOT NULL AND movement.status='POSTED'
UNION ALL
SELECT CONCAT(line.id, ':OUT'), line.organization_id, line.from_warehouse_id, line.item_id,
       movement.movement_type, movement.source_type, movement.source_id, -line.quantity,
       line.unit_cost, -line.value_amount, movement.actor_user_id, movement.posted_at
FROM flowora_stock_movement_line line JOIN flowora_stock_movement movement ON movement.id=line.movement_id
WHERE line.from_warehouse_id IS NOT NULL AND movement.status='POSTED';

CREATE TABLE flowora_inventory_cutover (
    legacy_balance_id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    legacy_quantity DECIMAL(19,4) NOT NULL,
    legacy_value DECIMAL(23,8) NOT NULL,
    represented_quantity DECIMAL(19,4) NOT NULL,
    represented_value DECIMAL(23,8) NOT NULL,
    imported_quantity DECIMAL(19,4) NOT NULL,
    imported_value DECIMAL(23,8) NOT NULL,
    movement_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DELIMITER //
CREATE PROCEDURE flowora_cutover_inventory()
BEGIN
    DECLARE ambiguous INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;

    -- An overlapping source must represent the complete legacy transaction. A partial
    -- or differently valued overlap cannot safely be interpreted as an opening balance.
    SELECT COUNT(*) INTO ambiguous FROM flowora_stock_ledger_entry old
    JOIN (SELECT organization_id,warehouse_id,item_id,document_type,document_id,
                 SUM(quantity_delta) quantity_delta,SUM(value_delta) value_delta
          FROM flowora_inventory_delta_v2 GROUP BY organization_id,warehouse_id,item_id,document_type,document_id) represented
      ON represented.organization_id=old.organization_id AND represented.warehouse_id=old.warehouse_id
     AND represented.item_id=old.item_id AND represented.document_type=old.document_type AND represented.document_id=old.document_id
    WHERE represented.quantity_delta<>old.quantity_delta OR ABS(represented.value_delta-old.value_delta)>0.0001;
    IF ambiguous>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Inventory cutover: ambiguous overlapping sources; reconcile before upgrade'; END IF;

    -- Existing v2 stock with no supporting movements could be a prior manual backfill.
    -- Reject it rather than adding the same opening stock again.
    SELECT COUNT(*) INTO ambiguous FROM flowora_inventory_summary_v2 balance
    LEFT JOIN (SELECT organization_id,warehouse_id,item_id,SUM(quantity_delta) quantity
               FROM flowora_inventory_delta_v2 GROUP BY organization_id,warehouse_id,item_id) ledger
      ON ledger.organization_id=balance.organization_id AND ledger.warehouse_id=balance.warehouse_id AND ledger.item_id=balance.item_id
    WHERE balance.quantity<>COALESCE(ledger.quantity,0);
    IF ambiguous>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Inventory cutover: v2 balance differs from movements; reconcile before upgrade'; END IF;

    START TRANSACTION;
    INSERT INTO flowora_inventory_cutover
      (legacy_balance_id,organization_id,warehouse_id,item_id,legacy_quantity,legacy_value,
       represented_quantity,represented_value,imported_quantity,imported_value,movement_id)
    SELECT old.id,old.organization_id,old.warehouse_id,old.item_id,old.quantity,old.quantity*old.average_cost,
           COALESCE(shared.quantity,0),COALESCE(shared.value_amount,0),
           old.quantity-COALESCE(shared.quantity,0),old.quantity*old.average_cost-COALESCE(shared.value_amount,0),UUID()
    FROM flowora_stock_balance old
    LEFT JOIN (
        SELECT legacy.organization_id,legacy.warehouse_id,legacy.item_id,
               SUM(legacy.quantity_delta) quantity,SUM(legacy.value_delta) value_amount
        FROM flowora_stock_ledger_entry legacy WHERE EXISTS (
            SELECT 1 FROM flowora_inventory_delta_v2 represented
            WHERE represented.organization_id=legacy.organization_id AND represented.warehouse_id=legacy.warehouse_id
              AND represented.item_id=legacy.item_id AND represented.document_type=legacy.document_type AND represented.document_id=legacy.document_id)
        GROUP BY legacy.organization_id,legacy.warehouse_id,legacy.item_id
    ) shared ON shared.organization_id=old.organization_id AND shared.warehouse_id=old.warehouse_id AND shared.item_id=old.item_id;

    SELECT COUNT(*) INTO ambiguous FROM flowora_inventory_cutover cut
    LEFT JOIN flowora_inventory_balance_v2 balance ON balance.organization_id=cut.organization_id
      AND balance.warehouse_id=cut.warehouse_id AND balance.item_id=cut.item_id
      AND balance.location_id='' AND balance.lot_id='' AND balance.serial_id=''
    WHERE (cut.imported_quantity=0 AND ABS(cut.imported_value)>0.0001)
       OR cut.imported_quantity*cut.imported_value<0
       OR COALESCE(balance.on_hand_quantity,0)+cut.imported_quantity<COALESCE(balance.reserved_quantity,0)
       OR COALESCE(balance.on_hand_quantity*balance.average_cost,0)+cut.imported_value<0;
    IF ambiguous>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Inventory cutover: correction requires reviewed dimension or valuation adjustment'; END IF;

    INSERT INTO flowora_inventory_balance_v2 (id,organization_id,warehouse_id,item_id,on_hand_quantity,average_cost)
    SELECT UUID(),organization_id,warehouse_id,item_id,0,0 FROM flowora_inventory_cutover
    ON DUPLICATE KEY UPDATE id=id;

    UPDATE flowora_inventory_balance_v2 balance JOIN flowora_inventory_cutover cut
      ON balance.organization_id=cut.organization_id AND balance.warehouse_id=cut.warehouse_id AND balance.item_id=cut.item_id
      AND balance.location_id='' AND balance.lot_id='' AND balance.serial_id=''
    SET balance.average_cost=CASE WHEN balance.on_hand_quantity+cut.imported_quantity=0 THEN 0
          ELSE (balance.on_hand_quantity*balance.average_cost+cut.imported_value)/(balance.on_hand_quantity+cut.imported_quantity) END,
        balance.on_hand_quantity=balance.on_hand_quantity+cut.imported_quantity,
        balance.version_no=balance.version_no+1;

    INSERT INTO flowora_stock_movement (id,organization_id,number,movement_type,source_type,source_id,actor_user_id,request_id)
    SELECT movement_id,organization_id,CONCAT('OPEN-',LEFT(movement_id,8)),'OPENING','INVENTORY_CUTOVER',legacy_balance_id,
           'system:inventory-cutover',CONCAT('inventory-cutover:',legacy_balance_id)
    FROM flowora_inventory_cutover WHERE imported_quantity<>0;
    INSERT INTO flowora_stock_movement_line
      (id,organization_id,movement_id,sequence_no,item_id,from_warehouse_id,to_warehouse_id,quantity,unit_cost,value_amount,source_line_type,source_line_id)
    SELECT UUID(),organization_id,movement_id,1,item_id,
           CASE WHEN imported_quantity<0 THEN warehouse_id ELSE NULL END,
           CASE WHEN imported_quantity>0 THEN warehouse_id ELSE NULL END,
           ABS(imported_quantity),ABS(imported_value/imported_quantity),ABS(imported_value),'LEGACY_BALANCE',legacy_balance_id
    FROM flowora_inventory_cutover WHERE imported_quantity<>0;
    COMMIT;
END//
DELIMITER ;
CALL flowora_cutover_inventory();
DROP PROCEDURE flowora_cutover_inventory;

CREATE VIEW flowora_inventory_ledger_v2 AS
SELECT delta.*,
       SUM(quantity_delta) OVER (PARTITION BY organization_id,warehouse_id,item_id ORDER BY created_at,id ROWS UNBOUNDED PRECEDING) balance_quantity,
       SUM(value_delta) OVER (PARTITION BY organization_id,warehouse_id,item_id ORDER BY created_at,id ROWS UNBOUNDED PRECEDING) balance_value
FROM flowora_inventory_delta_v2 delta;
