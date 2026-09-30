-- Read-only preflight for V17. Stop all business writers before comparing balances.
SELECT old.organization_id,old.warehouse_id,old.item_id,
       old.quantity legacy_quantity,old.quantity*old.average_cost legacy_value,
       COALESCE(current.quantity,0) v2_quantity,COALESCE(current.value_amount,0) v2_value
FROM flowora_stock_balance old
LEFT JOIN (SELECT organization_id,warehouse_id,item_id,SUM(on_hand_quantity) quantity,
                  SUM(on_hand_quantity*average_cost) value_amount
           FROM flowora_inventory_balance_v2 GROUP BY organization_id,warehouse_id,item_id) current
  ON current.organization_id=old.organization_id AND current.warehouse_id=old.warehouse_id AND current.item_id=old.item_id
ORDER BY old.organization_id,old.warehouse_id,old.item_id;

WITH signed AS (
    SELECT line.organization_id,line.item_id,line.to_warehouse_id warehouse_id,
           movement.source_type,movement.source_id,line.quantity quantity_delta,line.value_amount value_delta
    FROM flowora_stock_movement_line line JOIN flowora_stock_movement movement ON movement.id=line.movement_id
    WHERE line.to_warehouse_id IS NOT NULL AND movement.status='POSTED'
    UNION ALL
    SELECT line.organization_id,line.item_id,line.from_warehouse_id,movement.source_type,movement.source_id,
           -line.quantity,-line.value_amount
    FROM flowora_stock_movement_line line JOIN flowora_stock_movement movement ON movement.id=line.movement_id
    WHERE line.from_warehouse_id IS NOT NULL AND movement.status='POSTED'
), represented AS (
    SELECT organization_id,warehouse_id,item_id,source_type,source_id,
           SUM(quantity_delta) quantity_delta,SUM(value_delta) value_delta
    FROM signed GROUP BY organization_id,warehouse_id,item_id,source_type,source_id
)
SELECT old.organization_id,old.warehouse_id,old.item_id,old.document_type,old.document_id,
       old.quantity_delta legacy_delta,old.value_delta legacy_value_delta,
       represented.quantity_delta represented_delta,represented.value_delta represented_value_delta,
       CASE WHEN represented.quantity_delta IS NULL THEN 'UNREPRESENTED'
            WHEN represented.quantity_delta=old.quantity_delta AND ABS(represented.value_delta-old.value_delta)<=0.0001
            THEN 'ALREADY_REPRESENTED' ELSE 'REVIEW_REQUIRED' END classification
FROM flowora_stock_ledger_entry old LEFT JOIN represented
  ON represented.organization_id=old.organization_id AND represented.warehouse_id=old.warehouse_id
 AND represented.item_id=old.item_id AND represented.source_type=old.document_type AND represented.source_id=old.document_id
ORDER BY old.organization_id,old.warehouse_id,old.item_id,old.created_at;
