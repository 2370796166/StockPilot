package com.stockpilot.ai.service;

import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import java.util.*;

/** Minimum business evidence requirements, independent of the model's decision to finish. */
final class AiTaskEvidencePolicy {
    private AiTaskEvidencePolicy() {}

    static boolean direct(String question, String tool) {
        if (question.matches("(?s).*(以及|并且|再查|追溯单据).*")) return false;
        if (tool.equals("compare_inventory") || tool.equals("trace_ledger")) return true;
        return tool.equals("query_frozen_sources") && question.matches("(?s).*为什么.*可用.*实际.*");
    }

    static boolean sufficient(String question, List<Evidence> results) {
        question = question.replaceAll("必要时[^。？！]*", "");
        Set<String> tools = new HashSet<>();
        results.stream()
                .filter(e -> Set.of("OK", "NO_DATA").contains(e.status()))
                .filter(e -> !e.tool().startsWith("find_") && !e.tool().equals("clarify"))
                .forEach(e -> tools.add(e.tool()));
        if (tools.isEmpty()) return false;
        if (question.matches("(?s).*(比较|对比).*") && !tools.contains("compare_inventory"))
            return false;
        if (question.matches("(?s).*(下降|减少|变化).*")) {
            if (question.matches("(?s).*(每单|逐单|单据).*变化.*") && !question.matches("(?s).*(下降|减少).*"))
                return tools.contains("summarize_documents");
            if (!tools.contains("summarize_movements")) return false;
            if (question.matches("(?s).*(单据|哪.*单|追溯).*"))
                if (!tools.contains("summarize_documents") && !tools.contains("trace_ledger"))
                    return false;
        }
        if (question.matches("(?s).*(销售单|销售订单).*") && !question.matches("(?s).*(下降|减少|变化).*"))
            if (!tools.contains("query_sales_orders")
                    && !tools.contains("list_documents")
                    && !tools.contains("get_document")
                    && !tools.contains("trace_ledger")) return false;
        if (question.matches("(?s).*(冻结来源|冻结.*原因|为什么.*可用|可用.*为什么).*"))
            if (!tools.contains("query_frozen_sources") && !tools.contains("compare_inventory"))
                return false;
        if (question.matches("(?s).*(追溯流水|流水.*对应|流水.*来源).*"))
            if (!tools.contains("trace_ledger")) return false;
        if (question.matches("(?s).*(流水).*"))
            if (!tools.contains("query_ledgers")
                    && !tools.contains("trace_ledger")
                    && !tools.contains("summarize_movements")
                    && !tools.contains("summarize_documents")) return false;
        if (question.matches("(?s).*(单据).*"))
            if (!tools.contains("get_document")
                    && !tools.contains("list_documents")
                    && !tools.contains("trace_ledger")
                    && !tools.contains("summarize_documents")) return false;
        return true;
    }
}
