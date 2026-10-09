package com.stockpilot.ai.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Invalidates references whose business scope no longer applies; never stores quantities. */
final class AiTaskContext {
    private AiTaskContext() {}

    static void confirm(Map<String, String> context, String field, String code, String name) {
        if (!Objects.equals(code, context.get(field))) {
            if (field.equals("warehouse")) {
                context.remove("location");
                context.remove("_locationName");
                context.remove("otherWarehouse");
                context.remove("_scopeWarehouses");
            }
            if (List.of("sku", "warehouse", "location").contains(field)) clearDocuments(context);
        }
        context.put(field, code);
        context.put("_" + field + "Name", name);
    }

    static void clearDocuments(Map<String, String> context) {
        List.of("number", "documentType", "ledgerNo", "_documents", "_ledgerNumbers")
                .forEach(context::remove);
    }

    static void prepare(Map<String, String> context, String question) {
        if (question.matches("(?s).*(所有商品|全部商品|所有SKU|全部SKU).*")) {
            context.remove("sku");
            context.remove("_skuName");
            clearDocuments(context);
        }
        if (question.matches("(?s).*(全公司|所有单据|全部单据|所有销售单|全部销售单).*")) {
            List.of("warehouse", "_warehouseName", "location", "_locationName", "otherWarehouse")
                    .forEach(context::remove);
            clearDocuments(context);
        }
        if (question.matches("(?s).*(改查|切换|换成|换到|改为).*")) clearDocuments(context);
        if (question.matches("(?s).*(所有仓库|全部仓库|所有库位|全部库位|全仓|不限库位).*")) {
            context.remove("location");
            context.remove("_locationName");
            clearDocuments(context);
        }
        if (question.matches("(?s).*(所有仓库|全部仓库).*")) {
            context.remove("warehouse");
            context.remove("_warehouseName");
            context.remove("otherWarehouse");
        }
        if (question.matches("(?s).*(全部历史|不限日期|所有流水).*")) {
            context.remove("startDate");
            context.remove("endDate");
            clearDocuments(context);
        }
    }
}
