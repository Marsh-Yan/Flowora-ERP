-- Validate both directions: a movement with a missing balance must not be silently
-- omitted merely because it has no corresponding row in the summary view.
DELIMITER //
CREATE PROCEDURE flowora_check_inventory_cutover()
BEGIN
    DECLARE mismatches INT DEFAULT 0;
    SELECT COUNT(*) INTO mismatches FROM (
        SELECT organization_id,warehouse_id,item_id,SUM(quantity) difference
        FROM (
            SELECT organization_id,warehouse_id,item_id,quantity FROM flowora_inventory_summary_v2
            UNION ALL
            SELECT organization_id,warehouse_id,item_id,-quantity_delta FROM flowora_inventory_delta_v2
        ) signed_quantity GROUP BY organization_id,warehouse_id,item_id HAVING SUM(quantity)<>0
    ) discrepancies;
    IF mismatches>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Inventory cutover: canonical balance and ledger quantities differ; reconcile before startup';
    END IF;
END//
DELIMITER ;
CALL flowora_check_inventory_cutover();
DROP PROCEDURE flowora_check_inventory_cutover;
