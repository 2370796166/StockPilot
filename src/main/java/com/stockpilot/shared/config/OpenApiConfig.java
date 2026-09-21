package com.stockpilot.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockPilotOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("StockPilot API")
                                .description("智能仓储与库存管理平台后端接口")
                                .version("v1"));
    }
}
