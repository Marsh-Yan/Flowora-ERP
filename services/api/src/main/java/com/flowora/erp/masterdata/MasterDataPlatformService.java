package com.flowora.erp.masterdata;

import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("local | production")
public class MasterDataPlatformService {
    private final JdbcTemplate jdbcTemplate;

    public MasterDataPlatformService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<LocationView> locations(String organizationId, String warehouseId) {
        return jdbcTemplate.query("""
                SELECT id, warehouse_id, parent_id, code, name, location_type, active, version_no
                FROM flowora_stock_location
                WHERE organization_id = ? AND (? IS NULL OR warehouse_id = ?)
                ORDER BY warehouse_id, code
                """, (rs, row) -> new LocationView(
                rs.getString("id"), rs.getString("warehouse_id"), rs.getString("parent_id"),
                rs.getString("code"), rs.getString("name"), LocationType.valueOf(rs.getString("location_type")),
                rs.getBoolean("active"), rs.getLong("version_no")
        ), organizationId, blankToNull(warehouseId), blankToNull(warehouseId));
    }

    @Transactional
    public LocationView createLocation(String organizationId, LocationCommand command) {
        requireWarehouse(organizationId, command.warehouseId());
        requireParentLocation(organizationId, command.warehouseId(), command.parentId());
        String id = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO flowora_stock_location (
                        id, organization_id, warehouse_id, parent_id, code, name, location_type, active
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                    """, id, organizationId, command.warehouseId(), blankToNull(command.parentId()),
                    command.code().trim().toUpperCase(), command.name().trim(), command.locationType().name());
        } catch (DuplicateKeyException exception) {
            duplicate("stockLocation", command.code());
        }
        return location(organizationId, id);
    }

    @Transactional
    public LocationView updateLocation(
            String organizationId,
            String locationId,
            long expectedVersion,
            LocationCommand command,
            boolean active
    ) {
        requireWarehouse(organizationId, command.warehouseId());
        requireParentLocation(organizationId, command.warehouseId(), command.parentId());
        int updated = jdbcTemplate.update("""
                UPDATE flowora_stock_location
                SET warehouse_id = ?, parent_id = ?, code = ?, name = ?, location_type = ?,
                    active = ?, version_no = version_no + 1
                WHERE id = ? AND organization_id = ? AND version_no = ?
                """, command.warehouseId(), blankToNull(command.parentId()), command.code().trim().toUpperCase(),
                command.name().trim(), command.locationType().name(), active,
                locationId, organizationId, expectedVersion);
        if (updated != 1) optimisticOrNotFound(organizationId, "flowora_stock_location", locationId);
        return location(organizationId, locationId);
    }

    @Transactional
    public ItemTrackingView updateItemTracking(
            String organizationId,
            String itemId,
            long expectedVersion,
            TrackingMethod trackingMethod
    ) {
        Integer movements = jdbcTemplate.queryForObject("""
                SELECT (SELECT COUNT(*) FROM flowora_stock_ledger_entry WHERE organization_id=? AND item_id=?)
                     + (SELECT COUNT(*) FROM flowora_stock_movement_line WHERE organization_id=? AND item_id=?)
                """, Integer.class, organizationId, itemId, organizationId, itemId);
        String current = jdbcTemplate.query("""
                SELECT tracking_method FROM flowora_item WHERE id = ? AND organization_id = ?
                """, rs -> rs.next() ? rs.getString("tracking_method") : null, itemId, organizationId);
        if (current == null) notFound("item", itemId);
        if (movements != null && movements > 0 && !current.equals(trackingMethod.name())) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "REFERENCE_CONFLICT", "errors.referenceConflict",
                    Map.of("resource", "itemTracking", "id", itemId));
        }
        int updated = jdbcTemplate.update("""
                UPDATE flowora_item SET tracking_method = ?, version_no = version_no + 1
                WHERE id = ? AND organization_id = ? AND version_no = ?
                """, trackingMethod.name(), itemId, organizationId, expectedVersion);
        if (updated != 1) optimisticOrNotFound(organizationId, "flowora_item", itemId);
        return jdbcTemplate.queryForObject("""
                SELECT id, tracking_method, version_no FROM flowora_item
                WHERE id = ? AND organization_id = ?
                """, (rs, row) -> new ItemTrackingView(
                rs.getString("id"), TrackingMethod.valueOf(rs.getString("tracking_method")),
                rs.getLong("version_no")
        ), itemId, organizationId);
    }

    @Transactional(readOnly = true)
    public List<TaxRuleView> taxRules(String organizationId) {
        return jdbcTemplate.query("""
                SELECT id, code, name, active, version_no FROM flowora_tax_rule
                WHERE organization_id = ? ORDER BY code
                """, (rs, row) -> new TaxRuleView(
                rs.getString("id"), rs.getString("code"), rs.getString("name"), rs.getBoolean("active"),
                rs.getLong("version_no"), components(organizationId, rs.getString("id"))
        ), organizationId);
    }

    @Transactional
    public TaxRuleView createTaxRule(String organizationId, TaxRuleCommand command) {
        if (command.components() == null || command.components().isEmpty()) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "errors.validation");
        }
        String ruleId = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO flowora_tax_rule (id, organization_id, code, name, active)
                    VALUES (?, ?, ?, ?, TRUE)
                    """, ruleId, organizationId, command.code().trim().toUpperCase(), command.name().trim());
            int sequence = 1;
            for (TaxComponentCommand component : command.components()) {
                jdbcTemplate.update("""
                        INSERT INTO flowora_tax_component (
                            id, organization_id, tax_rule_id, code, name, rate, sequence_no, compound
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID().toString(), organizationId, ruleId,
                        component.code().trim().toUpperCase(), component.name().trim(), component.rate(),
                        sequence++, component.compound());
            }
        } catch (DuplicateKeyException exception) {
            duplicate("taxRule", command.code());
        }
        return taxRules(organizationId).stream().filter(rule -> rule.id().equals(ruleId)).findFirst().orElseThrow();
    }

    private List<TaxComponentView> components(String organizationId, String ruleId) {
        return jdbcTemplate.query("""
                SELECT id, code, name, rate, sequence_no, compound
                FROM flowora_tax_component
                WHERE organization_id = ? AND tax_rule_id = ? ORDER BY sequence_no
                """, (rs, row) -> new TaxComponentView(
                rs.getString("id"), rs.getString("code"), rs.getString("name"),
                rs.getBigDecimal("rate"), rs.getInt("sequence_no"), rs.getBoolean("compound")
        ), organizationId, ruleId);
    }

    private LocationView location(String organizationId, String id) {
        List<LocationView> found = jdbcTemplate.query("""
                SELECT id, warehouse_id, parent_id, code, name, location_type, active, version_no
                FROM flowora_stock_location WHERE organization_id = ? AND id = ?
                """, (rs, row) -> new LocationView(
                rs.getString("id"), rs.getString("warehouse_id"), rs.getString("parent_id"),
                rs.getString("code"), rs.getString("name"), LocationType.valueOf(rs.getString("location_type")),
                rs.getBoolean("active"), rs.getLong("version_no")
        ), organizationId, id);
        if (found.isEmpty()) notFound("stockLocation", id);
        return found.getFirst();
    }

    private void requireWarehouse(String organizationId, String warehouseId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flowora_warehouse
                WHERE id = ? AND organization_id = ? AND active = TRUE
                """, Integer.class, warehouseId, organizationId);
        if (count == null || count == 0) notFound("warehouse", warehouseId);
    }

    private void requireParentLocation(String organizationId, String warehouseId, String parentId) {
        if (parentId == null || parentId.isBlank()) return;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flowora_stock_location
                WHERE id = ? AND organization_id = ? AND warehouse_id = ?
                """, Integer.class, parentId, organizationId, warehouseId);
        if (count == null || count == 0) notFound("stockLocation", parentId);
    }

    private void optimisticOrNotFound(String organizationId, String table, String id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ? AND organization_id = ?",
                Integer.class, id, organizationId);
        if (count == null || count == 0) notFound("masterData", id);
        throw new PlatformApiException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "errors.optimisticLockConflict",
                Map.of("id", id));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void notFound(String resource, String id) {
        throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound",
                Map.of("resource", resource, "id", id));
    }

    private void duplicate(String resource, String code) {
        throw new PlatformApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "errors.duplicateCode",
                Map.of("resource", resource, "code", code));
    }

    public record LocationCommand(
            String warehouseId, String parentId, String code, String name, LocationType locationType
    ) {}
    public record LocationView(
            String id, String warehouseId, String parentId, String code, String name,
            LocationType locationType, boolean active, long version
    ) {}
    public record ItemTrackingView(String id, TrackingMethod trackingMethod, long version) {}
    public record TaxComponentCommand(String code, String name, BigDecimal rate, boolean compound) {}
    public record TaxRuleCommand(String code, String name, List<TaxComponentCommand> components) {}
    public record TaxComponentView(
            String id, String code, String name, BigDecimal rate, int sequence, boolean compound
    ) {}
    public record TaxRuleView(
            String id, String code, String name, boolean active, long version, List<TaxComponentView> components
    ) {}
}
