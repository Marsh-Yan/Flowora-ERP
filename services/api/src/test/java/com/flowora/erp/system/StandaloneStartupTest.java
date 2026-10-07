package com.flowora.erp.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "flowora.standalone.minimal=true")
class StandaloneStartupTest {
    @Autowired
    private TestRestTemplate http;

    @Autowired
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter mvc;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper json;

    @Test
    void retainsJacksonTwoHttpAndFinancialJsonContract() throws Exception {
        assertThat(mvc.getMessageConverters().stream()
                .filter(c -> c.canWrite(JsonContract.class, org.springframework.http.MediaType.APPLICATION_JSON))
                .findFirst().orElseThrow())
                .isInstanceOf(org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class);
        var value = new JsonContract(java.time.LocalDate.of(2026, 10, 7),
                java.time.Instant.parse("2026-10-07T01:02:03Z"), new java.math.BigDecimal("12.3400"));
        String encoded = json.writeValueAsString(value);
        assertThat(encoded).contains("\"date\":\"2026-10-07\"", "\"at\":\"2026-10-07T01:02:03Z\"", "\"amount\":12.3400");
        assertThat(json.readValue(encoded, JsonContract.class)).isEqualTo(value);
    }

    record JsonContract(java.time.LocalDate date, java.time.Instant at, java.math.BigDecimal amount) {}

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
