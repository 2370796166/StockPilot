package com.stockpilot.inbound;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inbound.application.PurchaseReceiptApplicationService;
import com.stockpilot.inbound.domain.PurchaseReceiptEntity;
import com.stockpilot.inbound.domain.PurchaseReceiptLineEntity;
import com.stockpilot.inbound.domain.PurchaseReceiptStatus;
import com.stockpilot.inbound.infrastructure.mapper.PurchaseReceiptLineMapper;
import com.stockpilot.inbound.infrastructure.mapper.PurchaseReceiptMapper;
import com.stockpilot.inbound.request.PurchaseReceiptRequests;
import com.stockpilot.inventory.application.InventoryMutationApplicationService;
import com.stockpilot.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.infrastructure.mapper.InventoryLedgerMapper;
import com.stockpilot.masterdata.application.MasterDataReferenceApplicationService;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.location.domain.WarehouseLocationEntity;
import com.stockpilot.masterdata.location.infrastructure.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.sku.domain.SkuEntity;
import com.stockpilot.masterdata.sku.infrastructure.mapper.SkuMapper;
import com.stockpilot.masterdata.warehouse.domain.WarehouseEntity;
import com.stockpilot.masterdata.warehouse.infrastructure.mapper.WarehouseMapper;
import com.stockpilot.messaging.application.TransactionalOutboxApplicationService;
import com.stockpilot.security.auth.StockPilotPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseReceiptApplicationServiceTest {
    private PurchaseReceiptMapper receipts;
    private PurchaseReceiptLineMapper lines;
    private MasterDataReferenceApplicationService masterData;
    private WarehouseMapper warehouses;
    private WarehouseLocationMapper locations;
    private SkuMapper skus;
    private PurchaseReceiptApplicationService service;
    private final StockPilotPrincipal operator = new StockPilotPrincipal(7L, "operator");

    @BeforeEach
    void setUp() {
        receipts = mock(PurchaseReceiptMapper.class);
        lines = mock(PurchaseReceiptLineMapper.class);
        warehouses = mock(WarehouseMapper.class);
        locations = mock(WarehouseLocationMapper.class);
        skus = mock(SkuMapper.class);
        masterData = new MasterDataReferenceApplicationService(warehouses, locations, skus);
        when(warehouses.selectById(1L)).thenReturn(warehouse(1L, MasterDataStatus.ENABLED));
        when(locations.selectById(2L)).thenReturn(location(2L, 1L, MasterDataStatus.ENABLED));
        when(skus.selectById(3L)).thenReturn(sku(3L, MasterDataStatus.ENABLED));
        InventoryMutationApplicationService inventory = new InventoryMutationApplicationService(
                mock(InventoryBalanceMapper.class), mock(InventoryLedgerMapper.class), masterData);
        service = new PurchaseReceiptApplicationService(
                receipts, lines, masterData, inventory, mock(TransactionalOutboxApplicationService.class));
    }

    @Test
    void createsNormalDraft() {
        doAnswer(invocation -> {
            PurchaseReceiptEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            return 1;
        }).when(receipts).insert(any());
        when(lines.insertBatch(any())).thenReturn(1);
        PurchaseReceiptEntity saved = receipt(11L, PurchaseReceiptStatus.DRAFT, 0);
        when(receipts.selectById(11L)).thenReturn(saved);
        when(lines.selectByReceiptId(11L)).thenReturn(List.of(line(11L, "2.5000")));

        var result = service.create(create("PR-001", new BigDecimal("2.5000")), operator);

        assertEquals(PurchaseReceiptStatus.DRAFT, result.status());
        assertEquals("PR-001", result.receiptNo());
        assertEquals(new BigDecimal("2.5000"), result.lines().get(0).quantity());
        verify(warehouses).selectById(1L);
        verify(locations).selectById(2L);
        verify(skus).selectById(3L);
    }

    @Test
    void duplicateReceiptNumberIsRejectedByDatabaseConstraint() {
        doThrow(new DuplicateKeyException("uk_purchase_receipt_no"))
                .when(receipts).insert(any());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(create("PR-DUPLICATE", BigDecimal.ONE), operator));

        assertEquals("PURCHASE_RECEIPT_409_DUPLICATE", exception.getErrorCode().code());
        verify(lines, never()).insertBatch(any());
    }

    @Test
    void editsOnlyDraftAndReplacesLines() {
        when(receipts.selectByIdForUpdate(11L)).thenReturn(receipt(11L, PurchaseReceiptStatus.DRAFT, 0));
        when(receipts.updateDraft(11L, 0, 1L, "changed")).thenReturn(1);
        when(lines.insertBatch(any())).thenReturn(1);
        when(receipts.selectById(11L)).thenReturn(receipt(11L, PurchaseReceiptStatus.DRAFT, 1));
        when(lines.selectByReceiptId(11L)).thenReturn(List.of(line(11L, "4.0000")));

        var result = service.update(11L, new PurchaseReceiptRequests.Update(
                0, 1L, " changed ", List.of(requestLine(new BigDecimal("4.0000")))), operator);

        assertEquals(1, result.version());
        verify(lines).deleteByReceiptId(11L);
        verify(lines).insertBatch(any());
    }

    @Test
    void submitsDraftAndApprovesOnlySubmitted() {
        when(receipts.selectByIdForUpdate(11L))
                .thenReturn(receipt(11L, PurchaseReceiptStatus.DRAFT, 0))
                .thenReturn(receipt(11L, PurchaseReceiptStatus.SUBMITTED, 1));
        when(lines.countByReceiptId(11L)).thenReturn(1L);
        when(receipts.submit(11L, 0, 7L, "operator")).thenReturn(1);
        when(receipts.approve(11L, 1, 8L, "auditor")).thenReturn(1);
        when(receipts.selectById(11L))
                .thenReturn(receipt(11L, PurchaseReceiptStatus.SUBMITTED, 1))
                .thenReturn(receipt(11L, PurchaseReceiptStatus.APPROVED, 2));
        when(lines.selectByReceiptId(11L)).thenReturn(List.of(line(11L, "1.0000")));

        assertEquals(PurchaseReceiptStatus.SUBMITTED,
                service.submit(11L, new PurchaseReceiptRequests.Transition(0), operator).status());
        assertEquals(PurchaseReceiptStatus.APPROVED,
                service.approve(11L, new PurchaseReceiptRequests.Transition(1),
                        new StockPilotPrincipal(8L, "auditor")).status());
    }

    @Test
    void rejectsIllegalStateTransitionsAndCompletedEditing() {
        when(receipts.selectByIdForUpdate(11L))
                .thenReturn(receipt(11L, PurchaseReceiptStatus.SUBMITTED, 1))
                .thenReturn(receipt(11L, PurchaseReceiptStatus.COMPLETED, 3));

        BusinessException submitError = assertThrows(BusinessException.class,
                () -> service.submit(11L, new PurchaseReceiptRequests.Transition(1), operator));
        BusinessException editError = assertThrows(BusinessException.class,
                () -> service.update(11L, new PurchaseReceiptRequests.Update(
                        3, 1L, null, List.of(requestLine(BigDecimal.ONE))), operator));

        assertEquals("PURCHASE_RECEIPT_409_STATE", submitError.getErrorCode().code());
        assertEquals("PURCHASE_RECEIPT_409_STATE", editError.getErrorCode().code());
        verify(lines, never()).deleteByReceiptId(anyLong());
    }

    @Test
    void rejectsZeroNegativeAndOverPrecisionQuantities() {
        for (BigDecimal invalid : List.of(
                BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.00001"))) {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.create(create("PR-BAD", invalid), operator));
            assertEquals("PURCHASE_RECEIPT_400_LINE", exception.getErrorCode().code());
        }
        verify(receipts, never()).insert(any());
    }

    @Test
    void rejectsMissingOrDisabledMasterData() {
        when(warehouses.selectById(1L)).thenReturn(null);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.create(create("PR-MISSING", BigDecimal.ONE), operator));
        assertEquals("WAREHOUSE_404", missing.getErrorCode().code());

        when(warehouses.selectById(4L)).thenReturn(warehouse(4L, MasterDataStatus.ENABLED));
        when(locations.selectById(5L)).thenReturn(location(5L, 4L, MasterDataStatus.ENABLED));
        when(skus.selectById(6L)).thenReturn(sku(6L, MasterDataStatus.DISABLED));
        BusinessException disabled = assertThrows(BusinessException.class,
                () -> service.create(new PurchaseReceiptRequests.Create(
                        "PR-DISABLED", 4L, null,
                        List.of(new PurchaseReceiptRequests.Line(5L, 6L, BigDecimal.ONE))), operator));
        assertEquals("MASTER_DATA_404", disabled.getErrorCode().code());
    }

    private PurchaseReceiptRequests.Create create(String receiptNo, BigDecimal quantity) {
        return new PurchaseReceiptRequests.Create(receiptNo, 1L, null, List.of(requestLine(quantity)));
    }

    private PurchaseReceiptRequests.Line requestLine(BigDecimal quantity) {
        return new PurchaseReceiptRequests.Line(2L, 3L, quantity);
    }

    private PurchaseReceiptEntity receipt(long id, PurchaseReceiptStatus status, int version) {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(id);
        entity.setReceiptNo("PR-001");
        entity.setWarehouseId(1L);
        entity.setStatus(status);
        entity.setCreatedBy(7L);
        entity.setCreatedByName("operator");
        entity.setVersion(version);
        return entity;
    }

    private PurchaseReceiptLineEntity line(long receiptId, String quantity) {
        PurchaseReceiptLineEntity entity = new PurchaseReceiptLineEntity();
        entity.setId(21L);
        entity.setReceiptId(receiptId);
        entity.setWarehouseId(1L);
        entity.setLineNo(1);
        entity.setLocationId(2L);
        entity.setSkuId(3L);
        entity.setQuantity(new BigDecimal(quantity));
        return entity;
    }

    private WarehouseEntity warehouse(long id, MasterDataStatus status) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(id);
        entity.setStatus(status);
        return entity;
    }

    private WarehouseLocationEntity location(long id, long warehouseId, MasterDataStatus status) {
        WarehouseLocationEntity entity = new WarehouseLocationEntity();
        entity.setId(id);
        entity.setWarehouseId(warehouseId);
        entity.setStatus(status);
        return entity;
    }

    private SkuEntity sku(long id, MasterDataStatus status) {
        SkuEntity entity = new SkuEntity();
        entity.setId(id);
        entity.setStatus(status);
        return entity;
    }
}
