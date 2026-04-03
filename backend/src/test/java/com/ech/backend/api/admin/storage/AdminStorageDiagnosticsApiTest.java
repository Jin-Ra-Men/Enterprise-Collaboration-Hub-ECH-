package com.ech.backend.api.admin.storage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ech.backend.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

class AdminStorageDiagnosticsApiTest extends BaseIntegrationTest {

    @Test
    void probe_as_admin_returns_writable_for_test_profile_storage() throws Exception {
        mockMvc.perform(get("/api/admin/storage/probe")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.writable").value(true))
                .andExpect(jsonPath("$.data.detail").value("ok"))
                .andExpect(jsonPath("$.data.uncPath").value(false));
    }
}
