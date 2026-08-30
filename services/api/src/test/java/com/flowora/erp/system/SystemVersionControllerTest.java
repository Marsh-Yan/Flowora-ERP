package com.flowora.erp.system;

import com.flowora.erp.config.SecurityConfig;
import com.flowora.erp.identity.DemoUserStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemVersionController.class)
@Import({SecurityConfig.class, DemoUserStore.class})
class SystemVersionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsPublicVersionAndCompatibilityPayload() throws Exception {
        mockMvc.perform(get("/api/v2/system/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productVersion").value("2.0.0-SNAPSHOT"))
                .andExpect(jsonPath("$.data.apiVersion").value("v2"))
                .andExpect(jsonPath("$.data.legacyApiVersion").value("v1"))
                .andExpect(jsonPath("$.data.deliveryStage").value("M3"))
                .andExpect(jsonPath("$.data.v2BusinessWritesEnabled").value(false))
                .andExpect(jsonPath("$.data.compatibility[0]").value("v1-auth-session"))
                .andExpect(jsonPath("$.data.compatibility[1]").value("v1-lossless-read"))
                .andExpect(jsonPath("$.requestId").isString());
    }
}
