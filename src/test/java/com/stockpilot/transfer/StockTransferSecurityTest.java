package com.stockpilot.transfer;

import com.stockpilot.inventory.application.InventoryMutationApplicationService;
import com.stockpilot.inventory.infrastructure.mapper.*;
import com.stockpilot.masterdata.application.MasterDataReferenceApplicationService;
import com.stockpilot.masterdata.location.infrastructure.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.sku.infrastructure.mapper.SkuMapper;
import com.stockpilot.masterdata.warehouse.infrastructure.mapper.WarehouseMapper;
import com.stockpilot.security.auth.*;
import com.stockpilot.security.config.SecurityConfig;
import com.stockpilot.security.domain.*;
import com.stockpilot.security.infrastructure.mapper.UserMapper;
import com.stockpilot.transfer.application.StockTransferApplicationService;
import com.stockpilot.transfer.controller.StockTransferController;
import com.stockpilot.transfer.infrastructure.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value=StockTransferController.class,properties={"stockpilot.security.jwt-secret=01234567890123456789012345678901","stockpilot.security.access-token-minutes=60"})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,JwtService.class,DatabaseUserDetailsService.class,
 StockTransferApplicationService.class,MasterDataReferenceApplicationService.class,InventoryMutationApplicationService.class})
class StockTransferSecurityTest {
 @Autowired MockMvc mvc; @Autowired JwtService jwt;
 @MockBean UserMapper users; @MockBean StockTransferMapper transfers; @MockBean StockTransferLineMapper lines;
 @MockBean StockTransferTransitMapper transit; @MockBean WarehouseMapper warehouses;
 @MockBean WarehouseLocationMapper locations; @MockBean SkuMapper skus;
 @MockBean InventoryBalanceMapper balances; @MockBean InventoryLedgerMapper ledgers;
 @Test void readPermissionCannotMutate() throws Exception {
  String token=token(List.of("TRANSFER_READ")); var page=com.baomidou.mybatisplus.extension.plugins.pagination.Page.<com.stockpilot.transfer.domain.StockTransferEntity>of(1,20,0);page.setRecords(List.of());when(transfers.selectPage(any(),any())).thenReturn(page);
  mvc.perform(get("/api/transfers").header("Authorization","Bearer "+token)).andExpect(status().isOk());
  mvc.perform(post("/api/transfers/1/submit").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
 }
 @Test void writePermissionCannotApproveDispatchOrReceive() throws Exception {
  String token=token(List.of("TRANSFER_WRITE"));
  mvc.perform(post("/api/transfers/1/approve").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/transfers/1/dispatch").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  mvc.perform(post("/api/transfers/1/receive").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
 }
 private String token(List<String> permissions){UserEntity user=new UserEntity();user.setId(1L);user.setUsername("alice");user.setStatus(SecurityStatus.ENABLED);when(users.selectById(1L)).thenReturn(user);when(users.findPermissionCodes(1L)).thenReturn(permissions);return jwt.issue(1,"alice");}
}
