package com.stockpilot.security.auth;
import com.stockpilot.security.domain.*; import com.stockpilot.security.infrastructure.mapper.UserMapper; import org.springframework.security.core.*; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.stereotype.Service; import java.util.List;
@Service public class DatabaseUserDetailsService {
 private final UserMapper mapper; public DatabaseUserDetailsService(UserMapper mapper){this.mapper=mapper;}
 public Authentication load(long id,String username){UserEntity u=mapper.selectById(id);if(u==null||u.getStatus()!=SecurityStatus.ENABLED||!u.getUsername().equals(username))return null;List<SimpleGrantedAuthority> authorities=mapper.findPermissionCodes(id).stream().map(SimpleGrantedAuthority::new).toList();return new UsernamePasswordAuthenticationToken(new StockPilotPrincipal(id,username),null,authorities);}
}
