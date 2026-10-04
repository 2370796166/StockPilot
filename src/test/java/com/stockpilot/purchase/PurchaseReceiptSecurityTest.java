package com.stockpilot.purchase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockpilot.inventory.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.mapper.InventoryLedgerMapper;
import com.stockpilot.inventory.service.InventoryMutationApplicationService;
import com.stockpilot.masterdata.mapper.SkuMapper;
import com.stockpilot.masterdata.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.mapper.WarehouseMapper;
import com.stockpilot.masterdata.service.MasterDataReferenceApplicationService;
import com.stockpilot.messaging.service.TransactionalOutboxApplicationService;
import com.stockpilot.purchase.controller.PurchaseReceiptController;
import com.stockpilot.purchase.mapper.PurchaseReceiptLineMapper;
import com.stockpilot.purchase.mapper.PurchaseReceiptMapper;
import com.stockpilot.purchase.service.PurchaseReceiptApplicationService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        value = PurchaseReceiptController.class,
        properties = {
            "stockpilot.security.jwt-secret=01234567890123456789012345678901",
            "stockpilot.security.access-token-minutes=60"
        })
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    DatabaseUserDetailsService.class,
    PurchaseReceiptApplicationService.class,
    MasterDataReferenceApplicationService.class,
    InventoryMutationApplicationService.class
})
class PurchaseReceiptSecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockBean private UserMapper users;
    @MockBean private PurchaseReceiptMapper receipts;
    @MockBean private PurchaseReceiptLineMapper lines;
    @MockBean private WarehouseMapper warehouses;
    @MockBean private WarehouseLocationMapper locations;
    @MockBean private SkuMapper skus;
    @MockBean private InventoryBalanceMapper balances;
    @MockBean private InventoryLedgerMapper ledgers;
    @MockBean private TransactionalOutboxApplicationService outbox;

    @Test
    void readPermissionAllowsQueries() throws Exception {
        String token = tokenWith(List.of("PURCHASE_RECEIPT_READ"));
        var page =
                com.baomidou.mybatisplus.extension.plugins.pagination.Page
                        .<com.stockpilot.purchase.domain.PurchaseReceiptEntity>of(1, 20, 0);
        page.setRecords(List.of());
        when(receipts.selectPage(any(), any())).thenReturn(page);

        mvc.perform(
                        get("/api/inbound/purchase-receipts")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void insufficientPermissionRejectsWriteApproveAndComplete() throws Exception {
        String readOnly = tokenWith(List.of("PURCHASE_RECEIPT_READ"));
        mvc.perform(
                        post("/api/inbound/purchase-receipts")
                                .header("Authorization", "Bearer " + readOnly)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"receiptNo":"PR-SEC-1","warehouseId":1,
                                 "lines":[{"locationId":2,"skuId":3,"quantity":1}]}
                                """))
                .andExpect(status().isForbidden());

        when(users.findPermissionCodes(1L)).thenReturn(List.of("PURCHASE_RECEIPT_WRITE"));
        mvc.perform(
                        post("/api/inbound/purchase-receipts/1/approve")
                                .header("Authorization", "Bearer " + readOnly)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":1}"))
                .andExpect(status().isForbidden());

        when(users.findPermissionCodes(1L)).thenReturn(List.of("PURCHASE_RECEIPT_APPROVE"));
        mvc.perform(
                        post("/api/inbound/purchase-receipts/1/complete")
                                .header("Authorization", "Bearer " + readOnly))
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
