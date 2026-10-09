package com.stockpilot.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockpilot.ai.vo.AiAgentVO.*;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

/** Only typed, evidence-backed clauses become business prose. No model free text is displayed. */
@Service
public class AiAgentAnswerService {
    public Answer render(
            List<Evidence> visible, String reason, JsonNode plan, List<Evidence> original) {
        if (plan == null) return render(visible, reason);
        Set<String> selectedIds = new LinkedHashSet<>();
        selected(plan, original).forEach(e -> selectedIds.add(e.evidenceId()));
        return render(
                visible.stream()
                        .filter(
                                e ->
                                        selectedIds.contains(e.evidenceId())
                                                || !Set.of("OK", "NO_DATA").contains(e.status()))
                        .toList(),
                reason);
    }

    public List<Evidence> selected(JsonNode plan, List<Evidence> evidence) {
        LinkedHashSet<Evidence> selected = new LinkedHashSet<>();
        for (JsonNode claim : plan.path("claims")) {
            selected.add(evidence.get(claim.path("evidence").asInt()));
            for (JsonNode index : claim.path("relatedEvidence"))
                selected.add(evidence.get(index.asInt()));
        }
        return List.copyOf(selected);
    }

    public Answer render(List<Evidence> evidence, String reason) {
        List<Conclusion> conclusions = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < evidence.size(); i++) {
            Evidence e = evidence.get(i);
            String id = evidenceId(e, i);
            JsonNode d = e.data();
            List<Fact> facts = new ArrayList<>();
            if (!Set.of("OK", "NO_DATA").contains(e.status())) {
                unknown.add(e.message());
                continue;
            }
            String text;
            if (e.status().equals("NO_DATA")) text = e.message();
            else
                switch (e.tool()) {
                    case "list_inventory" -> {
                        StringJoiner rows = new StringJoiner("；");
                        int row = 0;
                        for (JsonNode balance : d.path("inventory").path("records")) {
                            String path = "/inventory/records/" + row++;
                            rows.add(
                                    reference(d, "sku", balance.path("skuId").asText())
                                            + "（"
                                            + reference(
                                                    d,
                                                    "warehouse",
                                                    balance.path("warehouseId").asText())
                                            + " / "
                                            + reference(
                                                    d,
                                                    "location",
                                                    balance.path("locationId").asText())
                                            + "）：可用量"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/availableQuantity",
                                                    balance.path("availableQuantity")));
                        }
                        text = "库存列表：" + rows + "。" + e.message();
                        if (d.has("belowAvailable"))
                            text =
                                    "筛选可用量严格低于"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/belowAvailable",
                                                    d.path("belowAvailable"))
                                            + "的库存记录。"
                                            + text;
                    }
                    case "list_documents" -> {
                        StringJoiner rows = new StringJoiner("；");
                        int row = 0;
                        for (JsonNode document : d.path("documents").path("records")) {
                            String path = "/documents/records/" + row++;
                            rows.add(
                                    fact(
                                                    facts,
                                                    id,
                                                    path + "/businessNo",
                                                    document.path("businessNo"))
                                            + "（"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/status",
                                                    document.path("status"))
                                            + "）");
                        }
                        text =
                                period(d)
                                        + "匹配单据："
                                        + rows
                                        + "。"
                                        + (d.path("dateField").asText().equals("COMPLETED")
                                                ? "按完成/调整日期筛选。"
                                                : "按创建日期筛选。")
                                        + e.message();
                    }
                    case "query_balances" -> {
                        StringJoiner rows = new StringJoiner("；");
                        int row = 0;
                        for (JsonNode balance : d.path("warehouses").path("records")) {
                            String path = "/warehouses/records/" + row++;
                            String warehouse =
                                    reference(d, "warehouse", balance.path("warehouseId").asText());
                            rows.add(
                                    warehouse
                                            + "：实际量"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/actualQuantity",
                                                    balance.path("actualQuantity"))
                                            + "，可用量"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/availableQuantity",
                                                    balance.path("availableQuantity"))
                                            + "，冻结量"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/frozenQuantity",
                                                    balance.path("frozenQuantity")));
                        }
                        text = label(d) + rows + "。数量覆盖匹配库位；仓库和库位列表分页。";
                    }
                    case "summarize_movements" -> {
                        StringJoiner changes = new StringJoiner("；");
                        int row = 0;
                        for (JsonNode action : d.path("movements")) {
                            changes.add(
                                    action.path("meaning").asText()
                                            + "，实际量变化"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/movements/" + row++ + "/changeActualQuantity",
                                                    action.path("changeActualQuantity")));
                        }
                        String delta =
                                fact(
                                        facts,
                                        id,
                                        "/summary/changeActualQuantity",
                                        d.path("summary").path("changeActualQuantity"));
                        text =
                                label(d)
                                        + period(d)
                                        + "实际量净变化"
                                        + delta
                                        + "。"
                                        + changes
                                        + "。这是业务动作对变化的贡献；冻结和释放不改变实际量，不能据此认定销售消耗或更深层业务原因。";
                        if (d.path("summary").has("changeAvailableQuantity"))
                            text +=
                                    " 可用量净变化"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/summary/changeAvailableQuantity",
                                                    d.path("summary")
                                                            .path("changeAvailableQuantity"))
                                            + "，冻结量净变化"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/summary/changeFrozenQuantity",
                                                    d.path("summary").path("changeFrozenQuantity"))
                                            + "。";
                        if (d.path("summary").path("changeActualQuantity").isTextual()
                                && new BigDecimal(delta).signum() >= 0)
                            text += " 查询区间没有显示实际库存净下降；若关注可用量，应同时核对冻结占用。";
                    }
                    case "query_frozen_sources" -> {
                        JsonNode total = d.path("frozenTotals");
                        text =
                                label(d)
                                        + "当前有效销售占用"
                                        + fact(
                                                facts,
                                                id,
                                                "/frozenTotals/salesQuantity",
                                                total.path("salesQuantity"))
                                        + "，调拨占用"
                                        + fact(
                                                facts,
                                                id,
                                                "/frozenTotals/transferQuantity",
                                                total.path("transferQuantity"))
                                        + "。冻结仍在仓内，会减少可分配量，并不表示已经出库。";
                        if (total.has("frozenQuantity"))
                            text +=
                                    "余额冻结量"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/frozenTotals/frozenQuantity",
                                                    total.path("frozenQuantity"))
                                            + "，与有效单据总量差异"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/frozenTotals/differenceQuantity",
                                                    total.path("differenceQuantity"))
                                            + "。";
                        text += e.message();
                        if (total.has("unavailableQuantity"))
                            text =
                                    "实际量"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/frozenTotals/actualQuantity",
                                                    total.path("actualQuantity"))
                                            + "，可用量"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/frozenTotals/availableQuantity",
                                                    total.path("availableQuantity"))
                                            + "，两者差额"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/frozenTotals/unavailableQuantity",
                                                    total.path("unavailableQuantity"))
                                            + "由同一快照中的冻结量体现。"
                                            + text;
                    }
                    case "query_sales_orders" -> {
                        StringJoiner orders = new StringJoiner("；");
                        int row = 0;
                        for (JsonNode order : d.path("salesOrders").path("records")) {
                            String path = "/salesOrders/records/" + row++;
                            orders.add(
                                    fact(facts, id, path + "/businessNo", order.path("businessNo"))
                                            + "（"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/status",
                                                    order.path("status"))
                                            + "）");
                        }
                        text = label(d) + "匹配销售单：" + orders + "。" + e.message();
                    }
                    case "compare_inventory" -> {
                        StringJoiner warehouses = new StringJoiner("；");
                        int row = 0;
                        for (JsonNode balance : d.path("comparison")) {
                            String scopeKey = row == 0 ? "warehouse" : "otherWarehouse";
                            String path = "/comparison/" + row++;
                            String warehouse =
                                    fact(
                                            facts,
                                            id,
                                            "/" + scopeKey + "/name",
                                            d.path(scopeKey).path("name"));
                            if (!balance.path("balanceExists").asBoolean()) {
                                warehouses.add(warehouse + "没有余额记录，不能当作零库存");
                                continue;
                            }
                            warehouses.add(
                                    warehouse
                                            + "实际量"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/actualQuantity",
                                                    balance.path("actualQuantity"))
                                            + "，可用量"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/availableQuantity",
                                                    balance.path("availableQuantity"))
                                            + "，冻结量"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/frozenQuantity",
                                                    balance.path("frozenQuantity"))
                                            + "；有效销售占用"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/salesQuantity",
                                                    balance.path("salesQuantity"))
                                            + "，调拨占用"
                                            + fact(
                                                    facts,
                                                    id,
                                                    path + "/transferQuantity",
                                                    balance.path("transferQuantity")));
                        }
                        text = label(d) + warehouses + "。" + e.message();
                        if (d.has("difference")) {
                            text +=
                                    " 实际量差"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/difference/actualQuantity",
                                                    d.path("difference").path("actualQuantity"))
                                            + "，可用量差"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/difference/availableQuantity",
                                                    d.path("difference").path("availableQuantity"))
                                            + "，冻结量差"
                                            + fact(
                                                    facts,
                                                    id,
                                                    "/difference/frozenQuantity",
                                                    d.path("difference").path("frozenQuantity"))
                                            + "。";
                        } else unknown.add("至少一个仓库没有余额记录，无法计算仓间数量差。");
                    }
                    case "get_document", "trace_ledger" -> {
                        String field =
                                List.of("outboundNo", "receiptNo", "transferNo", "countNo").stream()
                                        .filter(d::has)
                                        .findFirst()
                                        .orElse("ledgerNo");
                        text = "单据/来源" + fact(facts, id, "/" + field, d.path(field));
                        if (d.has("status"))
                            text += "，当前状态" + fact(facts, id, "/status", d.path("status"));
                        text += "。" + d.path("meaning").asText("") + " " + e.message();
                    }
                    default -> text = label(d) + period(d) + e.message();
                }
            Map<String, String> scope = new LinkedHashMap<>();
            for (String key : List.of("sku", "warehouse", "location", "otherWarehouse"))
                if (d.has(key)) scope.put(key, d.path(key).path("code").asText());
            if (d.has("period"))
                for (String key : List.of("startDate", "endDate", "timezone"))
                    scope.put(key, d.path("period").path(key).asText());
            if (d.has("statusFilter")) scope.put("statusFilter", d.path("statusFilter").asText());
            conclusions.add(
                    new Conclusion(
                            e.tool(),
                            text,
                            List.copyOf(facts),
                            id,
                            Map.copyOf(scope),
                            e.queriedAt(),
                            e.message()));
        }
        if (reason != null && !Set.of("OK", "NO_DATA").contains(reason))
            unknown.add("本次查询未完整结束：" + reasonLabel(reason) + "；已取得证据可核对，缺失步骤的结论尚未确认。");
        explainDocuments(conclusions, evidence);
        return new Answer(
                List.copyOf(conclusions),
                unknown.stream().distinct().toList(),
                evidence.stream().anyMatch(e -> e.tool().equals("summarize_movements"))
                                && evidence.stream()
                                        .noneMatch(
                                                e ->
                                                        e.tool().equals("summarize_documents")
                                                                && e.status().equals("OK"))
                        ? List.of("可继续按业务单据核对区间变化及对应单据状态。")
                        : List.of());
    }

    /** Models may select supported conclusions and cite exact fields, never invent replacements. */
    public void validate(JsonNode finish, List<Evidence> evidence) {
        if (!finish.isObject()
                || !finish.path("claims").isArray()
                || finish.path("claims").isEmpty()
                || finish.path("claims").size() > 24)
            throw new IllegalArgumentException("Invalid answer contract");
        for (JsonNode claim : finish.path("claims")) {
            if (!claim.path("evidence").isIntegralNumber()
                    || !claim.path("evidence").canConvertToInt())
                throw new IllegalArgumentException();
            int index = claim.path("evidence").asInt(-1);
            if (index < 0 || index >= evidence.size()) throw new IllegalArgumentException();
            Evidence e = evidence.get(index);
            if (!Set.of("OK", "NO_DATA").contains(e.status())
                    || !e.tool().equals(claim.path("rule").asText()))
                throw new IllegalArgumentException();
            if (claim.has("field") || claim.has("value")) {
                String path = claim.path("field").asText();
                // Accept the observation envelope's explicit /data prefix, then validate the
                // identical whitelist field/value. This is not general path traversal.
                if (path.startsWith("/data/")) path = path.substring(5);
                if (!path.startsWith("/") || !claim.path("value").isTextual())
                    throw new IllegalArgumentException("Invalid fact reference");
                JsonNode actual = e.data().at(path);
                if (!actual.isValueNode() || actual.isNull() || actual.isMissingNode())
                    throw new IllegalArgumentException("Unknown fact reference");
                var fact = new Fact("E" + (index + 1), path, actual.asText());
                String value = claim.path("value").asText();
                if (!fact.value().equals(value)) {
                    if (!path.matches(".*/[A-Za-z]*Quantity"))
                        throw new IllegalArgumentException(
                                "Identifier or state must match exactly");
                    try {
                        if (new BigDecimal(fact.value()).compareTo(new BigDecimal(value)) != 0)
                            throw new IllegalArgumentException();
                    } catch (NumberFormatException failure) {
                        throw new IllegalArgumentException();
                    }
                }
            }
            if (claim.has("relatedEvidence")) {
                if (!claim.path("relatedEvidence").isArray()
                        || claim.path("relatedEvidence").size() > 12)
                    throw new IllegalArgumentException();
                for (JsonNode related : claim.path("relatedEvidence")) {
                    if (!related.isIntegralNumber()
                            || !related.canConvertToInt()
                            || related.asInt() < 0
                            || related.asInt() >= evidence.size())
                        throw new IllegalArgumentException();
                    Evidence other = evidence.get(related.asInt());
                    if (!Set.of("OK", "NO_DATA").contains(other.status())
                            || !related(e, other, evidence))
                        throw new IllegalArgumentException("Evidence scopes do not agree");
                }
            }
            Set<String> allowed = Set.of("rule", "evidence", "field", "value", "relatedEvidence");
            claim.fieldNames()
                    .forEachRemaining(
                            k -> {
                                if (!allowed.contains(k)) throw new IllegalArgumentException();
                            });
        }
        finish.fieldNames()
                .forEachRemaining(
                        k -> {
                            if (!k.equals("claims")) throw new IllegalArgumentException();
                        });
    }

    private static boolean related(Evidence left, Evidence right, List<Evidence> evidence) {
        if (sameScope(left, right)) return true;
        if (Set.of("get_document", "trace_ledger").contains(left.tool()))
            return linkedDocument(right, left, evidence);
        return linkedDocument(left, right, evidence);
    }

    /** Require queried movement, frozen or order records to prove a document's business link. */
    private static boolean linkedDocument(
            Evidence scope, Evidence document, List<Evidence> evidence) {
        if (!Set.of("get_document", "trace_ledger").contains(document.tool())) return false;
        JsonNode detail = document.data();
        String type = detail.path("documentType").asText();
        String number =
                detail.path(
                                switch (type) {
                                    case "SALES" -> "outboundNo";
                                    case "PURCHASE" -> "receiptNo";
                                    case "TRANSFER" -> "transferNo";
                                    case "COUNT" -> "countNo";
                                    default -> "";
                                })
                        .asText();
        if (number.isBlank()) return false;
        for (Evidence groups : evidence) {
            List<String> pages =
                    switch (groups.tool()) {
                        case "summarize_documents" -> List.of("documentMovements");
                        case "query_ledgers" -> List.of("ledgers");
                        case "query_frozen_sources" -> List.of("salesSources", "transferSources");
                        case "query_sales_orders" -> List.of("salesOrders");
                        default -> List.of();
                    };
            if (pages.isEmpty() || !groups.status().equals("OK") || !sameScope(scope, groups))
                continue;
            long sku = groups.data().path("sku").path("id").asLong(-1);
            long warehouse = groups.data().path("warehouse").path("id").asLong(-1);
            long location = groups.data().path("location").path("id").asLong(-1);
            if (sku <= 0 || warehouse <= 0) continue;
            List<JsonNode> records = new ArrayList<>();
            for (String page : pages)
                groups.data().path(page).path("records").forEach(records::add);
            for (JsonNode row : records) {
                if (!number.equals(row.path("businessNo").asText())) continue;
                String action = row.path("businessType").asText();
                if (groups.tool().equals("query_sales_orders")) {
                    if (!type.equals("SALES")) continue;
                } else if (groups.tool().equals("query_frozen_sources")) {
                    if (!type.equals(row.path("documentType").asText())) continue;
                } else
                    try {
                        if (!type.equals(
                                AiToolService.documentType(
                                        com.stockpilot.inventory.domain.InventoryBusinessType
                                                .valueOf(action)))) continue;
                    } catch (IllegalArgumentException failure) {
                        continue;
                    }
                if (row.has("warehouseId") && row.path("warehouseId").asLong(-1) != warehouse)
                    continue;
                if (row.has("skuId") && row.path("skuId").asLong(-1) != sku) continue;
                long matchedLocation =
                        row.has("locationId") ? row.path("locationId").asLong(-1) : location;
                boolean inboundTransfer = action.equals("TRANSFER_IN");
                String warehouseField =
                        type.equals("TRANSFER")
                                ? inboundTransfer ? "targetWarehouseId" : "sourceWarehouseId"
                                : "warehouseId";
                if (detail.path(warehouseField).asLong(-1) != warehouse) continue;
                String locationField =
                        type.equals("TRANSFER")
                                ? inboundTransfer ? "targetLocationId" : "sourceLocationId"
                                : "locationId";
                for (JsonNode line : detail.path("lines"))
                    if (line.path("skuId").asLong(-1) == sku
                            && (matchedLocation < 0
                                    || line.path(locationField).asLong(-1) == matchedLocation))
                        return true;
            }
        }
        return false;
    }

    private static boolean sameScope(Evidence left, Evidence right) {
        boolean hasScope = false;
        for (String key : List.of("sku", "warehouse", "location", "period")) {
            JsonNode a = left.data().path(key), b = right.data().path(key);
            if (a.isMissingNode() && b.isMissingNode()) continue;
            if (a.isMissingNode() || b.isMissingNode()) return false;
            if (key.equals("period")) {
                for (String field : List.of("startDate", "endDate", "timezone"))
                    if (!a.path(field).equals(b.path(field))) return false;
                if (a.has("endExclusive")
                        && b.has("endExclusive")
                        && !a.path("endExclusive").equals(b.path("endExclusive"))) return false;
            } else if (!a.path("code").asText().equals(b.path("code").asText())) return false;
            hasScope = true;
        }
        return hasScope;
    }

    private static String evidenceId(Evidence e, int index) {
        return e.evidenceId() == null ? "E" + (index + 1) : e.evidenceId();
    }

    private static void explainDocuments(List<Conclusion> conclusions, List<Evidence> evidence) {
        for (int i = 0; i < evidence.size(); i++) {
            Evidence movements = evidence.get(i);
            if (!movements.tool().equals("summarize_movements") || !movements.status().equals("OK"))
                continue;
            String movementId = evidenceId(movements, i);
            for (int j = 0; j < evidence.size(); j++) {
                Evidence documents = evidence.get(j);
                if (!documents.tool().equals("summarize_documents")
                        || !documents.status().equals("OK")
                        || !sameScope(movements, documents)) continue;
                String documentId = evidenceId(documents, j);
                int conclusionIndex = -1;
                for (int k = 0; k < conclusions.size(); k++)
                    if (conclusions.get(k).evidenceId().equals(movementId)) conclusionIndex = k;
                if (conclusionIndex < 0) continue;
                Conclusion original = conclusions.get(conclusionIndex);
                List<Fact> facts = new ArrayList<>(original.facts());
                StringJoiner contributions = new StringJoiner("；");
                int row = 0;
                for (JsonNode record : documents.data().path("documentMovements").path("records")) {
                    String path = "/documentMovements/records/" + row++;
                    if (new BigDecimal(record.path("changeActualQuantity").asText("0")).signum()
                            == 0) continue;
                    contributions.add(
                            "单据"
                                    + fact(
                                            facts,
                                            documentId,
                                            path + "/businessNo",
                                            record.path("businessNo"))
                                    + "（"
                                    + fact(
                                            facts,
                                            documentId,
                                            path + "/businessType",
                                            record.path("businessType"))
                                    + "）实际量变化"
                                    + fact(
                                            facts,
                                            documentId,
                                            path + "/changeActualQuantity",
                                            record.path("changeActualQuantity")));
                }
                if (contributions.length() > 0)
                    conclusions.set(
                            conclusionIndex,
                            new Conclusion(
                                    "EXPLAIN_MOVEMENT_CHANGE",
                                    original.text()
                                            + " 对应当前页的业务单据："
                                            + contributions
                                            + "。单据分组为分页结果，不能据此认定已逐单核对全部变化或确认更深层原因。",
                                    List.copyOf(facts),
                                    movementId,
                                    original.scope(),
                                    original.queriedAt(),
                                    original.coverage(),
                                    List.of(movementId, documentId)));
            }
        }
    }

    private static String fact(List<Fact> facts, String id, String path, JsonNode value) {
        if (!value.isValueNode() || value.isNull() || value.isMissingNode()) return "未知";
        String text = value.asText("未知");
        facts.add(new Fact(id, path, text));
        return text;
    }

    public static String reasonLabel(String reason) {
        return switch (reason) {
            case "OK" -> "查询完成";
            case "NO_DATA" -> "没有匹配记录";
            case "FORBIDDEN" -> "当前权限不足";
            case "OUT_OF_SCOPE" -> "该请求超出只读查询与分析范围";
            case "QUERY_FAILED" -> "业务查询失败";
            case "MODEL_ERROR" -> "模型请求失败";
            case "MODEL_TIMEOUT" -> "模型请求超时";
            case "QUESTION_TIMEOUT" -> "已达到总查询时限";
            case "ROUND_LIMIT_EXCEEDED" -> "已达到规划轮数上限";
            case "TOOL_LIMIT_EXCEEDED" -> "已达到工具调用上限";
            case "CONTEXT_LIMIT_EXCEEDED", "EVIDENCE_LIMIT_EXCEEDED" -> "查询范围超过容量限制";
            case "REQUEST_CANCELLED" -> "查询已取消";
            case "REPEATED_TOOL_CALL" -> "检测到没有新证据的重复查询";
            case "INVALID_MODEL_RESPONSE" -> "模型返回的计划或事实引用无效";
            case "INSUFFICIENT_EVIDENCE" -> "已有证据不足以回答原问题";
            case "NEEDS_SELECTION" -> "等待选择候选";
            case "NEEDS_CLARIFICATION" -> "等待补充条件";
            case "INPUT_EXPIRED" -> "补充条件已过期";
            case "SENSITIVE_DATA_REDACTED" -> "部分字段包含敏感内容，已停止向模型传递";
            case "DISABLED" -> "AI尚未启用";
            case "CONFIGURATION_ERROR" -> "模型配置无效";
            case "CAPACITY_EXCEEDED" -> "查询任务容量已满";
            default -> "查询未能完整执行";
        };
    }

    private static String reference(JsonNode data, String kind, String id) {
        return data.path("references").path(kind + ":" + id).path("name").asText(kind + "#" + id);
    }

    private static String label(JsonNode d) {
        return d.has("sku") ? "商品" + d.path("sku").path("name").asText() + "，" : "";
    }

    private static String period(JsonNode d) {
        return d.has("period")
                ? d.path("period").path("startDate").asText()
                        + "至"
                        + d.path("period").path("endDate").asText()
                        + "（Asia/Shanghai，包含起止日）："
                : "";
    }
}
