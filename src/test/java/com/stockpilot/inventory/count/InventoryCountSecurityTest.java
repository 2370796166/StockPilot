package com.stockpilot.inventory.count;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockpilot.inventory.count.controller.InventoryCountController;
import com.stockpilot.inventory.count.mapper.*;
import com.stockpilot.inventory.count.service.InventoryCountApplicationService;
import com.stockpilot.inventory.mapper.*;
import com.stockpilot.inventory.service.InventoryMutationApplicationService;
import com.stockpilot.masterdata.mapper.SkuMapper;
import com.stockpilot.masterdata.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.mapper.WarehouseMapper;
import com.stockpilot.masterdata.service.MasterDataReferenceApplicationService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        value = InventoryCountController.class,
        properties = {
            "stockpilot.security.jwt-secret=01234567890123456789012345678901",
            "stockpilot.security.access-token-minutes=60"
        })
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    DatabaseUserDetailsService.class,
    InventoryCountApplicationService.class,
    MasterDataReferenceApplicationService.class,
    InventoryMutationApplicationService.class
})
class InventoryCountSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean UserMapper users;
    @MockBean InventoryCountMapper counts;
    @MockBean InventoryCountLineMapper lines;
    @MockBean InventoryCountScopeMapper scopes;
    @MockBean WarehouseMapper warehouses;
    @MockBean WarehouseLocationMapper locations;
    @MockBean SkuMapper skus;
    @MockBean InventoryBalanceMapper balances;
    @MockBean InventoryLedgerMapper ledgers;

    @Test
    void readPermissionCannotWriteApproveOrAdjust() throws Exception {
        String token = token(List.of("INVENTORY_COUNT_READ"));
        var page =
                com.baomidou.mybatisplus.extension.plugins.pagination.Page
                        .<com.stockpilot.inventory.count.domain.InventoryCountEntity>of(1, 20, 0);
        page.setRecords(List.of());
        when(counts.selectPage(any(), any())).thenReturn(page);
        mvc.perform(get("/api/inventory-counts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/inventory-counts/1/start")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/inventory-counts/1/approve")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/inventory-counts/1/adjust")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void writePermissionCannotApproveOrAdjust() throws Exception {
        String token = token(List.of("INVENTORY_COUNT_WRITE"));
        mvc.perform(
                        post("/api/inventory-counts/1/approve")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/inventory-counts/1/adjust")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String token(List<String> permissions) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("alice");
        user.setStatus(SecurityStatus.ENABLED);
        when(users.selectById(1L)).thenReturn(user);
        when(users.findPermissionCodes(1L)).thenReturn(permissions);
        return jwt.issue(1, "alice");
    }
}
