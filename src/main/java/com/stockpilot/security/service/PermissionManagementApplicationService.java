package com.stockpilot.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockpilot.security.api.SecurityErrorCode;
import com.stockpilot.security.audit.AuditService;
import com.stockpilot.security.domain.PermissionEntity;
import com.stockpilot.security.domain.SecurityStatus;
import com.stockpilot.security.mapper.PermissionMapper;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.vo.SecurityVO;
import com.stockpilot.shared.exception.BusinessException;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionManagementApplicationService {
    private final PermissionMapper permissionMapper;
    private final AuditService auditService;

    public PermissionManagementApplicationService(
            PermissionMapper permissionMapper, AuditService auditService) {
        this.permissionMapper = permissionMapper;
        this.auditService = auditService;
    }

    @Transactional
    public SecurityVO.Permission createPermission(SecurityRequests.CreatePermission request) {
        PermissionEntity permission = new PermissionEntity();
        permission.setCode(request.code());
        permission.setName(request.name().trim());
        permission.setDescription(normalizeDescription(request.description()));
        permission.setStatus(SecurityStatus.ENABLED);
        try {
            permissionMapper.insert(permission);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(SecurityErrorCode.DUPLICATE, "权限编码已存在");
        }
        auditService.record(
                "CREATE_PERMISSION",
                "PERMISSION",
                permission.getId(),
                "SUCCESS",
                "code=" + permission.getCode());
        return toView(permission);
    }

    @Transactional
    public SecurityVO.Permission updatePermission(
            long id, SecurityRequests.UpdatePermission request) {
        PermissionEntity permission = requirePermission(id);
        permission.setName(request.name().trim());
        permission.setDescription(normalizeDescription(request.description()));
        permission.setVersion(request.version());
        if (permissionMapper.updateById(permission) != 1) {
            throw new BusinessException(SecurityErrorCode.CONCURRENT);
        }
        auditService.record("UPDATE_PERMISSION", "PERMISSION", id, "SUCCESS", "权限资料已更新");
        return toView(requirePermission(id));
    }

    @Transactional
    public SecurityVO.Permission changeStatus(long id, SecurityRequests.Status request) {
        PermissionEntity permission = requirePermission(id);
        permission.setStatus(request.status());
        permission.setVersion(request.version());
        if (permissionMapper.updateById(permission) != 1) {
            throw new BusinessException(SecurityErrorCode.CONCURRENT);
        }
        auditService.record(
                "CHANGE_PERMISSION_STATUS",
                "PERMISSION",
                id,
                "SUCCESS",
                "status=" + request.status());
        return toView(requirePermission(id));
    }

    @Transactional(readOnly = true)
    public List<SecurityVO.Permission> listPermissions() {
        return permissionMapper
                .selectList(
                        new LambdaQueryWrapper<PermissionEntity>()
                                .orderByAsc(PermissionEntity::getId))
                .stream()
                .map(PermissionManagementApplicationService::toView)
                .toList();
    }

    private PermissionEntity requirePermission(long id) {
        PermissionEntity permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(SecurityErrorCode.NOT_FOUND);
        }
        return permission;
    }

    private static SecurityVO.Permission toView(PermissionEntity permission) {
        return new SecurityVO.Permission(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getStatus(),
                permission.getCreatedAt(),
                permission.getUpdatedAt(),
                permission.getVersion());
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }
}
