package com.gotham.command.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaWebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET / should forward to /index.html")
    void testRootForwardToIndexHtml() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("GET /auth/callback should forward to /index.html")
    void testAuthCallbackForwardToIndexHtml() throws Exception {
        mockMvc.perform(get("/auth/callback"))
            .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("GET /api/health should not be intercepted by SPA forward")
    void testApiHealthNotIntercepted() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/auth/refresh should return 401 when missing cookie and not forward to index.html")
    void testApiRefreshNotIntercepted() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/nonexistent should return 401 when unauthenticated and not forward to index.html")
    void testUnauthenticatedNonexistentApiReturns401() throws Exception {
        mockMvc.perform(get("/api/nonexistent"))
            .andExpect(status().isUnauthorized());
    }
}
