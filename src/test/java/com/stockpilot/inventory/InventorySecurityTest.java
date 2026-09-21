package com.stockpilot.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockpilot.inventory.controller.InventoryQueryController;
import com.stockpilot.inventory.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.mapper.InventoryLedgerMapper;
import com.stockpilot.inventory.service.InventoryQueryApplicationService;
import com.stockpilot.security.auth.DatabaseUserDetailsService;
import com.stockpilot.security.auth.JwtAuthenticationFilter;
import com.stockpilot.security.auth.JwtService;
import com.stockpilot.security.config.SecurityConfig;
import com.stockpilot.security.domain.SecurityStatus;
import com.stockpilot.security.domain.UserEntity;
import com.stockpilot.security.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        value = InventoryQueryController.class,
        properties = {
            "stockpilot.security.jwt-secret=01234567890123456789012345678901",
            "stockpilot.security.access-token-minutes=60"
        })
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    DatabaseUserDetailsService.class,
    InventoryQueryApplicationService.class
})
class InventorySecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockBean private UserMapper users;
    @MockBean private InventoryBalanceMapper balances;
    @MockBean private InventoryLedgerMapper ledgers;

    @Test
    void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/api/inventory/balances"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SECURITY_401"));
    }

    @Test
    void inventoryReadPermissionControlsBothQueries() throws Exception {
        String token = tokenWith(List.of("MASTER_DATA_READ"));
        mvc.perform(get("/api/inventory/balances").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        when(users.findPermissionCodes(1L)).thenReturn(List.of("INVENTORY_READ"));
        when(balances.selectInventoryPage(any(), any())).thenReturn(emptyPage());
        when(ledgers.selectInventoryPage(any(), any())).thenReturn(emptyPage());

        mvc.perform(get("/api/inventory/balances").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/inventory/ledgers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void noPublicInventoryWriteEndpointExists() throws Exception {
        String token = tokenWith(List.of("INVENTORY_READ"));
        mvc.perform(post("/api/inventory/balances").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void paginationParametersAreValidated() throws Exception {
        String token = tokenWith(List.of("INVENTORY_READ"));
        mvc.perform(
                        get("/api/inventory/balances?page=0&size=101")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    private String tokenWith(List<String> permissions) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("alice");
        user.setStatus(SecurityStatus.ENABLED);
        when(users.selectById(1L)).thenReturn(user);
        when(users.findPermissionCodes(1L)).thenReturn(permissions);
        return jwt.issue(1, "alice");
    }

    private <T> com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> emptyPage() {
        var page = com.baomidou.mybatisplus.extension.plugins.pagination.Page.<T>of(1, 20, 0);
        page.setRecords(List.of());
        return page;
    }
}
