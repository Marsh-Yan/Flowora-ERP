package com.flowora.erp.trade.v2;

import com.flowora.erp.identity.DataScope;
import com.flowora.erp.identity.FloworaPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@Profile("local | production")
public class OrderReadScope {
    private final JdbcTemplate jdbc;

    public OrderReadScope(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireSales(FloworaPrincipal actor, String id) {
        require(actor, id, "flowora_sales_order", "sales_user_id");
    }

    public void requirePurchase(FloworaPrincipal actor, String id) {
        require(actor, id, "flowora_purchase_order", "buyer_user_id");
    }

    private void require(FloworaPrincipal actor, String id, String table, String ownerColumn) {
        if (actor.dataScope() == DataScope.ALL) return;
        // Table and column are fixed by the two callers, never request input.
        String sql = "SELECT COUNT(*) FROM " + table + " orders WHERE orders.id = ? AND orders.organization_id = ? AND "
                + switch (actor.dataScope()) {
                    case SELF -> "orders." + ownerColumn + " = ?";
                    case DEPARTMENT -> "orders." + ownerColumn + " IN (SELECT user_id FROM flowora_organization_membership "
                            + "WHERE organization_id = ? AND department_id = ? AND status = 'ACTIVE')";
                    case ASSIGNED -> "FALSE";
                    case ALL -> "TRUE";
                };
        Object[] args = switch (actor.dataScope()) {
            case SELF -> new Object[] {id, actor.organizationId(), actor.userId()};
            case DEPARTMENT -> new Object[] {id, actor.organizationId(), actor.organizationId(), actor.departmentId()};
            case ASSIGNED, ALL -> new Object[] {id, actor.organizationId()};
        };
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        if (count == null || count == 0) throw new AccessDeniedException("Order outside data scope");
    }
}
