package com.stockpilot.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.security.api.SecurityErrorCode;
import com.stockpilot.security.audit.AuditService;
import com.stockpilot.security.domain.*;
import com.stockpilot.security.mapper.*;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.vo.SecurityVO;
import com.stockpilot.shared.exception.BusinessException;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SecurityManagementApplicationService {
    private final UserMapper users;
    private final RoleMapper roles;
    private final PermissionMapper permissions;
    private final AuditLogMapper logs;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    public SecurityManagementApplicationService(
            UserMapper u,
            RoleMapper r,
            PermissionMapper p,
            AuditLogMapper l,
            PasswordEncoder pe,
            AuditService a) {
        users = u;
        roles = r;
        permissions = p;
        logs = l;
        passwords = pe;
        audit = a;
    }

    // 创建用户：密码只保存 BCrypt 哈希，用户名唯一性由数据库约束兜底，并记录不含密码的审计摘要。
    public SecurityVO.User createUser(SecurityRequests.CreateUser r) {
        UserEntity e = new UserEntity();
        e.setUsername(r.username().trim());
        e.setPasswordHash(passwords.encode(r.password()));
        e.setDisplayName(r.displayName().trim());
        e.setStatus(SecurityStatus.ENABLED);
        try {
            users.insert(e);
        } catch (DuplicateKeyException x) {
            throw new BusinessException(SecurityErrorCode.DUPLICATE, "用户名已存在");
        }
        audit.record("CREATE_USER", "USER", e.getId(), "SUCCESS", "username=" + e.getUsername());
        return user(e);
    }

    // 更新用户资料：使用乐观锁阻止旧版本覆盖新数据；仅在明确提交新密码时重新生成哈希。
    public SecurityVO.User updateUser(long id, SecurityRequests.UpdateUser r) {
        UserEntity e = requireUser(id);
        e.setDisplayName(r.displayName().trim());
        if (r.newPassword() != null && !r.newPassword().isBlank())
            e.setPasswordHash(passwords.encode(r.newPassword()));
        e.setVersion(r.version());
        if (users.updateById(e) != 1) throw new BusinessException(SecurityErrorCode.CONCURRENT);
        audit.record("UPDATE_USER", "USER", id, "SUCCESS", "用户资料已更新");
        return user(requireUser(id));
    }

    // 启用或停用用户：状态变更实时影响后续请求鉴权，不删除历史用户及其审计关系。
    public SecurityVO.User userStatus(long id, SecurityRequests.Status r) {
        UserEntity e = requireUser(id);
        e.setStatus(r.status());
        e.setVersion(r.version());
        if (users.updateById(e) != 1) throw new BusinessException(SecurityErrorCode.CONCURRENT);
        audit.record("CHANGE_USER_STATUS", "USER", id, "SUCCESS", "status=" + r.status());
        return user(requireUser(id));
    }

    // 整体替换用户角色：先验证所有目标角色存在且启用，再在同一事务删除旧关系并写入去重后的新关系。
    @Transactional
    public SecurityVO.User setUserRoles(long id, SecurityRequests.Ids r) {
        requireUser(id);
        validateRoles(r.ids());
        users.deleteRoles(id);
        if (!r.ids().isEmpty()) users.insertRoles(id, r.ids().stream().distinct().toList());
        audit.record(
                "ASSIGN_USER_ROLES",
                "USER",
                id,
                "SUCCESS",
                "roleCount=" + r.ids().stream().distinct().count());
        return user(requireUser(id));
    }

    public SecurityVO.User getUser(long id) {
        return user(requireUser(id));
    }

    public PageResult<SecurityVO.User> users(long page, long size) {
        Page<UserEntity> p =
                users.selectPage(
                        Page.of(page, size),
                        new LambdaQueryWrapper<UserEntity>().orderByDesc(UserEntity::getId));
        return new PageResult<>(
                p.getRecords().stream().map(this::user).toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize());
    }

    // 创建角色：角色编码作为稳定业务标识不可重复，后续通过权限集合定义其授权能力。
    public SecurityVO.Role createRole(SecurityRequests.CreateRole r) {
        RoleEntity e = new RoleEntity();
        e.setCode(r.code());
        e.setName(r.name().trim());
        e.setStatus(SecurityStatus.ENABLED);
        try {
            roles.insert(e);
        } catch (DuplicateKeyException x) {
            throw new BusinessException(SecurityErrorCode.DUPLICATE, "角色编码已存在");
        }
        audit.record("CREATE_ROLE", "ROLE", e.getId(), "SUCCESS", "code=" + e.getCode());
        return role(e);
    }

    public SecurityVO.Role updateRole(long id, SecurityRequests.UpdateRole r) {
        RoleEntity e = requireRole(id);
        e.setName(r.name().trim());
        e.setVersion(r.version());
        if (roles.updateById(e) != 1) throw new BusinessException(SecurityErrorCode.CONCURRENT);
        audit.record("UPDATE_ROLE", "ROLE", id, "SUCCESS", "角色资料已更新");
        return role(requireRole(id));
    }

    public SecurityVO.Role roleStatus(long id, SecurityRequests.Status r) {
        RoleEntity e = requireRole(id);
        e.setStatus(r.status());
        e.setVersion(r.version());
        if (roles.updateById(e) != 1) throw new BusinessException(SecurityErrorCode.CONCURRENT);
        audit.record("CHANGE_ROLE_STATUS", "ROLE", id, "SUCCESS", "status=" + r.status());
        return role(requireRole(id));
    }

    // 整体替换角色权限：所有权限必须存在且启用，关系替换与安全审计在同一事务内完成。
    @Transactional
    public SecurityVO.Role setRolePermissions(long id, SecurityRequests.Ids r) {
        requireRole(id);
        validatePermissions(r.ids());
        roles.deletePermissions(id);
        if (!r.ids().isEmpty()) roles.insertPermissions(id, r.ids().stream().distinct().toList());
        audit.record(
                "ASSIGN_ROLE_PERMISSIONS",
                "ROLE",
                id,
                "SUCCESS",
                "permissionCount=" + r.ids().stream().distinct().count());
        return role(requireRole(id));
    }

    public SecurityVO.Role getRole(long id) {
        return role(requireRole(id));
    }

    public List<SecurityVO.Role> roles() {
        return roles
                .selectList(new LambdaQueryWrapper<RoleEntity>().orderByAsc(RoleEntity::getId))
                .stream()
                .map(this::role)
                .toList();
    }

    // 创建权限：权限编码直接参与方法和 URL 授权判断，因此必须保持唯一且稳定。
    public SecurityVO.Permission createPermission(SecurityRequests.CreatePermission r) {
        PermissionEntity e = new PermissionEntity();
        e.setCode(r.code());
        e.setName(r.name().trim());
        e.setDescription(trim(r.description()));
        e.setStatus(SecurityStatus.ENABLED);
        try {
            permissions.insert(e);
        } catch (DuplicateKeyException x) {
            throw new BusinessException(SecurityErrorCode.DUPLICATE, "权限编码已存在");
        }
        audit.record(
                "CREATE_PERMISSION", "PERMISSION", e.getId(), "SUCCESS", "code=" + e.getCode());
        return permission(e);
    }

    public SecurityVO.Permission updatePermission(long id, SecurityRequests.UpdatePermission r) {
        PermissionEntity e = requirePermission(id);
        e.setName(r.name().trim());
        e.setDescription(trim(r.description()));
        e.setVersion(r.version());
        if (permissions.updateById(e) != 1)
            throw new BusinessException(SecurityErrorCode.CONCURRENT);
        audit.record("UPDATE_PERMISSION", "PERMISSION", id, "SUCCESS", "权限资料已更新");
        return permission(requirePermission(id));
    }

    public SecurityVO.Permission permissionStatus(long id, SecurityRequests.Status r) {
        PermissionEntity e = requirePermission(id);
        e.setStatus(r.status());
        e.setVersion(r.version());
        if (permissions.updateById(e) != 1)
            throw new BusinessException(SecurityErrorCode.CONCURRENT);
        audit.record(
                "CHANGE_PERMISSION_STATUS", "PERMISSION", id, "SUCCESS", "status=" + r.status());
        return permission(requirePermission(id));
    }

    public List<SecurityVO.Permission> permissions() {
        return permissions
                .selectList(
                        new LambdaQueryWrapper<PermissionEntity>()
                                .orderByAsc(PermissionEntity::getId))
                .stream()
                .map(SecurityManagementApplicationService::permission)
                .toList();
    }

    // 分页查询安全审计记录；响应仅包含白名单摘要，不暴露密码哈希或完整 Token。
    public PageResult<SecurityVO.Audit> audits(long page, long size) {
        Page<AuditLogEntity> p =
                logs.selectPage(
                        Page.of(page, size),
                        new LambdaQueryWrapper<AuditLogEntity>()
                                .orderByDesc(AuditLogEntity::getId));
        return new PageResult<>(
                p.getRecords().stream()
                        .map(
                                e ->
                                        new SecurityVO.Audit(
                                                e.getId(),
                                                e.getOperatorId(),
                                                e.getOperatorName(),
                                                e.getActionType(),
                                                e.getObjectType(),
                                                e.getObjectId(),
                                                e.getResult(),
                                                e.getSummary(),
                                                e.getOccurredAt()))
                        .toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize());
    }

    private UserEntity requireUser(long id) {
        UserEntity e = users.selectById(id);
        if (e == null) throw new BusinessException(SecurityErrorCode.NOT_FOUND);
        return e;
    }

    private RoleEntity requireRole(long id) {
        RoleEntity e = roles.selectById(id);
        if (e == null) throw new BusinessException(SecurityErrorCode.NOT_FOUND);
        return e;
    }

    private PermissionEntity requirePermission(long id) {
        PermissionEntity e = permissions.selectById(id);
        if (e == null) throw new BusinessException(SecurityErrorCode.NOT_FOUND);
        return e;
    }

    private void validateRoles(List<Long> ids) {
        for (Long id : new HashSet<>(ids)) {
            RoleEntity e = roles.selectById(id);
            if (e == null || e.getStatus() != SecurityStatus.ENABLED)
                throw new BusinessException(SecurityErrorCode.INVALID_RELATION);
        }
    }

    private void validatePermissions(List<Long> ids) {
        for (Long id : new HashSet<>(ids)) {
            PermissionEntity e = permissions.selectById(id);
            if (e == null || e.getStatus() != SecurityStatus.ENABLED)
                throw new BusinessException(SecurityErrorCode.INVALID_RELATION);
        }
    }

    private SecurityVO.User user(UserEntity e) {
        return new SecurityVO.User(
                e.getId(),
                e.getUsername(),
                e.getDisplayName(),
                e.getStatus(),
                users.findRoleIds(e.getId()),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion());
    }

    private SecurityVO.Role role(RoleEntity e) {
        return new SecurityVO.Role(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getStatus(),
                roles.findPermissionIds(e.getId()),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion());
    }

    private static SecurityVO.Permission permission(PermissionEntity e) {
        return new SecurityVO.Permission(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getDescription(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion());
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
