package com.stockpilot.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.security.domain.AuditLogEntity;
import com.stockpilot.security.mapper.AuditLogMapper;
import com.stockpilot.security.vo.SecurityVO;
import com.stockpilot.shared.api.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditQueryService {
    private final AuditLogMapper auditLogMapper;

    public SecurityAuditQueryService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<SecurityVO.Audit> pageAudits(long page, long size) {
        Page<AuditLogEntity> result =
                auditLogMapper.selectPage(
                        Page.of(page, size),
                        new LambdaQueryWrapper<AuditLogEntity>()
                                .orderByDesc(AuditLogEntity::getId));
        return new PageResult<>(
                result.getRecords().stream().map(SecurityAuditQueryService::toView).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    private static SecurityVO.Audit toView(AuditLogEntity audit) {
        return new SecurityVO.Audit(
                audit.getId(),
                audit.getOperatorId(),
                audit.getOperatorName(),
                audit.getActionType(),
                audit.getObjectType(),
                audit.getObjectId(),
                audit.getResult(),
                audit.getSummary(),
                audit.getOccurredAt());
    }
}
