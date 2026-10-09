package com.stockpilot.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class AiTaskContextTest {
    @Test
    void switchingWarehouseInvalidatesLocationAndDocumentReferences() {
        var context =
                new HashMap<>(
                        Map.of(
                                "sku",
                                "BOLT",
                                "warehouse",
                                "WH1",
                                "location",
                                "L1",
                                "_locationName",
                                "第一库位",
                                "_documents",
                                "old",
                                "_ledgerNumbers",
                                "old",
                                "ledgerNo",
                                "old",
                                "otherWarehouse",
                                "WH2"));
        AiTaskContext.confirm(context, "warehouse", "WH3", "新仓库");
        assertEquals("BOLT", context.get("sku"));
        assertEquals("WH3", context.get("warehouse"));
        for (String field :
                List.of(
                        "location",
                        "_locationName",
                        "_documents",
                        "_ledgerNumbers",
                        "ledgerNo",
                        "otherWarehouse")) assertFalse(context.containsKey(field), field);
    }

    @Test
    void switchingProductOrExplicitScopeInvalidatesOldDocumentPointers() {
        var context = new HashMap<>(Map.of("sku", "BOLT", "warehouse", "WH1", "_documents", "old"));
        AiTaskContext.confirm(context, "sku", "NUT", "螺母");
        assertFalse(context.containsKey("_documents"));
        context.put("_documents", "new");
        AiTaskContext.prepare(context, "改查二号仓");
        assertFalse(context.containsKey("_documents"));
        assertEquals("NUT", context.get("sku"));
    }
}
