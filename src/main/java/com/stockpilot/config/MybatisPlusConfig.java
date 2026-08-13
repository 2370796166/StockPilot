package com.stockpilot.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.stockpilot.**.infrastructure.mapper")
public class MybatisPlusConfig {
}
