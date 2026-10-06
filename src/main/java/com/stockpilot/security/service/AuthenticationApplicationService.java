package com.stockpilot.security.service;

import com.stockpilot.security.api.SecurityErrorCode;
import com.stockpilot.security.audit.AuditService;
import com.stockpilot.security.auth.JwtService;
import com.stockpilot.security.config.SecurityProperties;
import com.stockpilot.security.domain.*;
import com.stockpilot.security.mapper.*;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.vo.SecurityVO;
import com.stockpilot.shared.api.AccessErrorCode;
import com.stockpilot.shared.auth.AuthenticatedActor;
import com.stockpilot.shared.exception.BusinessException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthenticationApplicationService implements ApplicationRunner {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final SecurityProperties securityProperties;
    private final String dummyPasswordHash;

    public AuthenticationApplicationService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService,
            SecurityProperties securityProperties) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
        dummyPasswordHash = passwordEncoder.encode("stockpilot-login-timing-placeholder");
    }

    // 用户登录：统一返回“凭据无效”以避免泄露账号是否存在，并对不存在用户执行一次伪密码校验以缩小计时差异。
    // 只有启用用户且 BCrypt 密码匹配时才签发 JWT；成功与失败结果都会写入安全审计。
    public SecurityVO.Token login(SecurityRequests.Login r) {
        String username = r.username().trim();
        UserEntity u = userMapper.findByUsername(username);
        if (u == null) {
            passwordEncoder.matches(r.password(), dummyPasswordHash);
            auditService.recordLogin(null, username, "FAILURE", "用户不存在");
            throw new BusinessException(SecurityErrorCode.INVALID_CREDENTIALS);
        }
        boolean passwordMatches = passwordEncoder.matches(r.password(), u.getPasswordHash());
        if (u.getStatus() != SecurityStatus.ENABLED) {
            auditService.recordLogin(u.getId(), u.getUsername(), "FAILURE", "用户已停用");
            throw new BusinessException(SecurityErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordMatches) {
            auditService.recordLogin(u.getId(), u.getUsername(), "FAILURE", "凭据错误");
            throw new BusinessException(SecurityErrorCode.INVALID_CREDENTIALS);
        }
        String token = jwtService.issue(u.getId(), u.getUsername());
        JwtService.Claims c = jwtService.verify(token);
        auditService.recordLogin(u.getId(), u.getUsername(), "SUCCESS", "登录成功");
        return new SecurityVO.Token(token, "Bearer", c.expiresAt());
    }

    // 查询当前用户：JWT 仅提供身份线索，每次请求重新从 MySQL 校验用户状态并加载实时角色和权限。
    // 因此停用用户或修改授权后无需等待令牌过期即可立即生效。
    public SecurityVO.CurrentUser current(AuthenticatedActor principal) {
        if (principal == null) throw new BusinessException(AccessErrorCode.UNAUTHENTICATED);
        UserEntity u = userMapper.selectById(principal.userId());
        if (u == null
                || u.getStatus() != SecurityStatus.ENABLED
                || !u.getUsername().equals(principal.username()))
            throw new BusinessException(AccessErrorCode.UNAUTHENTICATED);
        return new SecurityVO.CurrentUser(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                userMapper.findRoleCodes(u.getId()),
                userMapper.findPermissionCodes(u.getId()));
    }

    // 应用启动时可按环境变量创建首个管理员；凭据不写入配置文件，也不会覆盖已经存在的用户。
    // 创建后立即绑定预置 ADMIN 角色，缺少角色种子时直接阻止错误初始化。
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(securityProperties.bootstrapAdminUsername())
                && !StringUtils.hasText(securityProperties.bootstrapAdminPassword())) return;
        if (!StringUtils.hasText(securityProperties.bootstrapAdminUsername())
                || !StringUtils.hasText(securityProperties.bootstrapAdminPassword()))
            throw new IllegalStateException(
                    "Bootstrap admin username and password must both be set");
        String username = securityProperties.bootstrapAdminUsername().trim();
        // Serialize bootstrap instances on the seeded role; do not grant an existing user's roles.
        RoleEntity admin = roleMapper.findByCodeForUpdate("ADMIN");
        if (admin == null || admin.getStatus() != SecurityStatus.ENABLED)
            throw new IllegalStateException("Enabled ADMIN role missing");
        if (userMapper.findByUsername(username) != null) return;
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setDisplayName("系统管理员");
        u.setPasswordHash(passwordEncoder.encode(securityProperties.bootstrapAdminPassword()));
        u.setStatus(SecurityStatus.ENABLED);
        if (userMapper.insert(u) != 1
                || userMapper.insertRoles(u.getId(), java.util.List.of(admin.getId())) != 1)
            throw new IllegalStateException("Cannot create bootstrap administrator");
        auditService.recordLogin(u.getId(), u.getUsername(), "SUCCESS", "引导管理员已创建");
    }
}
