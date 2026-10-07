package com.flowora.erp.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.flowora.erp.config.SecurityConfig;
import com.flowora.erp.identity.DemoUserStore;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.autoconfigure.ImportAutoConfiguration(org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration.class)
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, DemoUserStore.class})
class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsHealthyServicePayload() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.service").value("flowora-api"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.requestId").isString());
    }
}
