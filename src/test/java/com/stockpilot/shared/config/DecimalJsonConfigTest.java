package com.stockpilot.shared.config;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverters;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DecimalJsonConfigTest {
    @Test
    void exactDecimalStringsAndNumericIdsUseTheRealBootMapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(DecimalJsonConfig.class)
                .run(
                        context -> {
                            ObjectMapper mapper = context.getBean(ObjectMapper.class);
                            ModelConverters converters = new ModelConverters();
                            converters.addConverter(context.getBean(ModelConverter.class));
                            io.swagger.v3.oas.models.media.Schema<?> schema =
                                    converters.readAll(Quantity.class).get("Quantity");
                            assertEquals(
                                    "string", schema.getProperties().get("quantity").getType());
                            assertEquals("integer", schema.getProperties().get("id").getType());
                            var input = new Quantity(7L, new BigDecimal("999999999999999.1234"));
                            var tree = mapper.readTree(mapper.writeValueAsString(input));
                            assertTrue(tree.path("quantity").isTextual());
                            assertEquals("999999999999999.1234", tree.path("quantity").asText());
                            assertTrue(tree.path("id").isIntegralNumber());
                            assertEquals(input, mapper.treeToValue(tree, Quantity.class));
                            for (String decimal :
                                    new String[] {
                                        "0.0000", "0.0001", "-99999999999999.9999", "1E+3"
                                    }) {
                                BigDecimal value = new BigDecimal(decimal);
                                assertEquals(
                                        value.toPlainString(), mapper.valueToTree(value).asText());
                            }
                            assertNull(
                                    mapper.readValue("{\"id\":7,\"quantity\":null}", Quantity.class)
                                            .quantity());
                            assertThrows(
                                    Exception.class,
                                    () ->
                                            mapper.readValue(
                                                    "{\"id\":7,\"quantity\":\"NaN\"}",
                                                    Quantity.class));
                        });
    }

    record Quantity(Long id, BigDecimal quantity) {}
}
