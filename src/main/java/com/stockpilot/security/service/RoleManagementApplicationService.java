package com.stockpilot.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockpilot.security.api.SecurityErrorCode;
import com.stockpilot.security.audit.AuditService;
import com.stockpilot.security.domain.PermissionEntity;
import com.stockpilot.security.domain.RoleEntity;
import com.stockpilot.security.domain.SecurityStatus;
import com.stockpilot.security.mapper.PermissionMapper;
import com.stockpilot.security.mapper.RoleMapper;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.vo.SecurityVO;
import com.stockpilot.shared.exception.BusinessException;
import java.util.HashSet;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementApplicationService {
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final AuditService auditService;

    public RoleManagementApplicationService(
            RoleMapper roleMapper, PermissionMapper permissionMapper, AuditService auditService) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.auditService = auditService;
    }

    @Transactional
    public SecurityVO.Role createRole(SecurityRequests.CreateRole request) {
        RoleEntity role = new RoleEntity();
        role.setCode(request.code());
        role.setName(request.name().trim());
        role.setStatus(SecurityStatus.ENABLED);
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(SecurityErrorCode.DUPLICATE, "角色编码已存在");
        }
        auditService.record(
                "CREATE_ROLE", "ROLE", role.getId(), "SUCCESS", "code=" + role.getCode());
        return toView(role);
    }

    @Transactional
    public SecurityVO.Role updateRole(long id, SecurityRequests.UpdateRole request) {
        RoleEntity role = requireRole(id);
        role.setName(request.name().trim());
        role.setVersion(request.version());
        if (roleMapper.updateById(role) != 1) {
            throw new BusinessException(SecurityErrorCode.CONCURRENT);
        }
        auditService.record("UPDATE_ROLE", "ROLE", id, "SUCCESS", "角色资料已更新");
        return toView(requireRole(id));
    }

    @Transactional
    public SecurityVO.Role changeStatus(long id, SecurityRequests.Status request) {
        RoleEntity role = requireRole(id);
        role.setStatus(request.status());
        role.setVersion(request.version());
        if (roleMapper.updateById(role) != 1) {
            throw new BusinessException(SecurityErrorCode.CONCURRENT);
        }
        auditService.record(
                "CHANGE_ROLE_STATUS", "ROLE", id, "SUCCESS", "status=" + request.status());
        return toView(requireRole(id));
    }

    @Transactional
    public SecurityVO.Role replacePermissions(long id, SecurityRequests.Ids request) {
        requireRole(id);
        validatePermissions(request.ids());
        roleMapper.deletePermissions(id);
        List<Long> permissionIds = request.ids().stream().distinct().toList();
        if (!permissionIds.isEmpty()) {
            roleMapper.insertPermissions(id, permissionIds);
        }
        auditService.record(
                "ASSIGN_ROLE_PERMISSIONS",
                "ROLE",
                id,
                "SUCCESS",
                "permissionCount=" + permissionIds.size());
        return toView(requireRole(id));
    }

    @Transactional(readOnly = true)
    public SecurityVO.Role getRole(long id) {
        return toView(requireRole(id));
    }

    @Transactional(readOnly = true)
    public List<SecurityVO.Role> listRoles() {
        return roleMapper
                .selectList(new LambdaQueryWrapper<RoleEntity>().orderByAsc(RoleEntity::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    private RoleEntity requireRole(long id) {
        RoleEntity role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(SecurityErrorCode.NOT_FOUND);
        }
        return role;
    }

    private void validatePermissions(List<Long> permissionIds) {
        for (Long permissionId : new HashSet<>(permissionIds)) {
            PermissionEntity permission = permissionMapper.selectById(permissionId);
            if (permission == null || permission.getStatus() != SecurityStatus.ENABLED) {
                throw new BusinessException(SecurityErrorCode.INVALID_RELATION);
            }
        }
    }

    private SecurityVO.Role toView(RoleEntity role) {
        return new SecurityVO.Role(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getStatus(),
                roleMapper.findPermissionIds(role.getId()),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                role.getVersion());
    }
}
