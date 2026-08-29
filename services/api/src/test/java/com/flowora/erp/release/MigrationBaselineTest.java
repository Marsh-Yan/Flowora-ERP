package com.flowora.erp.release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationBaselineTest {
    private static final Pattern MIGRATION_NAME = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final Map<Integer, PublishedMigration> PUBLISHED = Map.of(
            1, new PublishedMigration("V1__baseline.sql", "13314ff934b80a02cc4a83f98ef140323e0d69e23fdb7a92c1fdc94f5b768386"),
            2, new PublishedMigration("V2__platform_foundation.sql", "635c5326090e119be2d3b587ed72fd131955e50e2768ab6a8087dd9a971abf85"),
            3, new PublishedMigration("V3__master_data.sql", "821670ec74e9c22ef0ce23b19ba70ecedce824f12c1ed5113db6d83b504448c3"),
            4, new PublishedMigration("V4__workflow_collaboration.sql", "3993ae062d74b6658bc169b715428415b0588fb8d41f10b248e8044353f10a7f"),
            5, new PublishedMigration("V5__procurement_inventory.sql", "605a655ad2016c6cf33c4afba5d64abe2719a3db1f2c3307bda4456169d4bb0b"),
            6, new PublishedMigration("V6__sales_fulfillment.sql", "454daa6124f2d898ca2c88d1a3e1b9b5210b8c97c65f490360585bc39fbd0f4b"),
            7, new PublishedMigration("V7__finance_accounting.sql", "6b2c06f434c25f27530a613871fcec3478d509b198d3834d287b663ed9ad5236"),
            8, new PublishedMigration("V8__project_collaboration.sql", "3a364924081e6b95fbfa4ba65576fb15750f80010b5ef62b296ebe31341689cc"),
            9, new PublishedMigration("V9__quality_hardening.sql", "4584ddc06ecdd3e601b25a9016cecfe97a6bdcaf5705fe18ebea3389319d0b75")
    );

    @Test
    void publishedV1MigrationsRemainImmutableAndFutureMigrationsStartAtV10() throws Exception {
        Path migrationDirectory = Path.of("src", "main", "resources", "db", "migration");
        Map<Integer, Path> migrations = loadMigrations(migrationDirectory);

        assertThat(migrations.keySet()).containsAll(PUBLISHED.keySet());
        assertThat(migrations.keySet().stream().filter(version -> version < 10).toList())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);

        for (Map.Entry<Integer, PublishedMigration> entry : PUBLISHED.entrySet()) {
            Path path = migrations.get(entry.getKey());
            PublishedMigration expected = entry.getValue();
            assertThat(path.getFileName().toString()).isEqualTo(expected.fileName());
            assertThat(normalizedSha256(path))
                    .as("published migration %s must never be edited", expected.fileName())
                    .isEqualTo(expected.sha256());
        }
    }

    private Map<Integer, Path> loadMigrations(Path directory) throws IOException {
        Map<Integer, Path> migrations = new TreeMap<>();
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Matcher matcher = MIGRATION_NAME.matcher(path.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                int version = Integer.parseInt(matcher.group(1));
                assertThat(migrations.put(version, path))
                        .as("duplicate Flyway migration version V%s", version)
                        .isNull();
            }
        }
        return migrations;
    }

    private String normalizedSha256(Path path) throws IOException, NoSuchAlgorithmException {
        String content = Files.readString(path, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private record PublishedMigration(String fileName, String sha256) {
    }
}
