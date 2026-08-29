package com.flowora.erp.identity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class DemoPermissionCatalog {
    private static final List<String> ALL = List.of(
            "organization:view", "organization:configure", "user:view", "user:configure",
            "role:view", "role:configure", "audit:view", "master:view", "master:create",
            "master:edit", "master:export", "master:configure", "sales:view", "sales:create",
            "sales:submit", "procurement:view", "procurement:create", "procurement:submit",
            "inventory:view", "inventory:create", "inventory:post", "finance:view",
            "finance:create", "finance:post", "workflow:view", "workflow:approve",
            "project:view", "project:create"
    );

    private DemoPermissionCatalog() {
    }

    static List<String> forRoles(List<String> roles) {
        if (roles.contains("ADMIN")) return ALL;
        Set<String> result = new LinkedHashSet<>(List.of("master:view", "workflow:view"));
        if (roles.contains("BUSINESS")) result.addAll(List.of(
                "master:create", "master:edit", "master:export", "sales:view", "sales:create",
                "sales:submit", "procurement:view", "procurement:create", "procurement:submit"));
        if (roles.contains("WAREHOUSE")) result.addAll(List.of("inventory:view", "inventory:create", "inventory:post"));
        if (roles.contains("FINANCE")) result.addAll(List.of("finance:view", "finance:create", "finance:post"));
        if (roles.contains("PROJECT_MANAGER")) result.addAll(List.of("project:view", "project:create"));
        if (roles.contains("MANAGEMENT")) result.add("workflow:approve");
        return List.copyOf(result);
    }
}
