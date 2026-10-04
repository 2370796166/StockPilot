package com.stockpilot.security.auth;

import com.stockpilot.security.domain.*;
import com.stockpilot.security.mapper.UserMapper;
import com.stockpilot.shared.auth.AuthenticatedActor;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService {
    private final UserMapper userMapper;

    public DatabaseUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Authentication load(long id, String username) {
        UserEntity u = userMapper.selectById(id);
        if (u == null
                || u.getStatus() != SecurityStatus.ENABLED
                || !u.getUsername().equals(username)) return null;
        List<SimpleGrantedAuthority> authorities =
                userMapper.findPermissionCodes(id).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedActor(id, username), null, authorities);
    }
}
