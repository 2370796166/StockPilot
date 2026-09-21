package com.stockpilot.security;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.stockpilot.security.auth.*;
import com.stockpilot.security.config.SecurityConfig;
import com.stockpilot.security.domain.*;
import com.stockpilot.security.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        value = TestProtectedController.class,
        properties = {
            "stockpilot.security.jwt-secret=01234567890123456789012345678901",
            "stockpilot.security.access-token-minutes=60"
        })
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    DatabaseUserDetailsService.class
})
class SecurityFilterChainTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean UserMapper users;

    @Test
    void missingTokenReturnsUnified401() throws Exception {
        mvc.perform(get("/api/security/users/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SECURITY_401"));
    }

    @Test
    void illegalTokenReturnsUnified401() throws Exception {
        mvc.perform(get("/api/security/users/1").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SECURITY_401"));
    }

    @Test
    void readPermissionAllowsAndMissingPermissionReturns403() throws Exception {
        String token = tokenWith("SECURITY_USER_READ");
        mvc.perform(get("/api/security/users/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        when(users.findPermissionCodes(1L)).thenReturn(List.of());
        mvc.perform(get("/api/security/users/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SECURITY_403"));
    }

    @Test
    void ordinaryUserWriteCannotGrantRolesButGrantPermissionCan() throws Exception {
        String token = tokenWith("SECURITY_USER_WRITE");
        mvc.perform(put("/api/security/users/1/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SECURITY_403"));
        when(users.findPermissionCodes(1L)).thenReturn(List.of("SECURITY_GRANT"));
        mvc.perform(put("/api/security/users/1/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUnknownEndpointIsDeniedByDefault() throws Exception {
        String token = tokenWith("SECURITY_USER_READ");
        mvc.perform(get("/future-unannotated-endpoint").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledUserExistingTokenIsRejectedImmediately() throws Exception {
        String token = tokenWith("SECURITY_USER_READ");
        UserEntity disabled = new UserEntity();
        disabled.setId(1L);
        disabled.setUsername("alice");
        disabled.setStatus(SecurityStatus.DISABLED);
        when(users.selectById(1L)).thenReturn(disabled);
        mvc.perform(get("/api/security/users/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SECURITY_401"));
    }

    @Test
    void currentUserEndpointRequiresLoginButNoBusinessPermission() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        String token = tokenWith("SECURITY_USER_READ");
        when(users.findPermissionCodes(1L)).thenReturn(List.of());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    private String tokenWith(String permission) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("alice");
        user.setStatus(SecurityStatus.ENABLED);
        when(users.selectById(1L)).thenReturn(user);
        when(users.findPermissionCodes(1L)).thenReturn(List.of(permission));
        return jwt.issue(1, "alice");
    }
}
