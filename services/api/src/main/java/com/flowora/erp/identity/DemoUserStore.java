package com.flowora.erp.identity;

import com.flowora.erp.common.api.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DemoUserStore implements UserDetailsService {
    private static final String DEMO_PASSWORD = "Demo123!";
    private final Map<String, DemoUser> users;
    private final PasswordEncoder passwordEncoder;

    public DemoUserStore(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.users = Map.of(
                "admin@demo.flowora", new DemoUser(
                        "user-demo-admin", "Demo Administrator", "Administrator", List.of("ADMIN"),
                        passwordEncoder.encode(DEMO_PASSWORD)
                ),
                "operator@demo.flowora", new DemoUser(
                        "user-demo-operator", "Demo Operator", "Operations", List.of("BUSINESS"),
                        passwordEncoder.encode(DEMO_PASSWORD)
                ),
                "warehouse@demo.flowora", new DemoUser(
                        "user-demo-warehouse", "Demo Warehouse", "Warehouse", List.of("WAREHOUSE"),
                        passwordEncoder.encode(DEMO_PASSWORD)
                ),
                "finance@demo.flowora", new DemoUser(
                        "user-demo-finance", "Demo Finance", "Finance", List.of("FINANCE"),
                        passwordEncoder.encode(DEMO_PASSWORD)
                ),
                "project@demo.flowora", new DemoUser(
                        "user-demo-project", "Demo Project Manager", "Projects", List.of("PROJECT_MANAGER"),
                        passwordEncoder.encode(DEMO_PASSWORD)
                ),
                "manager@demo.flowora", new DemoUser(
                        "user-demo-manager", "Demo Manager", "Management", List.of("MANAGEMENT"),
                        passwordEncoder.encode(DEMO_PASSWORD)
                )
        );
    }

    public FloworaPrincipal authenticate(String username, String password) {
        DemoUser user = users.get(normalize(username));
        if (user == null || !user.enabled() || password == null || !matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return new FloworaPrincipal(
                user.id(),
                normalize(username),
                user.displayName(),
                "org-demo",
                "Demo Organization",
                user.roles()
        );
    }

    private boolean matches(String password, String hash) {
        return passwordEncoder.matches(password, hash);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        DemoUser user = users.get(normalize(username));
        if (user == null) {
            throw new UsernameNotFoundException("Unknown demo user");
        }
        return org.springframework.security.core.userdetails.User.withUsername(normalize(username))
                .password(user.passwordHash())
                .roles(user.roles().toArray(String[]::new))
                .disabled(!user.enabled())
                .build();
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private record DemoUser(
            String id,
            String displayName,
            String department,
            List<String> roles,
            String passwordHash,
            boolean enabled
    ) {
        private DemoUser(String id, String displayName, String department, List<String> roles, String passwordHash) {
            this(id, displayName, department, roles, passwordHash, true);
        }
    }
}
