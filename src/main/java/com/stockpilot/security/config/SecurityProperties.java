package com.stockpilot.security.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="stockpilot.security")
public record SecurityProperties(String jwtSecret,long accessTokenMinutes,String bootstrapAdminUsername,String bootstrapAdminPassword) {}
