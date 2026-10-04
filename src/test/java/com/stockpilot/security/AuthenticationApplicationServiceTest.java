package com.stockpilot.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.stockpilot.security.audit.AuditService;
import com.stockpilot.security.auth.JwtService;
import com.stockpilot.security.config.SecurityProperties;
import com.stockpilot.security.domain.*;
import com.stockpilot.security.mapper.*;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.service.*;
import com.stockpilot.shared.auth.AuthenticatedActor;
import com.stockpilot.shared.exception.BusinessException;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthenticationApplicationServiceTest {
    private UserMapper users;
    private RoleMapper roles;
    private AuditLogMapper logs;
    private BCryptPasswordEncoder encoder;
    private AuthenticationApplicationService service;

    @BeforeEach
    void setUp() {
        users = mock(UserMapper.class);
        roles = mock(RoleMapper.class);
        logs = mock(AuditLogMapper.class);
        encoder = new BCryptPasswordEncoder(4);
        SecurityProperties p =
                new SecurityProperties("01234567890123456789012345678901", 60, "", "");
        service =
                new AuthenticationApplicationService(
                        users, roles, encoder, new JwtService(p), new AuditService(logs), p);
    }

    @Test
    void correctLoginReturnsTokenAndAudit() {
        when(users.findByUsername("alice"))
                .thenReturn(user(SecurityStatus.ENABLED, "correct-password"));
        var token = service.login(new SecurityRequests.Login("alice", "correct-password"));
        assertEquals("Bearer", token.tokenType());
        assertNotNull(token.accessToken());
        verify(logs)
                .insert(
                        argThat(
                                e ->
                                        "LOGIN".equals(e.getActionType())
                                                && "SUCCESS".equals(e.getResult())
                                                && !String.valueOf(e.getSummary())
                                                        .contains("correct-password")));
    }

    @Test
    void wrongPasswordAndUnknownUserUseSafeFailure() {
        when(users.findByUsername("alice"))
                .thenReturn(user(SecurityStatus.ENABLED, "correct-password"));
        BusinessException wrong =
                assertThrows(
                        BusinessException.class,
                        () -> service.login(new SecurityRequests.Login("alice", "wrong-password")));
        assertEquals("AUTH_401", wrong.getErrorCode().code());
        BusinessException missing =
                assertThrows(
                        BusinessException.class,
                        () -> service.login(new SecurityRequests.Login("missing", "whateverxx")));
        assertEquals("AUTH_401", missing.getErrorCode().code());
        verify(logs, times(2))
                .insert(
                        argThat(
                                e ->
                                        "FAILURE".equals(e.getResult())
                                                && !String.valueOf(e.getSummary())
                                                        .contains("wrong-password")
                                                && !String.valueOf(e.getSummary())
                                                        .contains("whateverxx")));
    }

    @Test
    void disabledUserUsesSamePublicErrorAsOtherCredentialFailures() {
        when(users.findByUsername("alice"))
                .thenReturn(user(SecurityStatus.DISABLED, "correct-password"));
        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.login(
                                        new SecurityRequests.Login("alice", "correct-password")));
        assertEquals("AUTH_401", ex.getErrorCode().code());
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(logs).insert(argThat(e -> "用户已停用".equals(e.getSummary())));
    }

    @Test
    void createdUserStoresBcryptNotPlainPasswordAndCreatesSafeAudit() {
        when(users.insert(any()))
                .thenAnswer(
                        i -> {
                            UserEntity e = i.getArgument(0);
                            e.setId(9L);
                            e.setVersion(0);
                            return 1;
                        });
        when(users.findRoleIds(9L)).thenReturn(List.of());
        UserManagementApplicationService management =
                new UserManagementApplicationService(
                        users, mock(RoleMapper.class), encoder, new AuditService(logs));
        management.createUser(new SecurityRequests.CreateUser("alice", "1234", "Alice"));
        verify(users)
                .insert(
                        argThat(
                                e ->
                                        !"1234".equals(e.getPasswordHash())
                                                && encoder.matches("1234", e.getPasswordHash())));
        verify(logs).insert(argThat(e -> !String.valueOf(e.getSummary()).contains("1234")));
    }

    @Test
    void fourCharacterPasswordsPassCreateAndUpdateValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(
                    validator
                            .validate(new SecurityRequests.CreateUser("alice", "1234", "Alice"))
                            .isEmpty());
            assertTrue(
                    validator
                            .validate(new SecurityRequests.UpdateUser("Alice", "1234", 0))
                            .isEmpty());
        }
    }

    @Test
    void bootstrapAdministratorUsesAdminRole() {
        SecurityProperties p =
                new SecurityProperties(
                        "01234567890123456789012345678901", 60, "bootstrap_admin", "1234");
        AuthenticationApplicationService bootstrap =
                new AuthenticationApplicationService(
                        users, roles, encoder, new JwtService(p), new AuditService(logs), p);
        RoleEntity admin = new RoleEntity();
        admin.setId(3L);
        admin.setCode("ADMIN");
        admin.setStatus(SecurityStatus.ENABLED);
        when(roles.selectOne(any())).thenReturn(admin);
        when(users.insert(any()))
                .thenAnswer(
                        i -> {
                            UserEntity e = i.getArgument(0);
                            e.setId(8L);
                            return 1;
                        });
        bootstrap.run(mock(org.springframework.boot.ApplicationArguments.class));
        verify(users).insertRoles(8L, List.of(3L));
    }

    @Test
    void currentUserReturnsAuthoritativeRolesAndPermissions() {
        UserEntity alice = user(SecurityStatus.ENABLED, "correct-password");
        when(users.selectById(1L)).thenReturn(alice);
        when(users.findRoleCodes(1L)).thenReturn(List.of("ADMIN"));
        when(users.findPermissionCodes(1L))
                .thenReturn(List.of("MASTER_DATA_READ", "MASTER_DATA_WRITE"));
        var current = service.current(new AuthenticatedActor(1L, "alice"));
        assertEquals(1L, current.userId());
        assertEquals("alice", current.username());
        assertEquals("Alice", current.displayName());
        assertEquals(List.of("ADMIN"), current.roles());
        assertEquals(List.of("MASTER_DATA_READ", "MASTER_DATA_WRITE"), current.authorities());
    }

    private UserEntity user(SecurityStatus status, String raw) {
        UserEntity u = new UserEntity();
        u.setId(1L);
        u.setUsername("alice");
        u.setDisplayName("Alice");
        u.setStatus(status);
        u.setPasswordHash(encoder.encode(raw));
        u.setVersion(0);
        return u;
    }
}
