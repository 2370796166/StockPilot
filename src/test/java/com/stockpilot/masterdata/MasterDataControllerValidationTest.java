package com.stockpilot.masterdata;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.stockpilot.masterdata.infrastructure.cache.NoOpReferenceDataCache;
import com.stockpilot.masterdata.warehouse.controller.WarehouseController;
import com.stockpilot.masterdata.warehouse.mapper.WarehouseMapper;
import com.stockpilot.masterdata.warehouse.service.WarehouseApplicationService;
import com.stockpilot.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class MasterDataControllerValidationTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc =
                standaloneSetup(
                                new WarehouseController(
                                        new WarehouseApplicationService(
                                                mock(WarehouseMapper.class),
                                                NoOpReferenceDataCache.INSTANCE)))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void shouldRejectBlankRequiredFields() throws Exception {
        mvc.perform(
                        post("/api/master-data/warehouses")
                                .contentType("application/json")
                                .content("{\"code\":\"\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void shouldRejectInvalidCode() throws Exception {
        mvc.perform(
                        post("/api/master-data/warehouses")
                                .contentType("application/json")
                                .content("{\"code\":\"1 bad\",\"name\":\"仓库\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("编码须以字母开头")));
    }

    @Test
    void shouldRejectOversizedPage() throws Exception {
        mvc.perform(get("/api/master-data/warehouses?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void shouldRejectInvalidStatusValue() throws Exception {
        mvc.perform(get("/api/master-data/warehouses?status=UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        mvc.perform(
                        patch("/api/master-data/warehouses/1/status")
                                .contentType("application/json")
                                .content("{\"status\":\"UNKNOWN\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }
}
