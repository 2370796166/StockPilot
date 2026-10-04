package com.stockpilot.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.security.api.SecurityErrorCode;
import com.stockpilot.security.audit.AuditService;
import com.stockpilot.security.domain.RoleEntity;
import com.stockpilot.security.domain.SecurityStatus;
import com.stockpilot.security.domain.UserEntity;
import com.stockpilot.security.mapper.RoleMapper;
import com.stockpilot.security.mapper.UserMapper;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.vo.SecurityVO;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.exception.BusinessException;
import java.util.HashSet;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementApplicationService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserManagementApplicationService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public SecurityVO.User createUser(SecurityRequests.CreateUser request) {
        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setStatus(SecurityStatus.ENABLED);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(SecurityErrorCode.DUPLICATE, "用户名已存在");
        }
        auditService.record(
                "CREATE_USER", "USER", user.getId(), "SUCCESS", "username=" + user.getUsername());
        return toView(user);
    }

    @Transactional
    public SecurityVO.User updateUser(long id, SecurityRequests.UpdateUser request) {
        UserEntity user = requireUser(id);
        user.setDisplayName(request.displayName().trim());
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }
        user.setVersion(request.version());
        if (userMapper.updateById(user) != 1) {
            throw new BusinessException(SecurityErrorCode.CONCURRENT);
        }
        auditService.record("UPDATE_USER", "USER", id, "SUCCESS", "用户资料已更新");
        return toView(requireUser(id));
    }

    @Transactional
    public SecurityVO.User changeStatus(long id, SecurityRequests.Status request) {
        UserEntity user = requireUser(id);
        user.setStatus(request.status());
        user.setVersion(request.version());
        if (userMapper.updateById(user) != 1) {
            throw new BusinessException(SecurityErrorCode.CONCURRENT);
        }
        auditService.record(
                "CHANGE_USER_STATUS", "USER", id, "SUCCESS", "status=" + request.status());
        return toView(requireUser(id));
    }

    @Transactional
    public SecurityVO.User replaceRoles(long id, SecurityRequests.Ids request) {
        requireUser(id);
        validateRoles(request.ids());
        userMapper.deleteRoles(id);
        List<Long> roleIds = request.ids().stream().distinct().toList();
        if (!roleIds.isEmpty()) {
            userMapper.insertRoles(id, roleIds);
        }
        auditService.record(
                "ASSIGN_USER_ROLES", "USER", id, "SUCCESS", "roleCount=" + roleIds.size());
        return toView(requireUser(id));
    }

    @Transactional(readOnly = true)
    public SecurityVO.User getUser(long id) {
        return toView(requireUser(id));
    }

    @Transactional(readOnly = true)
    public PageResult<SecurityVO.User> pageUsers(long page, long size) {
        Page<UserEntity> result =
                userMapper.selectPage(
                        Page.of(page, size),
                        new LambdaQueryWrapper<UserEntity>().orderByDesc(UserEntity::getId));
        return new PageResult<>(
                result.getRecords().stream().map(this::toView).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    private UserEntity requireUser(long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(SecurityErrorCode.NOT_FOUND);
        }
        return user;
    }

    private void validateRoles(List<Long> roleIds) {
        for (Long roleId : new HashSet<>(roleIds)) {
            RoleEntity role = roleMapper.selectById(roleId);
            if (role == null || role.getStatus() != SecurityStatus.ENABLED) {
                throw new BusinessException(SecurityErrorCode.INVALID_RELATION);
            }
        }
    }

    private SecurityVO.User toView(UserEntity user) {
        return new SecurityVO.User(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getStatus(),
                userMapper.findRoleIds(user.getId()),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion());
    }
}
