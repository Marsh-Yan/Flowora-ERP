package com.flowora.erp.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "flowora.standalone.minimal=true")
class StandaloneStartupTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void startsWithoutDatabaseAndExposesOnlyDocumentedEndpoints() {
        ResponseEntity<String> health = http.getForEntity("/api/v1/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
        assertThat(http.getForEntity("/api/v2/system/version", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/api/v2/analytics/workspace", String.class).getStatusCode())
                .isNotEqualTo(HttpStatus.OK);
    }
}
