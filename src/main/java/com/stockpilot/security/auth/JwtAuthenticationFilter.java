package com.stockpilot.security.auth;
import com.stockpilot.common.exception.BusinessException; import jakarta.servlet.*; import jakarta.servlet.http.*; import org.springframework.security.core.Authentication; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter; import java.io.IOException;
@Component public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtService jwt; private final DatabaseUserDetailsService users;
 public JwtAuthenticationFilter(JwtService jwt,DatabaseUserDetailsService users){this.jwt=jwt;this.users=users;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{String h=req.getHeader("Authorization");if(h!=null&&h.startsWith("Bearer "))try{JwtService.Claims c=jwt.verify(h.substring(7));Authentication a=users.load(c.userId(),c.username());if(a!=null)SecurityContextHolder.getContext().setAuthentication(a);}catch(BusinessException ignored){}chain.doFilter(req,res);}
}
