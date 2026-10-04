package com.stockpilot.sales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockpilot.inventory.mapper.*;
import com.stockpilot.inventory.service.InventoryMutationApplicationService;
import com.stockpilot.masterdata.mapper.SkuMapper;
import com.stockpilot.masterdata.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.mapper.WarehouseMapper;
import com.stockpilot.masterdata.service.MasterDataReferenceApplicationService;
import com.stockpilot.messaging.service.TransactionalOutboxApplicationService;
import com.stockpilot.sales.controller.SalesOutboundController;
import com.stockpilot.sales.mapper.*;
import com.stockpilot.sales.service.SalesOutboundApplicationService;
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
        value = SalesOutboundController.class,
        properties = {
            "stockpilot.security.jwt-secret=01234567890123456789012345678901",
            "stockpilot.security.access-token-minutes=60"
        })
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    DatabaseUserDetailsService.class,
    SalesOutboundApplicationService.class,
    MasterDataReferenceApplicationService.class,
    InventoryMutationApplicationService.class
})
class SalesOutboundSecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockBean private UserMapper users;
    @MockBean private SalesOutboundMapper outbounds;
    @MockBean private SalesOutboundLineMapper lines;
    @MockBean private WarehouseMapper warehouses;
    @MockBean private WarehouseLocationMapper locations;
    @MockBean private SkuMapper skus;
    @MockBean private InventoryBalanceMapper balances;
    @MockBean private InventoryLedgerMapper ledgers;
    @MockBean private TransactionalOutboxApplicationService outbox;

    @Test
    void readPermissionAllowsQueryButCannotMutate() throws Exception {
        String token = tokenWith(List.of("SALES_OUTBOUND_READ"));
        var page =
                com.baomidou.mybatisplus.extension.plugins.pagination.Page
                        .<com.stockpilot.sales.domain.SalesOutboundEntity>of(1, 20, 0);
        page.setRecords(List.of());
        when(outbounds.selectPage(any(), any())).thenReturn(page);
        mvc.perform(get("/api/outbound/sales-orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/outbound/sales-orders/1/reserve")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/outbound/sales-orders/1/cancel")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void writePermissionCannotApproveOrComplete() throws Exception {
        String token = tokenWith(List.of("SALES_OUTBOUND_WRITE"));
        mvc.perform(
                        post("/api/outbound/sales-orders/1/approve")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/outbound/sales-orders/1/complete")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
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
}
