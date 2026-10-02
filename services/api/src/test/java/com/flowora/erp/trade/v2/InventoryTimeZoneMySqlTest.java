package com.flowora.erp.trade.v2;

import com.zaxxer.hikari.HikariDataSource;
import com.flowora.erp.inventory.CanonicalInventoryReader;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

/** Real profile pool configuration, actual canonical view, no historical time rewrite. */
@EnabledIfEnvironmentVariable(named = "FLOWORA_R2_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13306/audit_flowora\\?.*")
class InventoryTimeZoneMySqlTest {
    static Stream<Object[]> matrix() {
        return Stream.of("local", "production").flatMap(profile -> Stream.of("UTC", "Asia/Shanghai")
                .flatMap(jvm -> Stream.of("+00:00", "+08:00").map(session -> new Object[]{profile, jvm, session})));
    }

    @ParameterizedTest(name = "{0}, JVM={1}, historical session={2}")
    @MethodSource("matrix")
    void preservesHistoricalAndNewInstantsAcrossJvmAndSessionTimeZones(String profile, String jvm, String session) {
        var original = TimeZone.getDefault();
        String org = UUID.randomUUID().toString(), movement = UUID.randomUUID().toString();
        String password = Objects.requireNonNullElse(System.getenv("FLOWORA_R2_MYSQL_PASSWORD"), "");
        var raw = new JdbcTemplate(new DriverManagerDataSource(System.getenv("FLOWORA_R2_MYSQL_URL"), "root", password));
        long historicalEpoch = Instant.parse("2026-10-02T09:01:06Z").getEpochSecond();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(jvm));
            raw.update("INSERT INTO flowora_organization(id,name,base_currency_code) VALUES (?,'R5G timezone','USD')", org);
            // One connection: the SQL expression deliberately uses the writer's session semantics.
            raw.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                try (var statement = connection.createStatement()) { statement.execute("SET time_zone='" + session + "'"); }
                try (var insert = connection.prepareStatement("INSERT INTO flowora_stock_movement(id,organization_id,number,movement_type,source_type,source_id,actor_user_id,request_id,posted_at) VALUES (?,?,?,'COUNT','STOCK_COUNT',?,'r5g-test',?,FROM_UNIXTIME(?))")) {
                    for (int i = 1; i <= 5; i++) insert.setString(i, i == 2 ? org : movement);
                    insert.setLong(6, historicalEpoch); insert.executeUpdate();
                }
                return null;
            });
            raw.update("INSERT INTO flowora_stock_movement_line(id,organization_id,movement_id,sequence_no,item_id,to_warehouse_id,quantity,unit_cost,value_amount) VALUES (?,?,?,1,'r5g-item','r5g-warehouse',1,1,1)", UUID.randomUUID().toString(), org, movement);
            var yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource("application-" + profile + ".yml"));
            var properties = Objects.requireNonNull(yaml.getObject());
            try (var pool = new HikariDataSource()) {
                // Bind only temporal configuration; production pool sizing contains deployment placeholders.
                var temporal = new HashMap<String, Object>();
                properties.forEach((key, value) -> {
                    String name = key.toString();
                    if (name.equals("spring.datasource.hikari.connection-init-sql") || name.startsWith("spring.datasource.hikari.data-source-properties.")) temporal.put(name, value);
                });
                new Binder(new MapConfigurationPropertySource(temporal)).bind("spring.datasource.hikari", Bindable.ofInstance(pool));
                pool.setJdbcUrl(System.getenv("FLOWORA_R2_MYSQL_URL")); pool.setUsername("root"); pool.setPassword(password);
                pool.setMaximumPoolSize(1); pool.setMinimumIdle(0);
                var jdbc = new JdbcTemplate(pool);
                assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
                long stored = jdbc.queryForObject("SELECT UNIX_TIMESTAMP(posted_at) FROM flowora_stock_movement WHERE id=?", Long.class, movement);
                assertThat(stored).isEqualTo(historicalEpoch);
                var line = new CanonicalInventoryReader(jdbc).ledger(org, "", "", PageRequest.of(0, 50)).content().getFirst();
                assertThat(line.createdAt()).isEqualTo(Instant.ofEpochSecond(stored));
                // JDBC Timestamp writes must agree with the database epoch even under a non-UTC JVM.
                Instant requested = Instant.parse("2026-10-02T10:12:13Z");
                jdbc.update("UPDATE flowora_stock_movement SET posted_at=? WHERE id=?", Timestamp.from(requested), movement);
                assertThat(jdbc.queryForObject("SELECT UNIX_TIMESTAMP(posted_at) FROM flowora_stock_movement WHERE id=?", Long.class, movement)).isEqualTo(requested.getEpochSecond());
                assertThat(new CanonicalInventoryReader(jdbc).ledger(org, "", "", PageRequest.of(0, 50)).content().getFirst().createdAt()).isEqualTo(requested);
            }
        } finally {
            TimeZone.setDefault(original);
            raw.update("DELETE FROM flowora_stock_movement_line WHERE organization_id=?", org);
            raw.update("DELETE FROM flowora_stock_movement WHERE organization_id=?", org);
            raw.update("DELETE FROM flowora_organization WHERE id=?", org);
        }
    }
}
