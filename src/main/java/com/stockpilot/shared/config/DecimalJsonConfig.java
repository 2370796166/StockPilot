package com.stockpilot.shared.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.oas.models.media.StringSchema;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keep decimal quantities exact across JSON and JavaScript; IDs and versions remain numbers. */
@Configuration
public class DecimalJsonConfig {
    @Bean
    ModelConverter decimalQuantitySchema(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return (type, context, chain) -> {
            if (type.getType() != null
                    && objectMapper.constructType(type.getType()).hasRawClass(BigDecimal.class))
                return new StringSchema().example("10.0000").pattern("^-?\\d+(?:\\.\\d+)?$");
            return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        };
    }

    @Bean
    Module decimalQuantityModule() {
        SimpleModule module = new SimpleModule("decimal-quantities");
        module.addSerializer(
                BigDecimal.class,
                new JsonSerializer<BigDecimal>() {
                    @Override
                    public void serialize(
                            BigDecimal value, JsonGenerator generator, SerializerProvider provider)
                            throws IOException {
                        generator.writeString(value.toPlainString());
                    }
                });
        return module;
    }
}
