package com.stockpilot.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Tool metadata and permission-dependent schema generation; contains no business queries. */
final class AiToolDefinitions {
    private AiToolDefinitions() {}

    private static final Map<String, List<String>> FIELDS =
            new HashMap<>(
                    Map.of(
                            "clarify",
                            List.of("reason", "tool"),
                            "find_sku",
                            List.of("keyword"),
                            "find_warehouse",
                            List.of("keyword"),
                            "find_location",
                            List.of("keyword", "warehouse"),
                            "query_balances",
                            List.of("sku", "warehouse", "location", "page", "size"),
                            "query_ledgers",
                            List.of(
                                    "sku",
                                    "warehouse",
                                    "location",
                                    "businessNo",
                                    "businessType",
                                    "startDate",
                                    "endDate",
                                    "page",
                                    "size"),
                            "get_document",
                            List.of("documentType", "number"),
                            "trace_ledger",
                            List.of("ledgerNo"),
                            "summarize_movements",
                            List.of("sku", "warehouse", "location", "startDate", "endDate"),
                            "query_frozen_sources",
                            List.of("sku", "warehouse", "location", "page", "size")));

    static {
        FIELDS.put(
                "query_sales_orders",
                List.of("sku", "warehouse", "location", "status", "page", "size"));
        FIELDS.put(
                "summarize_documents",
                List.of("sku", "warehouse", "location", "startDate", "endDate", "page", "size"));
        FIELDS.put("compare_inventory", List.of("sku", "warehouse", "otherWarehouse"));
        FIELDS.put(
                "list_inventory",
                List.of("sku", "warehouse", "location", "belowAvailable", "page", "size"));
        FIELDS.put(
                "list_documents",
                List.of(
                        "documentType",
                        "warehouse",
                        "otherWarehouse",
                        "status",
                        "number",
                        "startDate",
                        "endDate",
                        "dateField",
                        "page",
                        "size"));
    }

    static Map<String, List<String>> fields() {
        return Collections.unmodifiableMap(FIELDS);
    }

    static ArrayNode definitions(
            ObjectMapper json,
            java.util.function.Predicate<String> available,
            java.util.function.Predicate<String> can) {
        ArrayNode tools = json.createArrayNode();
        FIELDS.keySet().stream()
                .sorted(
                        Comparator.<String>comparingInt(
                                        name ->
                                                name.equals("clarify")
                                                        ? 2
                                                        : name.startsWith("find_") ? 1 : 0)
                                .thenComparing(Comparator.naturalOrder()))
                .forEach(
                        name -> {
                            List<String> fields = FIELDS.get(name);
                            if (!available.test(name)) return;
                            ObjectNode params =
                                    json.createObjectNode()
                                            .put("type", "object")
                                            .put("additionalProperties", false);
                            ObjectNode props = params.putObject("properties");
                            for (String field : fields) {
                                ObjectNode prop = props.putObject(field);
                                if (field.equals("page") || field.equals("size")) {
                                    prop.put("type", "integer")
                                            .put("minimum", 1)
                                            .put("maximum", field.equals("size") ? 20 : 1000);
                                } else {
                                    prop.put("type", "string").put("maxLength", 100);
                                    if (field.equals("reason"))
                                        prop.putArray("enum")
                                                .add("MISSING_INPUT")
                                                .add("FORBIDDEN")
                                                .add("OUT_OF_SCOPE");
                                    if (field.equals("tool")) {
                                        ArrayNode allowed = prop.putArray("enum");
                                        allowed.add("NONE");
                                        FIELDS.keySet().stream()
                                                .filter(
                                                        tool ->
                                                                !tool.equals("clarify")
                                                                        && !available.test(tool))
                                                .sorted()
                                                .forEach(allowed::add);
                                    }
                                    prop.put(
                                            "description",
                                            switch (field) {
                                                case "reason" ->
                                                        "缺少输入、没有所需读取权限或完全超出只读范围；不接受自由文本解释。";
                                                case "tool" ->
                                                        "FORBIDDEN时指定所需但未提供的白名单工具名称，后端会校验当前权限。其他原因填NONE。";
                                                case "documentType" ->
                                                        "PURCHASE, SALES, TRANSFER or COUNT";
                                                case "startDate", "endDate" ->
                                                        "YYYY-MM-DD，包含起止日期；Asia/Shanghai时区，最多九十二个自然日";
                                                case "belowAvailable" ->
                                                        "可用库存严格小于该十进制阈值，按仓库/库位/商品逐记录筛选；必须由用户明确提供，不是安全库存预测。";
                                                case "status" ->
                                                        "准确业务状态或UNFINISHED；未完成排除完成/调整和取消。可不填写以查询所有状态。";
                                                case "dateField" ->
                                                        "CREATED为创建日期（默认），COMPLETED为完成/收货/盘点调整日期；不得混淆创建与实际完成。";
                                                case "sku",
                                                                "warehouse",
                                                                "location",
                                                                "keyword",
                                                                "otherWarehouse" ->
                                                        "用户提供的编码或名称。不得编造ID。";
                                                default -> "用户提供的准确单号";
                                            });
                                }
                            }
                            List<String> required =
                                    switch (name) {
                                        case "clarify" -> List.of("reason", "tool");
                                        case "query_balances" -> List.of("sku");
                                        case "list_inventory" -> List.of();
                                        case "list_documents" -> List.of("documentType");
                                        case "query_ledgers" -> List.of("sku", "warehouse");
                                        case "summarize_movements", "summarize_documents" ->
                                                List.of("sku", "warehouse", "startDate", "endDate");
                                        case "query_frozen_sources", "query_sales_orders" ->
                                                List.of("sku", "warehouse");
                                        case "compare_inventory" ->
                                                List.of("sku", "warehouse", "otherWarehouse");
                                        case "get_document" -> List.of("documentType", "number");
                                        case "trace_ledger" -> List.of("ledgerNo");
                                        case "find_location" -> List.of("keyword", "warehouse");
                                        default -> List.of("keyword");
                                    };
                            params.set("required", json.valueToTree(required));
                            tools.addObject()
                                    .put("type", "function")
                                    .putObject("function")
                                    .put("name", name)
                                    .put(
                                            "description",
                                            switch (name) {
                                                case "clarify" ->
                                                        "仅当缺少用户查询所必需的商品/仓库/单号，或所需业务工具未提供时使用。用户已给出名称时必须调用业务工具验证，即使名称含测试或不存在；不得以数据是否存在为缺参理由。FORBIDDEN须提供未授权的业务工具名tool。OUT_OF_SCOPE仅用于整个问题不含可支持的读取需求。";
                                                case "summarize_movements" ->
                                                        "指定商品、仓库、可选库位及日期区间的完整流水差量汇总；按业务动作区分销售、调拨、盘点。不是期初期末余额，不证明完整历史。";
                                                case "query_frozen_sources" ->
                                                        "查询当前有效销售/调拨冻结来源及全范围总量；两类明细各自分页。核对当前余额冻结总量，不证明逐库位一致。";
                                                case "query_balances" ->
                                                        "按用户给出的商品名称/编码直接查单仓或多仓库存，工具内部解析名称并返回候选；无需先find。汇总覆盖匹配库位，明细分页。";
                                                case "list_inventory" ->
                                                        "查询全部或指定仓库的库存列表；商品可选，不要求单个商品。可按用户明确的belowAvailable阈值筛选。每条为一个商品/库位，列表分页，不能跨商品累加数量。";
                                                case "list_documents" ->
                                                        "查询采购PURCHASE、销售SALES、调拨TRANSFER、盘点COUNT单据列表。只有类型必填；仓库、状态、日期可选；UNFINISHED查未完成。调拨warehouse为源仓，otherWarehouse为目标仓。日期默认创建，实际入库/出库/收货用COMPLETED。";
                                                case "query_ledgers" ->
                                                        "直接查询指定商品、仓库的一页库存流水；不代表完整历史或历史总量。";
                                                case "get_document" -> "按准确单号查询采购、销售、调拨或盘点单据状态及明细。";
                                                case "trace_ledger" -> "按准确流水号追溯业务来源单据。";
                                                case "query_sales_orders" ->
                                                        "商品、仓库对应的销售单，默认UNFINISHED包含草稿、已冻结和已审核；分页不等于全部单据。";
                                                case "summarize_documents" ->
                                                        "区间内按单据和业务动作汇总流水，用于准确单号追溯，单据分组列表分页。";
                                                case "compare_inventory" ->
                                                        "同一快照比较两个仓库的余额与冻结来源总量，由后端计算差量。";
                                                default ->
                                                        "仅用于单独查找商品/仓库/库位资料。其他业务工具内部解析名称，无需预先调用此工具。";
                                            })
                                    .set("parameters", params);
                        });
        return tools;
    }
}
