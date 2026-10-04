package com.stockpilot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpilot.security.audit.AuditService;
import com.stockpilot.security.domain.PermissionEntity;
import com.stockpilot.security.domain.RoleEntity;
import com.stockpilot.security.domain.SecurityStatus;
import com.stockpilot.security.domain.UserEntity;
import com.stockpilot.security.mapper.PermissionMapper;
import com.stockpilot.security.mapper.RoleMapper;
import com.stockpilot.security.mapper.UserMapper;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.service.PermissionManagementApplicationService;
import com.stockpilot.security.service.RoleManagementApplicationService;
import com.stockpilot.security.service.UserManagementApplicationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityManagementServicesTest {
    @Test
    void userServiceReplacesValidatedRoles() {
        UserMapper userMapper = mock(UserMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        AuditService auditService = mock(AuditService.class);
        UserEntity user = user(7L);
        RoleEntity role = new RoleEntity();
        role.setId(3L);
        role.setStatus(SecurityStatus.ENABLED);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(roleMapper.selectById(3L)).thenReturn(role);
        when(userMapper.findRoleIds(7L)).thenReturn(List.of(3L));
        UserManagementApplicationService service =
                new UserManagementApplicationService(
                        userMapper, roleMapper, mock(PasswordEncoder.class), auditService);

        var result = service.replaceRoles(7L, new SecurityRequests.Ids(List.of(3L, 3L)));

        assertEquals(List.of(3L), result.roleIds());
        verify(userMapper).deleteRoles(7L);
        verify(userMapper).insertRoles(7L, List.of(3L));
        verify(auditService).record("ASSIGN_USER_ROLES", "USER", 7L, "SUCCESS", "roleCount=1");
    }

    @Test
    void roleServiceReplacesValidatedPermissions() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        PermissionMapper permissionMapper = mock(PermissionMapper.class);
        AuditService auditService = mock(AuditService.class);
        RoleEntity role = new RoleEntity();
        role.setId(4L);
        role.setCode("AUDITOR");
        role.setName("审核员");
        role.setStatus(SecurityStatus.ENABLED);
        role.setVersion(0);
        PermissionEntity permission = permission(9L);
        when(roleMapper.selectById(4L)).thenReturn(role);
        when(permissionMapper.selectById(9L)).thenReturn(permission);
        when(roleMapper.findPermissionIds(4L)).thenReturn(List.of(9L));
        RoleManagementApplicationService service =
                new RoleManagementApplicationService(roleMapper, permissionMapper, auditService);

        var result = service.replacePermissions(4L, new SecurityRequests.Ids(List.of(9L, 9L)));

        assertEquals(List.of(9L), result.permissionIds());
        verify(roleMapper).deletePermissions(4L);
        verify(roleMapper).insertPermissions(4L, List.of(9L));
        verify(auditService)
                .record("ASSIGN_ROLE_PERMISSIONS", "ROLE", 4L, "SUCCESS", "permissionCount=1");
    }

    @Test
    void permissionServiceOwnsPermissionCreation() {
        PermissionMapper permissionMapper = mock(PermissionMapper.class);
        AuditService auditService = mock(AuditService.class);
        when(permissionMapper.insert(any()))
                .thenAnswer(
                        invocation -> {
                            PermissionEntity permission = invocation.getArgument(0);
                            permission.setId(11L);
                            permission.setVersion(0);
                            return 1;
                        });
        PermissionManagementApplicationService service =
                new PermissionManagementApplicationService(permissionMapper, auditService);

        var result =
                service.createPermission(
                        new SecurityRequests.CreatePermission(
                                "INVENTORY_READ", "库存读取", "  查询库存  "));

        assertEquals("查询库存", result.description());
        verify(auditService)
                .record("CREATE_PERMISSION", "PERMISSION", 11L, "SUCCESS", "code=INVENTORY_READ");
    }

    private static UserEntity user(long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("operator");
        user.setDisplayName("业务员");
        user.setStatus(SecurityStatus.ENABLED);
        user.setVersion(0);
        return user;
    }

    private static PermissionEntity permission(long id) {
        PermissionEntity permission = new PermissionEntity();
        permission.setId(id);
        permission.setCode("INVENTORY_READ");
        permission.setName("库存读取");
        permission.setStatus(SecurityStatus.ENABLED);
        permission.setVersion(0);
        return permission;
    }
}
