package com.stockpilot.alert.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.alert.domain.SafetyStockRuleEntity;
import com.stockpilot.alert.mapper.LowStockAlertMapper;
import com.stockpilot.alert.mapper.SafetyStockRuleMapper;
import com.stockpilot.alert.request.SafetyStockRequests;
import com.stockpilot.alert.vo.SafetyStockVO;
import com.stockpilot.inventory.service.InventoryQueryApplicationService;
import com.stockpilot.masterdata.service.MasterDataReferenceApplicationService;
import com.stockpilot.security.audit.AuditService;
import com.stockpilot.shared.api.CommonErrorCode;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SafetyStockManagementService {
    private final SafetyStockRuleMapper safetyStockRuleMapper;
    private final LowStockAlertMapper lowStockAlertMapper;
    private final MasterDataReferenceApplicationService masterDataReferenceService;
    private final InventoryQueryApplicationService inventoryQueryService;
    private final AuditService auditService;

    public SafetyStockManagementService(
            SafetyStockRuleMapper safetyStockRuleMapper,
            LowStockAlertMapper lowStockAlertMapper,
            MasterDataReferenceApplicationService masterDataReferenceService,
            InventoryQueryApplicationService inventoryQueryService,
            AuditService auditService) {
        this.safetyStockRuleMapper = safetyStockRuleMapper;
        this.lowStockAlertMapper = lowStockAlertMapper;
        this.masterDataReferenceService = masterDataReferenceService;
        this.inventoryQueryService = inventoryQueryService;
        this.auditService = auditService;
    }

    @Transactional
    public SafetyStockVO.Rule create(SafetyStockRequests.Create request) {
        masterDataReferenceService.requireEnabledInventoryDimension(
                request.warehouseId(), request.locationId(), request.skuId());
        SafetyStockRuleEntity rule = new SafetyStockRuleEntity();
        rule.setWarehouseId(request.warehouseId());
        rule.setLocationId(request.locationId());
        rule.setSkuId(request.skuId());
        rule.setThresholdQuantity(request.thresholdQuantity());
        rule.setStatus("ENABLED");
        rule.setVersion(0);
        try {
            if (safetyStockRuleMapper.insert(rule) != 1)
                throw new IllegalStateException("Cannot create safety stock rule");
        } catch (DuplicateKeyException e) {
            throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT, "该库存维度已有规则，请修改现有规则");
        }
        evaluate(rule);
        auditService.record(
                "SAFETY_STOCK_CREATE", "SAFETY_STOCK_RULE", rule.getId(), "SUCCESS", "创建并评估安全库存规则");
        return view(rule);
    }

    @Transactional
    public SafetyStockVO.Rule update(long id, SafetyStockRequests.Update request) {
        SafetyStockRuleEntity rule = requireLocked(id);
        if (!rule.getVersion().equals(request.version()))
            throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT, "规则已变化，请刷新后重试");
        if ("ENABLED".equals(request.status()))
            masterDataReferenceService.requireEnabledInventoryDimension(
                    rule.getWarehouseId(), rule.getLocationId(), rule.getSkuId());
        if (safetyStockRuleMapper.update(
                        id, request.version(), request.thresholdQuantity(), request.status())
                != 1) throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT);
        rule.setThresholdQuantity(request.thresholdQuantity());
        rule.setStatus(request.status());
        rule.setVersion(request.version() + 1);
        evaluate(rule);
        auditService.record(
                "SAFETY_STOCK_UPDATE",
                "SAFETY_STOCK_RULE",
                id,
                "SUCCESS",
                "修改并评估安全库存规则，状态=" + request.status());
        return view(rule);
    }

    @Transactional
    public void reevaluate(long id) {
        evaluate(requireLocked(id));
        auditService.record(
                "SAFETY_STOCK_EVALUATE", "SAFETY_STOCK_RULE", id, "SUCCESS", "人工重新评估安全库存");
    }

    @Transactional(readOnly = true)
    public PageResult<SafetyStockVO.Rule> rules(
            long page, long size, Long warehouseId, String status) {
        validate(page, size, status, Set.of("ENABLED", "DISABLED"));
        var result = safetyStockRuleMapper.page(Page.of(page, size), warehouseId, status);
        return new PageResult<>(
                result.getRecords().stream().map(SafetyStockManagementService::view).toList(),
                result.getTotal(),
                page,
                size);
    }

    @Transactional(readOnly = true)
    public PageResult<SafetyStockVO.Alert> alerts(
            long page, long size, Long warehouseId, String status) {
        validate(page, size, status, Set.of("OPEN", "RESOLVED"));
        var result = lowStockAlertMapper.page(Page.of(page, size), warehouseId, status);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    private SafetyStockRuleEntity requireLocked(long id) {
        var rule = safetyStockRuleMapper.lock(id);
        if (rule == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        return rule;
    }

    private void evaluate(SafetyStockRuleEntity rule) {
        BigDecimal available =
                inventoryQueryService
                        .findBalanceForEvaluation(
                                rule.getWarehouseId(), rule.getLocationId(), rule.getSkuId())
                        .map(v -> v.availableQuantity())
                        .orElse(BigDecimal.ZERO);
        String source = UUID.randomUUID().toString();
        if ("ENABLED".equals(rule.getStatus())
                && available.compareTo(rule.getThresholdQuantity()) < 0) {
            lowStockAlertMapper.open(
                    rule.getId(),
                    rule.getWarehouseId(),
                    rule.getLocationId(),
                    rule.getSkuId(),
                    rule.getThresholdQuantity(),
                    available,
                    source,
                    "stockpilot.safety-stock.rule.changed",
                    "RULE-" + rule.getId());
        } else {
            lowStockAlertMapper.resolve(
                    rule.getId(),
                    rule.getThresholdQuantity(),
                    available,
                    source,
                    "stockpilot.safety-stock.rule.changed",
                    "RULE-" + rule.getId());
        }
    }

    private static void validate(long page, long size, String status, Set<String> allowed) {
        if (page < 1
                || page > 10000
                || size < 1
                || size > 100
                || (status != null && !allowed.contains(status)))
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
    }

    private static SafetyStockVO.Rule view(SafetyStockRuleEntity r) {
        return new SafetyStockVO.Rule(
                r.getId(),
                r.getWarehouseId(),
                r.getLocationId(),
                r.getSkuId(),
                r.getThresholdQuantity(),
                r.getStatus(),
                r.getVersion());
    }
}
