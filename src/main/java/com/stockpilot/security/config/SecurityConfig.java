package com.stockpilot.security.config;
import com.fasterxml.jackson.databind.ObjectMapper;import com.stockpilot.common.api.ApiResponse;import com.stockpilot.security.api.SecurityErrorCode;import com.stockpilot.security.auth.JwtAuthenticationFilter;import org.springframework.boot.context.properties.EnableConfigurationProperties;import org.springframework.context.annotation.*;import org.springframework.http.*;import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.http.SessionCreationPolicy;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.security.web.SecurityFilterChain;import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration @EnableMethodSecurity @EnableConfigurationProperties(SecurityProperties.class) public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean SecurityFilterChain chain(HttpSecurity http,JwtAuthenticationFilter jwt,ObjectMapper json)throws Exception{return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a
  .requestMatchers("/api/auth/login","/api/health","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
  .requestMatchers(HttpMethod.GET,"/api/auth/me").authenticated()
  .requestMatchers(HttpMethod.PUT,"/api/security/users/*/roles","/api/security/roles/*/permissions").hasAuthority("SECURITY_GRANT")
  .requestMatchers(HttpMethod.GET,"/api/security/users/**").hasAuthority("SECURITY_USER_READ").requestMatchers("/api/security/users/**").hasAuthority("SECURITY_USER_WRITE")
  .requestMatchers(HttpMethod.GET,"/api/security/roles/**").hasAuthority("SECURITY_ROLE_READ").requestMatchers("/api/security/roles/**").hasAuthority("SECURITY_ROLE_WRITE")
  .requestMatchers(HttpMethod.GET,"/api/security/permissions/**").hasAuthority("SECURITY_PERMISSION_READ").requestMatchers("/api/security/permissions/**").hasAuthority("SECURITY_PERMISSION_WRITE")
  .requestMatchers(HttpMethod.GET,"/api/security/audit-logs").hasAuthority("AUDIT_LOG_READ")
  .requestMatchers(HttpMethod.GET,"/api/master-data/**").hasAuthority("MASTER_DATA_READ").requestMatchers("/api/master-data/**").hasAuthority("MASTER_DATA_WRITE")
  .requestMatchers(HttpMethod.GET,"/api/inventory/**").hasAuthority("INVENTORY_READ")
  .requestMatchers(HttpMethod.GET,"/api/inbound/purchase-receipts/**").hasAuthority("PURCHASE_RECEIPT_READ")
  .requestMatchers(HttpMethod.POST,"/api/inbound/purchase-receipts/*/approve").hasAuthority("PURCHASE_RECEIPT_APPROVE")
  .requestMatchers(HttpMethod.POST,"/api/inbound/purchase-receipts/*/complete").hasAuthority("PURCHASE_RECEIPT_COMPLETE")
  .requestMatchers("/api/inbound/purchase-receipts/**").hasAuthority("PURCHASE_RECEIPT_WRITE")
  .requestMatchers(HttpMethod.GET,"/api/outbound/sales-orders/**").hasAuthority("SALES_OUTBOUND_READ")
  .requestMatchers(HttpMethod.POST,"/api/outbound/sales-orders/*/approve").hasAuthority("SALES_OUTBOUND_APPROVE")
  .requestMatchers(HttpMethod.POST,"/api/outbound/sales-orders/*/complete").hasAuthority("SALES_OUTBOUND_COMPLETE")
  .requestMatchers("/api/outbound/sales-orders/**").hasAuthority("SALES_OUTBOUND_WRITE")
  .requestMatchers(HttpMethod.GET,"/api/transfers/**").hasAuthority("TRANSFER_READ")
  .requestMatchers(HttpMethod.POST,"/api/transfers/*/approve").hasAuthority("TRANSFER_APPROVE")
  .requestMatchers(HttpMethod.POST,"/api/transfers/*/dispatch","/api/transfers/*/start-transit").hasAuthority("TRANSFER_OUTBOUND")
  .requestMatchers(HttpMethod.POST,"/api/transfers/*/receive").hasAuthority("TRANSFER_INBOUND")
  .requestMatchers("/api/transfers/**").hasAuthority("TRANSFER_WRITE")
  .requestMatchers(HttpMethod.GET,"/api/inventory-counts/**").hasAuthority("INVENTORY_COUNT_READ")
  .requestMatchers(HttpMethod.POST,"/api/inventory-counts/*/approve").hasAuthority("INVENTORY_COUNT_APPROVE")
  .requestMatchers(HttpMethod.POST,"/api/inventory-counts/*/adjust").hasAuthority("INVENTORY_COUNT_ADJUST")
  .requestMatchers("/api/inventory-counts/**").hasAuthority("INVENTORY_COUNT_WRITE")
  .anyRequest().denyAll()).exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->write(r,json,SecurityErrorCode.UNAUTHENTICATED)).accessDeniedHandler((q,r,x)->write(r,json,SecurityErrorCode.FORBIDDEN))).addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class).build();}
 private static void write(jakarta.servlet.http.HttpServletResponse r,ObjectMapper json,SecurityErrorCode e)throws java.io.IOException{r.setStatus(e.httpStatus().value());r.setContentType(MediaType.APPLICATION_JSON_VALUE);r.setCharacterEncoding("UTF-8");json.writeValue(r.getWriter(),ApiResponse.failure(e));}
}
