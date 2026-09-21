package com.stockpilot.security.service;

import com.stockpilot.security.api.SecurityErrorCode;
import com.stockpilot.security.audit.AuditService;
import com.stockpilot.security.auth.JwtService;
import com.stockpilot.security.auth.StockPilotPrincipal;
import com.stockpilot.security.config.SecurityProperties;
import com.stockpilot.security.domain.*;
import com.stockpilot.security.mapper.*;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.vo.SecurityVO;
import com.stockpilot.shared.exception.BusinessException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthenticationApplicationService implements ApplicationRunner {
    private final UserMapper users;
    private final RoleMapper roles;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final AuditService audit;
    private final SecurityProperties properties;
    private final String dummyPasswordHash;

    public AuthenticationApplicationService(
            UserMapper u,
            RoleMapper r,
            PasswordEncoder p,
            JwtService j,
            AuditService a,
            SecurityProperties sp) {
        users = u;
        roles = r;
        passwords = p;
        jwt = j;
        audit = a;
        properties = sp;
        dummyPasswordHash = p.encode("stockpilot-login-timing-placeholder");
    }

    // 用户登录：统一返回“凭据无效”以避免泄露账号是否存在，并对不存在用户执行一次伪密码校验以缩小计时差异。
    // 只有启用用户且 BCrypt 密码匹配时才签发 JWT；成功与失败结果都会写入安全审计。
    public SecurityVO.Token login(SecurityRequests.Login r) {
        String username = r.username().trim();
        UserEntity u = users.findByUsername(username);
        if (u == null) {
            passwords.matches(r.password(), dummyPasswordHash);
            audit.recordLogin(null, username, "FAILURE", "用户不存在");
            throw new BusinessException(SecurityErrorCode.INVALID_CREDENTIALS);
        }
        boolean passwordMatches = passwords.matches(r.password(), u.getPasswordHash());
        if (u.getStatus() != SecurityStatus.ENABLED) {
            audit.recordLogin(u.getId(), u.getUsername(), "FAILURE", "用户已停用");
            throw new BusinessException(SecurityErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordMatches) {
            audit.recordLogin(u.getId(), u.getUsername(), "FAILURE", "凭据错误");
            throw new BusinessException(SecurityErrorCode.INVALID_CREDENTIALS);
        }
        String token = jwt.issue(u.getId(), u.getUsername());
        JwtService.Claims c = jwt.verify(token);
        audit.recordLogin(u.getId(), u.getUsername(), "SUCCESS", "登录成功");
        return new SecurityVO.Token(token, "Bearer", c.expiresAt());
    }

    // 查询当前用户：JWT 仅提供身份线索，每次请求重新从 MySQL 校验用户状态并加载实时角色和权限。
    // 因此停用用户或修改授权后无需等待令牌过期即可立即生效。
    public SecurityVO.CurrentUser current(StockPilotPrincipal principal) {
        if (principal == null) throw new BusinessException(SecurityErrorCode.UNAUTHENTICATED);
        UserEntity u = users.selectById(principal.userId());
        if (u == null
                || u.getStatus() != SecurityStatus.ENABLED
                || !u.getUsername().equals(principal.username()))
            throw new BusinessException(SecurityErrorCode.UNAUTHENTICATED);
        return new SecurityVO.CurrentUser(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                users.findRoleCodes(u.getId()),
                users.findPermissionCodes(u.getId()));
    }

    // 应用启动时可按环境变量创建首个管理员；凭据不写入配置文件，也不会覆盖已经存在的用户。
    // 创建后立即绑定预置 ADMIN 角色，缺少角色种子时直接阻止错误初始化。
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.bootstrapAdminUsername())
                && !StringUtils.hasText(properties.bootstrapAdminPassword())) return;
        if (!StringUtils.hasText(properties.bootstrapAdminUsername())
                || !StringUtils.hasText(properties.bootstrapAdminPassword()))
            throw new IllegalStateException(
                    "Bootstrap admin username and password must both be set");
        if (users.findByUsername(properties.bootstrapAdminUsername()) != null) return;
        UserEntity u = new UserEntity();
        u.setUsername(properties.bootstrapAdminUsername().trim());
        u.setDisplayName("系统管理员");
        u.setPasswordHash(passwords.encode(properties.bootstrapAdminPassword()));
        u.setStatus(SecurityStatus.ENABLED);
        users.insert(u);
        RoleEntity admin =
                roles.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                                        RoleEntity>()
                                .eq(RoleEntity::getCode, "ADMIN"));
        if (admin == null) throw new IllegalStateException("ADMIN role missing");
        users.insertRoles(u.getId(), java.util.List.of(admin.getId()));
        audit.recordLogin(u.getId(), u.getUsername(), "SUCCESS", "引导管理员已创建");
    }
}
