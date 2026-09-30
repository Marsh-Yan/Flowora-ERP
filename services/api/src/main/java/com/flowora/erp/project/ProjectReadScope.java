package com.flowora.erp.project;

import com.flowora.erp.identity.DataScope;
import com.flowora.erp.identity.FloworaPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@Profile("local | production")
public class ProjectReadScope {
    private final JdbcTemplate jdbc;

    public ProjectReadScope(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void require(FloworaPrincipal actor, String projectId) {
        if (actor.dataScope() == DataScope.ALL) return;
        Integer visible = jdbc.queryForObject("""
                SELECT COUNT(*) FROM flowora_project p
                WHERE p.id = ? AND p.organization_id = ?
                  AND ((? = 'SELF' AND p.manager_user_id = ?)
                    OR (? = 'DEPARTMENT' AND p.department_id = ?)
                    OR (? = 'ASSIGNED' AND EXISTS (
                        SELECT 1 FROM flowora_project_member m
                        WHERE m.project_id = p.id AND m.organization_id = ?
                          AND m.user_id = ? AND m.active = TRUE)))
                """, Integer.class, projectId, actor.organizationId(),
                actor.dataScope().name(), actor.userId(),
                actor.dataScope().name(), actor.departmentId(),
                actor.dataScope().name(), actor.organizationId(), actor.userId());
        if (visible == null || visible == 0) throw new AccessDeniedException("Project outside data scope");
    }
}
