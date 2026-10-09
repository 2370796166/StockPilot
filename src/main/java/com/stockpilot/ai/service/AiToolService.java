package com.stockpilot.ai.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.stockpilot.ai.request.AiQuestionRequest.Selection;
import com.stockpilot.ai.vo.AiAnswerVO.*;
import com.stockpilot.inventory.count.service.InventoryCountApplicationService;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.inventory.request.*;
import com.stockpilot.inventory.service.InventoryQueryApplicationService;
import com.stockpilot.masterdata.request.*;
import com.stockpilot.masterdata.service.*;
import com.stockpilot.purchase.service.PurchaseReceiptApplicationService;
import com.stockpilot.sales.service.SalesOutboundApplicationService;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.exception.BusinessException;
import com.stockpilot.transfer.service.StockTransferApplicationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Closed read-only tool boundary: validate, authorize, resolve names, then call public Services.
 */
@Service
public class AiToolService {
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

    private final ObjectMapper json;
    private final SkuApplicationService skuService;
    private final WarehouseApplicationService warehouseService;
    private final WarehouseLocationApplicationService locationService;
    private final InventoryQueryApplicationService inventoryQueryService;
    private final SalesOutboundApplicationService salesOutboundService;
    private final PurchaseReceiptApplicationService purchaseReceiptService;
    private final StockTransferApplicationService stockTransferService;
    private final InventoryCountApplicationService inventoryCountService;
    private final AiFrozenInventoryService aiFrozenInventoryService;
    private final MasterDataReferenceQueryService masterDataReferenceQueryService;

    public AiToolService(
            ObjectMapper json,
            SkuApplicationService skuService,
            WarehouseApplicationService warehouseService,
            WarehouseLocationApplicationService locationService,
            InventoryQueryApplicationService inventoryQueryService,
            SalesOutboundApplicationService salesOutboundService,
            PurchaseReceiptApplicationService purchaseReceiptService,
            StockTransferApplicationService stockTransferService,
            InventoryCountApplicationService inventoryCountService,
            AiFrozenInventoryService aiFrozenInventoryService,
            MasterDataReferenceQueryService masterDataReferenceQueryService) {
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.skuService = skuService;
        this.warehouseService = warehouseService;
        this.locationService = locationService;
        this.inventoryQueryService = inventoryQueryService;
        this.salesOutboundService = salesOutboundService;
        this.purchaseReceiptService = purchaseReceiptService;
        this.stockTransferService = stockTransferService;
        this.inventoryCountService = inventoryCountService;
        this.aiFrozenInventoryService = aiFrozenInventoryService;
        this.masterDataReferenceQueryService = masterDataReferenceQueryService;
    }

    public boolean isKnownTool(String name) {
        return FIELDS.containsKey(name);
    }

    public boolean mayQuery(String name) {
        return FIELDS.containsKey(name) && available(name);
    }

    /** Map a model's code back to the user's actual name, never choose a same-name candidate. */
    public String mentionedName(String kind, String code, String question) {
        if (!can("MASTER_DATA_READ") || !Set.of("sku", "warehouse").contains(kind)) return null;
        return masterDataReferenceQueryService
                .exactCode(kind, code, null)
                .map(com.stockpilot.masterdata.vo.ReferenceDataVO::name)
                .filter(
                        name ->
                                name != null
                                        && !name.isBlank()
                                        && question.toLowerCase(Locale.ROOT)
                                                .contains(name.toLowerCase(Locale.ROOT)))
                .orElse(null);
    }

    public boolean canReuse(Evidence evidence) {
        return available(evidence.tool())
                && (!evidence.data().has("references") || can("MASTER_DATA_READ"))
                && evidence.sources().stream().allMatch(source -> can(source.authority()));
    }

    /** Inventory facts remain readable when their linked document page is not authorized. */
    public Evidence authorizedView(Evidence evidence) {
        if (!available(evidence.tool())
                || evidence.data().has("references") && !can("MASTER_DATA_READ")) return null;
        if (Set.of("get_document", "trace_ledger", "list_documents").contains(evidence.tool())
                && evidence.data().has("documentType")
                && !can(permission(evidence.data().path("documentType").asText()))) return null;
        return new Evidence(
                evidence.tool(),
                evidence.status(),
                evidence.message(),
                evidence.data(),
                evidence.candidates(),
                evidence.sources().stream().filter(source -> can(source.authority())).toList(),
                evidence.queriedAt(),
                evidence.evidenceId());
    }

    public ArrayNode definitions() {
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
                            if (!available(name)) return;
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
                                                                        && !available(tool))
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

    public Evidence execute(String name, JsonNode args, List<Selection> selections) {
        try {
            if (FIELDS.containsKey(name) && !available(name))
                throw new ToolStop("FORBIDDEN", "当前账号缺少所需业务读取权限，请联系管理员");
            if (name.equals("clarify")
                    && args != null
                    && args.isObject()
                    && args.has("tool")
                    && (args.path("tool").isNull()
                            || args.path("tool").isTextual()
                                    && args.path("tool").asText().isBlank())) {
                args = args.deepCopy();
                ((ObjectNode) args).put("tool", "NONE");
            }
            validate(name, args);
            return switch (name) {
                case "clarify" -> {
                    String reason = text(args, "reason");
                    if ("FORBIDDEN".equals(reason)) {
                        String target = text(args, "tool");
                        boolean noBusinessReads =
                                FIELDS.keySet().stream()
                                        .filter(
                                                tool ->
                                                        !tool.equals("clarify")
                                                                && !tool.startsWith("find_"))
                                        .noneMatch(AiToolService::available);
                        if ((target == null || !FIELDS.containsKey(target)) && noBusinessReads)
                            yield result(
                                    name,
                                    "FORBIDDEN",
                                    "当前账号没有库存或单据读取权限，请联系管理员",
                                    json.createObjectNode(),
                                    List.of(),
                                    List.of());
                        if (target == null || !FIELDS.containsKey(target) || available(target))
                            throw new ToolStop("INVALID_ARGUMENTS", "无法确认所需业务读取权限");
                        yield result(
                                name,
                                "FORBIDDEN",
                                "当前账号缺少所需业务读取权限，请联系管理员",
                                json.createObjectNode(),
                                List.of(),
                                List.of());
                    }
                    if (!"MISSING_INPUT".equals(reason) && !"OUT_OF_SCOPE".equals(reason))
                        throw new ToolStop("INVALID_ARGUMENTS", "澄清原因无效");
                    yield result(
                            name,
                            "NEEDS_CLARIFICATION",
                            "OUT_OF_SCOPE".equals(reason)
                                    ? "本助手仅支持只读查询，请提供商品、仓库或准确业务单号、流水号"
                                    : "请补充商品和仓库名称，或准确业务单号、流水号",
                            json.createObjectNode(),
                            List.of(),
                            List.of());
                }
                case "find_sku", "find_warehouse", "find_location" -> {
                    String kind = name.substring(5);
                    Long warehouse =
                            kind.equals("location")
                                    ? resolve(
                                                    "warehouse",
                                                    text(args, "warehouse"),
                                                    null,
                                                    selections)
                                            .id()
                                    : null;
                    Candidate candidate =
                            resolve(kind, text(args, "keyword"), warehouse, selections);
                    ObjectNode candidateData = json.createObjectNode();
                    candidateData.set(kind, json.valueToTree(candidate));
                    enrich(candidateData);
                    yield result(name, "OK", "已找到唯一资料", candidateData, List.of(), List.of());
                }
                case "query_balances", "query_ledgers" -> inventory(name, args, selections);
                case "list_inventory" -> inventoryList(args, selections);
                case "list_documents" -> documentList(args, selections);
                case "summarize_movements", "query_frozen_sources" ->
                        analysis(name, args, selections);
                case "get_document" ->
                        document(name, text(args, "documentType"), number(args, "number"));
                case "trace_ledger" -> trace(name, number(args, "ledgerNo"));
                case "query_sales_orders", "summarize_documents", "compare_inventory" ->
                        extended(name, args, selections);
                default -> throw new ToolStop("INVALID_TOOL", "工具不在只读白名单中");
            };
        } catch (ToolStop e) {
            return result(
                    name,
                    e.status,
                    e.getMessage(),
                    json.createObjectNode(),
                    e.candidates,
                    List.of());
        } catch (BusinessException e) {
            return result(
                    name,
                    e.getErrorCode().httpStatus().value() == 404 ? "NO_DATA" : "QUERY_FAILED",
                    e.getErrorCode().httpStatus().value() == 404
                            ? "未找到匹配记录，不能视为库存为零"
                            : "业务查询失败，未得到有效数据",
                    json.createObjectNode(),
                    List.of(),
                    List.of());
        } catch (RuntimeException e) {
            return result(
                    name,
                    "QUERY_FAILED",
                    "业务查询失败，未得到有效数据",
                    json.createObjectNode(),
                    List.of(),
                    List.of());
        }
    }

    private void validate(String name, JsonNode args) {
        if (!FIELDS.containsKey(name)) throw new ToolStop("INVALID_TOOL", "工具不在只读白名单中");
        if (args == null || !args.isObject()) throw new ToolStop("INVALID_ARGUMENTS", "工具参数必须是对象");
        args.fields()
                .forEachRemaining(
                        entry -> {
                            String key = entry.getKey();
                            JsonNode value = entry.getValue();
                            if (!FIELDS.get(name).contains(key))
                                throw new ToolStop("INVALID_ARGUMENTS", "工具包含未知参数");
                            if (key.equals("page") || key.equals("size")) {
                                int max = key.equals("size") ? 20 : 1000;
                                if (!value.isIntegralNumber()
                                        || !value.canConvertToInt()
                                        || value.asInt() < 1
                                        || value.asInt() > max)
                                    throw new ToolStop("INVALID_ARGUMENTS", "分页参数超出范围");
                            } else if (!value.isTextual()
                                    || value.asText().isBlank()
                                    || value.asText().length() > 100) {
                                throw new ToolStop("INVALID_ARGUMENTS", "工具字符串参数无效");
                            }
                        });
    }

    private Candidate resolve(
            String kind, String keyword, Long warehouseId, List<Selection> selections) {
        require("MASTER_DATA_READ");
        if (keyword == null)
            throw new ToolStop("NEEDS_CLARIFICATION", "请提供" + kindLabel(kind) + "名称或编码");
        if (!kind.equals("location") || warehouseId != null) {
            var exact = masterDataReferenceQueryService.exactCode(kind, keyword, warehouseId);
            if (exact.isPresent()) {
                var v = exact.get();
                return new Candidate(kind, keyword, v.id(), v.code(), v.name());
            }
        }
        PageQuery query =
                kind.equals("sku")
                        ? new SkuPageQuery()
                        : kind.equals("location") ? new LocationPageQuery() : new PageQuery();
        query.setKeyword(keyword);
        query.setSize(20);
        List<Candidate> candidates;
        long total;
        if (kind.equals("sku")) {
            var page = skuService.page((SkuPageQuery) query);
            total = page.total();
            candidates =
                    page.records().stream()
                            .map(v -> new Candidate(kind, keyword, v.id(), v.code(), v.name()))
                            .toList();
        } else if (kind.equals("location")) {
            if (warehouseId == null) throw new ToolStop("NEEDS_CLARIFICATION", "请先指定库位所属仓库");
            ((LocationPageQuery) query).setWarehouseId(warehouseId);
            var page = locationService.page((LocationPageQuery) query);
            total = page.total();
            candidates =
                    page.records().stream()
                            .map(v -> new Candidate(kind, keyword, v.id(), v.code(), v.name()))
                            .toList();
        } else {
            var page = warehouseService.page(query);
            total = page.total();
            candidates =
                    page.records().stream()
                            .map(v -> new Candidate(kind, keyword, v.id(), v.code(), v.name()))
                            .toList();
        }
        if (total == 0) throw new ToolStop("NO_DATA", "未找到匹配的" + kindLabel(kind) + "，不能视为库存为零");
        if (selections != null)
            for (Selection selection : selections) {
                if (kind.equals(selection.kind()) && keyword.equals(selection.keyword())) {
                    return candidates.stream()
                            .filter(c -> c.id().equals(selection.id()))
                            .findFirst()
                            .orElseThrow(
                                    () -> new ToolStop("INVALID_ARGUMENTS", "所选候选已失效，请重新缩小查询条件"));
                }
            }
        // Codes are unique business identifiers. An exact code must not become ambiguous merely
        // because a fuzzy keyword query also matched longer codes; same-name matches still pause.
        var exactCode =
                candidates.stream().filter(c -> c.code().equalsIgnoreCase(keyword)).toList();
        if (exactCode.size() == 1) return exactCode.get(0);
        if (total != 1)
            throw new ToolStop(
                    "NEEDS_SELECTION",
                    "找到多个" + kindLabel(kind) + "，请选择；仅显示前二十项，更多结果请缩小关键词",
                    candidates);
        return candidates.get(0);
    }

    private Evidence inventory(String name, JsonNode args, List<Selection> selections) {
        require("INVENTORY_READ");
        Candidate sku = resolve("sku", text(args, "sku"), null, selections);
        String warehouseName = text(args, "warehouse");
        if (name.equals("query_ledgers") && warehouseName == null)
            throw new ToolStop("NEEDS_CLARIFICATION", "查询流水需要指定仓库");
        Candidate warehouse =
                warehouseName == null
                        ? null
                        : resolve("warehouse", warehouseName, null, selections);
        Candidate location =
                text(args, "location") == null
                        ? null
                        : resolve(
                                "location",
                                text(args, "location"),
                                warehouse == null ? null : warehouse.id(),
                                selections);
        ObjectNode data = json.createObjectNode();
        data.set("sku", json.valueToTree(sku));
        if (warehouse != null) data.set("warehouse", json.valueToTree(warehouse));
        if (location != null) data.set("location", json.valueToTree(location));
        long page = args.path("page").asLong(1), size = args.path("size").asLong(20);
        List<Source> sources = new ArrayList<>();
        String path;
        String message;
        boolean empty;
        if (name.equals("query_balances")) {
            var q = new InventoryBalancePageQuery();
            q.setSkuId(sku.id());
            q.setWarehouseId(warehouse == null ? null : warehouse.id());
            q.setLocationId(location == null ? null : location.id());
            q.setPage(page);
            q.setSize(size);
            var overview = inventoryQueryService.balanceOverview(q);
            data.set(
                    "warehouses",
                    pageData(
                            overview.warehouses(),
                            "warehouseId",
                            "actualQuantity",
                            "availableQuantity",
                            "frozenQuantity"));
            data.set(
                    "locations",
                    pageData(
                            overview.locations(),
                            "warehouseId",
                            "locationId",
                            "skuId",
                            "actualQuantity",
                            "availableQuantity",
                            "frozenQuantity",
                            "updatedAt"));
            empty = overview.warehouses().total() == 0;
            message = "仓库数量由MySQL汇总全部匹配库位；仓库列表和库位明细分别分页，未显示的仓库不能视为零库存。";
            path = "/inventory/balances";
        } else {
            var q = new InventoryLedgerPageQuery();
            q.setSkuId(sku.id());
            q.setWarehouseId(warehouse.id());
            q.setLocationId(location == null ? null : location.id());
            q.setBusinessNo(args.has("businessNo") ? number(args, "businessNo") : null);
            q.setPage(page);
            q.setSize(size);
            if (args.has("startDate") || args.has("endDate")) {
                q.setStartDate(date(args, "startDate"));
                q.setEndDate(date(args, "endDate"));
                if (!q.isPeriodValid()) throw new ToolStop("INVALID_ARGUMENTS", "日期范围无效");
                data.putObject("period")
                        .put("startDate", q.getStartDate().toString())
                        .put("endDate", q.getEndDate().toString())
                        .put("timezone", "Asia/Shanghai");
            }
            if (text(args, "businessType") != null) {
                try {
                    q.setBusinessType(InventoryBusinessType.valueOf(text(args, "businessType")));
                } catch (IllegalArgumentException e) {
                    throw new ToolStop("INVALID_ARGUMENTS", "业务动作无效");
                }
            }
            var records = inventoryQueryService.pageLedgers(q);
            ObjectNode ledgerPage =
                    pageData(
                            records,
                            "ledgerNo",
                            "businessType",
                            "businessNo",
                            "warehouseId",
                            "locationId",
                            "skuId",
                            "beforeActualQuantity",
                            "changeActualQuantity",
                            "afterActualQuantity",
                            "beforeAvailableQuantity",
                            "changeAvailableQuantity",
                            "afterAvailableQuantity",
                            "beforeFrozenQuantity",
                            "changeFrozenQuantity",
                            "afterFrozenQuantity",
                            "countBookQuantity",
                            "countedQuantity",
                            "differenceQuantity",
                            "occurredAt");
            for (int i = 0; i < records.records().size(); i++) {
                var ledger = records.records().get(i);
                ((ObjectNode) ledgerPage.path("records").get(i))
                        .put("meaning", meaning(ledger.businessType()));
                String type = documentType(ledger.businessType());
                if (type != null) sources.add(documentSource(type, ledger.businessNo()));
            }
            data.set("ledgers", ledgerPage);
            empty = records.total() == 0;
            message = "仅为指定条件下的一页流水，匹配总条数不是数量汇总。缺少其他页、有效单据状态及完整版本链，不能推断完整历史、总出库量或全部冻结来源。";
            path = "/inventory/ledgers";
        }
        sources.add(
                new Source(
                        "查看查询条件对应的库存页面",
                        path
                                + "?skuId="
                                + sku.id()
                                + (warehouse == null ? "" : "&warehouseId=" + warehouse.id())
                                + (location == null ? "" : "&locationId=" + location.id()),
                        "INVENTORY_READ"));
        enrich(data);
        return result(
                name,
                empty ? "NO_DATA" : "OK",
                empty ? "没有匹配记录，不能视为库存为零。" + message : message,
                data,
                List.of(),
                sources);
    }

    private Evidence inventoryList(JsonNode args, List<Selection> selections) {
        require("INVENTORY_READ");
        var data = json.createObjectNode();
        var query = new InventoryBalancePageQuery();
        if (text(args, "warehouse") != null) {
            var warehouse = resolve("warehouse", text(args, "warehouse"), null, selections);
            query.setWarehouseId(warehouse.id());
            data.set("warehouse", json.valueToTree(warehouse));
        }
        if (text(args, "sku") != null) {
            var sku = resolve("sku", text(args, "sku"), null, selections);
            query.setSkuId(sku.id());
            data.set("sku", json.valueToTree(sku));
        }
        if (text(args, "location") != null) {
            var location =
                    resolve("location", text(args, "location"), query.getWarehouseId(), selections);
            query.setLocationId(location.id());
            data.set("location", json.valueToTree(location));
        }
        if (args.has("belowAvailable")) {
            try {
                var threshold = new BigDecimal(text(args, "belowAvailable"));
                if (threshold.signum() < 0
                        || threshold.scale() > 4
                        || threshold.precision() - threshold.scale() > 15)
                    throw new NumberFormatException();
                query.setBelowAvailable(threshold);
                data.put("belowAvailable", threshold.toPlainString());
            } catch (NumberFormatException failure) {
                throw new ToolStop("INVALID_ARGUMENTS", "数量阈值必须为合法的非负十进制数，最多四位小数");
            }
        }
        query.setPage(args.path("page").asLong(1));
        query.setSize(args.path("size").asLong(20));
        var page = inventoryQueryService.pageBalances(query);
        data.set(
                "inventory",
                pageData(
                        page,
                        "warehouseId",
                        "locationId",
                        "skuId",
                        "actualQuantity",
                        "availableQuantity",
                        "frozenQuantity",
                        "updatedAt"));
        enrich(data);
        return result(
                "list_inventory",
                page.total() == 0 ? "NO_DATA" : "OK",
                "库存列表按商品和库位显示，匹配总数由MySQL筛选；当前页不等于全部，不同商品数量不能相加。阈值筛选不等于安全库存预警或需求预测。",
                data,
                List.of(),
                List.of(
                        new Source(
                                "查看库存列表",
                                "/inventory/balances"
                                        + (query.getWarehouseId() == null
                                                ? ""
                                                : "?warehouseId=" + query.getWarehouseId()),
                                "INVENTORY_READ")));
    }

    private Evidence documentList(JsonNode args, List<Selection> selections) {
        String type = text(args, "documentType");
        if (type == null) throw new ToolStop("NEEDS_CLARIFICATION", "请指定采购、销售、调拨或盘点单据类型");
        require(permission(type));
        var data = json.createObjectNode().put("documentType", type);
        Long warehouseId = null, targetId = null;
        if (text(args, "warehouse") != null) {
            var warehouse = resolve("warehouse", text(args, "warehouse"), null, selections);
            warehouseId = warehouse.id();
            data.set("warehouse", json.valueToTree(warehouse));
        }
        if (text(args, "otherWarehouse") != null) {
            if (!type.equals("TRANSFER")) throw new ToolStop("INVALID_ARGUMENTS", "只有调拨列表支持目标仓库条件");
            var warehouse = resolve("warehouse", text(args, "otherWarehouse"), null, selections);
            targetId = warehouse.id();
            data.set("otherWarehouse", json.valueToTree(warehouse));
        }
        String status = text(args, "status"), number = text(args, "number");
        boolean unfinished = "UNFINISHED".equals(status);
        long page = args.path("page").asLong(1), size = args.path("size").asLong(20);
        PageResult<?> records;
        try {
            records =
                    switch (type) {
                        case "PURCHASE" -> {
                            var query =
                                    new com.stockpilot.purchase.request.PurchaseReceiptRequests
                                            .PageQuery();
                            query.setWarehouseId(warehouseId);
                            query.setReceiptNo(number);
                            query.setPage(page);
                            query.setSize(size);
                            if (status != null && !unfinished)
                                query.setStatus(
                                        com.stockpilot.purchase.domain.PurchaseReceiptStatus
                                                .valueOf(status));
                            configurePeriod(query, args, data, unfinished);
                            yield purchaseReceiptService.page(query);
                        }
                        case "SALES" -> {
                            var query =
                                    new com.stockpilot.sales.request.SalesOutboundRequests
                                            .PageQuery();
                            query.setWarehouseId(warehouseId);
                            query.setOutboundNo(number);
                            query.setPage(page);
                            query.setSize(size);
                            if (status != null && !unfinished)
                                query.setStatus(
                                        com.stockpilot.sales.request.SalesOutboundRequests
                                                .SalesOutboundStatusFilter.valueOf(status));
                            configurePeriod(query, args, data, unfinished);
                            yield salesOutboundService.page(query);
                        }
                        case "TRANSFER" -> {
                            var query =
                                    new com.stockpilot.transfer.request.StockTransferRequests
                                            .PageQuery();
                            query.setSourceWarehouseId(warehouseId);
                            query.setTargetWarehouseId(targetId);
                            query.setTransferNo(number);
                            query.setPage(page);
                            query.setSize(size);
                            if (status != null && !unfinished)
                                query.setStatus(
                                        com.stockpilot.transfer.domain.StockTransferStatus.valueOf(
                                                status));
                            configurePeriod(query, args, data, unfinished);
                            yield stockTransferService.page(query);
                        }
                        case "COUNT" -> {
                            var query =
                                    new com.stockpilot.inventory.count.request
                                            .InventoryCountRequests.PageQuery();
                            query.setWarehouseId(warehouseId);
                            query.setCountNo(number);
                            query.setPage(page);
                            query.setSize(size);
                            if (status != null && !unfinished)
                                query.setStatus(
                                        com.stockpilot.inventory.count.domain.InventoryCountStatus
                                                .valueOf(status));
                            configurePeriod(query, args, data, unfinished);
                            yield inventoryCountService.page(query);
                        }
                        default -> throw new ToolStop("INVALID_ARGUMENTS", "单据类型无效");
                    };
        } catch (IllegalArgumentException failure) {
            throw new ToolStop("INVALID_ARGUMENTS", "单据状态或日期条件无效");
        }
        if (status != null) data.put("statusFilter", status);
        ObjectNode projected =
                pageData(
                        records,
                        "receiptNo",
                        "outboundNo",
                        "transferNo",
                        "countNo",
                        "warehouseId",
                        "sourceWarehouseId",
                        "targetWarehouseId",
                        "status",
                        "createdAt",
                        "completedAt",
                        "adjustedAt");
        var sources = new ArrayList<Source>();
        for (JsonNode row : projected.path("records")) {
            var record = (ObjectNode) row;
            String field =
                    switch (type) {
                        case "PURCHASE" -> "receiptNo";
                        case "SALES" -> "outboundNo";
                        case "TRANSFER" -> "transferNo";
                        default -> "countNo";
                    };
            record.put("businessNo", record.path(field).asText());
            record.remove(field);
            sources.add(documentSource(type, record.path("businessNo").asText()));
        }
        data.set("documents", projected);
        enrich(data);
        return result(
                "list_documents",
                records.total() == 0 ? "NO_DATA" : "OK",
                "单据列表按条件在MySQL筛选；当前页不等于全部。未完成排除已完成/已调整和已取消。日期口径以dateField为准，单据状态不替代库存流水。",
                data,
                List.of(),
                sources);
    }

    private void configurePeriod(
            com.stockpilot.shared.query.DocumentDateRangeQuery query,
            JsonNode args,
            ObjectNode data,
            boolean unfinished) {
        query.setUnfinished(unfinished);
        query.setDateField(
                com.stockpilot.shared.query.DocumentDateRangeQuery.DateField.valueOf(
                        args.path("dateField").asText("CREATED")));
        data.put("dateField", query.getDateField().name());
        if (args.has("startDate") || args.has("endDate")) {
            query.setStartDate(date(args, "startDate"));
            query.setEndDate(date(args, "endDate"));
            if (!query.isPeriodValid()) throw new IllegalArgumentException();
            data.putObject("period")
                    .put("startDate", query.getStartDate().toString())
                    .put("endDate", query.getEndDate().toString())
                    .put("timezone", "Asia/Shanghai");
        }
    }

    private Evidence analysis(String name, JsonNode args, List<Selection> selections) {
        require("INVENTORY_READ");
        if (name.equals("query_frozen_sources")) {
            require("SALES_OUTBOUND_READ");
            require("TRANSFER_READ");
        }
        java.time.LocalDate start = null, end = null;
        if (name.equals("summarize_movements")) {
            start = date(args, "startDate");
            end = date(args, "endDate");
            if (end.isBefore(start) || java.time.temporal.ChronoUnit.DAYS.between(start, end) >= 92)
                throw new ToolStop("INVALID_ARGUMENTS", "起止日期须顺序正确，且最多九十二个自然日");
        }
        if (text(args, "warehouse") == null) throw new ToolStop("NEEDS_CLARIFICATION", "请指定查询仓库");
        Candidate sku = resolve("sku", text(args, "sku"), null, selections);
        Candidate warehouse = resolve("warehouse", text(args, "warehouse"), null, selections);
        Candidate location =
                text(args, "location") == null
                        ? null
                        : resolve("location", text(args, "location"), warehouse.id(), selections);
        var dimension =
                new InventoryDimensionQuery(
                        sku.id(), warehouse.id(), location == null ? null : location.id());
        ObjectNode data = json.createObjectNode();
        data.set("sku", json.valueToTree(sku));
        data.set("warehouse", json.valueToTree(warehouse));
        data.put("skuId", sku.id()).put("warehouseId", warehouse.id());
        if (location != null) {
            data.set("location", json.valueToTree(location));
            data.put("locationId", location.id());
        }
        List<Source> sources = new ArrayList<>();
        String filters =
                "?skuId="
                        + sku.id()
                        + "&warehouseId="
                        + warehouse.id()
                        + (location == null ? "" : "&locationId=" + location.id());
        String status = "OK", message;
        if (name.equals("summarize_movements")) {
            var query = new InventoryPeriodQuery(dimension, start, end);
            var summary = inventoryQueryService.periodSummary(query);
            data.putObject("period")
                    .put("startDate", start.toString())
                    .put("endDate", end.toString())
                    .put("endExclusive", query.endExclusive().toString())
                    .put("timezone", "Asia/Shanghai");
            data.set(
                    "summary",
                    project(
                            summary,
                            "ledgerCount",
                            "changeActualQuantity",
                            "changeAvailableQuantity",
                            "changeFrozenQuantity"));
            ((ObjectNode) data.get("summary")).put("completeForFilter", true);
            ArrayNode movements = data.putArray("movements");
            for (var movement : summary.movements()) {
                ObjectNode row =
                        project(
                                movement,
                                "businessType",
                                "ledgerCount",
                                "changeActualQuantity",
                                "changeAvailableQuantity",
                                "changeFrozenQuantity");
                row.put("meaning", meaning(movement.businessType()));
                movements.add(row);
            }
            message = "汇总覆盖指定日期与库存维度的全部匹配流水，按业务动作区分；不是期初期末余额，不验证版本链，不代表完整历史，也不用于判断当前冻结来源。";
            if (summary.ledgerCount() == 0) {
                status = "NO_DATA";
                message = "指定区间没有流水，不能据此认定库存为零。" + message;
            }
            sources.add(
                    new Source(
                            "查看本日期区间的库存流水",
                            "/inventory/ledgers"
                                    + filters
                                    + "&startDate="
                                    + start
                                    + "&endDate="
                                    + end,
                            "INVENTORY_READ"));
        } else {
            var snapshot =
                    aiFrozenInventoryService.query(
                            dimension, args.path("page").asLong(1), args.path("size").asLong(20));
            data.set(
                    "salesSources",
                    pageData(
                            snapshot.sales().sources(),
                            "documentType",
                            "businessNo",
                            "warehouseId",
                            "locationId",
                            "skuId",
                            "status",
                            "quantity",
                            "reservedAt"));
            data.set(
                    "transferSources",
                    pageData(
                            snapshot.transfer().sources(),
                            "documentType",
                            "businessNo",
                            "warehouseId",
                            "locationId",
                            "skuId",
                            "status",
                            "quantity",
                            "reservedAt"));
            ObjectNode totals = data.putObject("frozenTotals");
            totals.put("salesQuantity", snapshot.sales().totalQuantity().toPlainString())
                    .put("transferQuantity", snapshot.transfer().totalQuantity().toPlainString())
                    .put("sourceQuantity", snapshot.sourceQuantity().toPlainString());
            if (snapshot.balance().isPresent()) {
                totals.put(
                                "frozenQuantity",
                                snapshot.balance().get().frozenQuantity().toPlainString())
                        .put("differenceQuantity", snapshot.differenceQuantity().toPlainString());
                totals.put(
                                "actualQuantity",
                                snapshot.balance().get().actualQuantity().toPlainString())
                        .put(
                                "availableQuantity",
                                snapshot.balance().get().availableQuantity().toPlainString())
                        .put(
                                "unavailableQuantity",
                                snapshot.balance()
                                        .get()
                                        .actualQuantity()
                                        .subtract(snapshot.balance().get().availableQuantity())
                                        .toPlainString());
                data.put(
                        "totalCheck",
                        snapshot.differenceQuantity().signum() == 0
                                ? "TOTAL_MATCH"
                                : "TOTAL_MISMATCH");
            } else data.put("totalCheck", "BALANCE_MISSING");
            message =
                    "同一短只读快照：销售仅含已冻结/已审核未出库，调拨仅含已提交/已审核未调出；汇总覆盖全部匹配有效明细，两类来源分别分页。仅核对查询范围的总量，不能证明逐库位或逐单据一致；历史冻结流水累计不能替代当前冻结。";
            if (snapshot.balance().isEmpty()) {
                if (snapshot.sourceQuantity().signum() > 0)
                    message = "发现有效冻结单据但库存余额缺失，无法完成总量核对，需核查数据。" + message;
                else {
                    status = "NO_DATA";
                    message = "未找到库存余额，不能视为零库存。" + message;
                }
            } else if (snapshot.differenceQuantity().signum() != 0)
                message = "当前余额冻结量与有效单据占用总量不一致，需核对业务状态和完整流水；不会自动调整库存。" + message;
            else if (snapshot.sourceQuantity().signum() == 0)
                message = "当前余额冻结量为零，未发现有效销售或调拨冻结来源。" + message;
            for (var row : snapshot.sales().sources().records())
                sources.add(documentSource("SALES", row.businessNo()));
            for (var row : snapshot.transfer().sources().records())
                sources.add(documentSource("TRANSFER", row.businessNo()));
            sources.add(new Source("查看当前库存余额", "/inventory/balances" + filters, "INVENTORY_READ"));
        }
        enrich(data);
        return result(name, status, message, data, List.of(), sources);
    }

    private Evidence extended(String name, JsonNode args, List<Selection> selections) {
        Candidate sku = resolve("sku", text(args, "sku"), null, selections);
        Candidate warehouse = resolve("warehouse", text(args, "warehouse"), null, selections);
        Candidate location =
                text(args, "location") == null
                        ? null
                        : resolve("location", text(args, "location"), warehouse.id(), selections);
        var dimension =
                new InventoryDimensionQuery(
                        sku.id(), warehouse.id(), location == null ? null : location.id());
        ObjectNode data = json.createObjectNode();
        data.set("sku", json.valueToTree(sku));
        data.set("warehouse", json.valueToTree(warehouse));
        if (location != null) data.set("location", json.valueToTree(location));
        List<Source> sources = new ArrayList<>();
        String status = "OK";
        String message;
        long page = args.path("page").asLong(1), size = args.path("size").asLong(20);
        if (name.equals("query_sales_orders")) {
            String filter = args.path("status").asText("UNFINISHED");
            if (!Set.of("UNFINISHED", "DRAFT", "RESERVED", "APPROVED", "COMPLETED", "CANCELLED")
                    .contains(filter)) throw new ToolStop("INVALID_ARGUMENTS", "销售状态无效");
            var records = salesOutboundService.inventoryOrders(dimension, filter, page, size);
            data.put("statusFilter", filter);
            data.set("salesOrders", pageData(records, "businessNo", "status", "quantity"));
            for (var row : records.records())
                sources.add(documentSource("SALES", row.businessNo()));
            if (records.total() == 0) status = "NO_DATA";
            message = "按商品和库存范围查询销售单，未完成包含草稿、已冻结和已审核；草稿尚未占用库存。仅显示当前页，数量是匹配范围的单据行数量，不是当前冻结总量。";
        } else if (name.equals("summarize_documents")) {
            var query =
                    new InventoryPeriodQuery(
                            dimension, date(args, "startDate"), date(args, "endDate"));
            var records = inventoryQueryService.periodDocuments(query, page, size);
            data.putObject("period")
                    .put("startDate", query.startDate().toString())
                    .put("endDate", query.endDate().toString())
                    .put("timezone", "Asia/Shanghai");
            data.set(
                    "documentMovements",
                    pageData(
                            records,
                            "businessNo",
                            "businessType",
                            "ledgerCount",
                            "changeActualQuantity",
                            "changeAvailableQuantity",
                            "changeFrozenQuantity"));
            for (var row : records.records())
                if (documentType(row.businessType()) != null)
                    sources.add(documentSource(documentType(row.businessType()), row.businessNo()));
            if (records.total() == 0) status = "NO_DATA";
            message = "区间内每个单据/动作的差量覆盖全部匹配流水，分组列表分页；当前页不能代替完整区间总量，单据当前状态不代表当时状态。";
        } else {
            Candidate other = resolve("warehouse", text(args, "otherWarehouse"), null, selections);
            if (warehouse.id().equals(other.id()))
                throw new ToolStop("INVALID_ARGUMENTS", "比较需要两个不同仓库");
            data.set("otherWarehouse", json.valueToTree(other));
            var left = aiFrozenInventoryService.query(dimension, 1, 1);
            var right =
                    aiFrozenInventoryService.query(
                            new InventoryDimensionQuery(sku.id(), other.id(), null), 1, 1);
            data.put("sameSnapshot", true);
            var rows = data.putArray("comparison");
            for (var pair : List.of(Map.entry(warehouse, left), Map.entry(other, right))) {
                var row = rows.addObject().put("warehouseId", pair.getKey().id());
                row.put("balanceExists", pair.getValue().balance().isPresent());
                pair.getValue()
                        .balance()
                        .ifPresent(
                                balance ->
                                        row.setAll(
                                                project(
                                                        balance,
                                                        "actualQuantity",
                                                        "availableQuantity",
                                                        "frozenQuantity")));
                row.put("salesQuantity", pair.getValue().sales().totalQuantity().toPlainString());
                row.put(
                        "transferQuantity",
                        pair.getValue().transfer().totalQuantity().toPlainString());
                row.put("sourceQuantity", pair.getValue().sourceQuantity().toPlainString());
                if (pair.getValue().differenceQuantity() != null) {
                    row.put(
                            "sourceDifferenceQuantity",
                            pair.getValue().differenceQuantity().toPlainString());
                    row.put(
                            "totalCheck",
                            pair.getValue().differenceQuantity().signum() == 0
                                    ? "TOTAL_MATCH"
                                    : "TOTAL_MISMATCH");
                } else row.put("totalCheck", "BALANCE_MISSING");
            }
            if (left.balance().isPresent() && right.balance().isPresent()) {
                var delta = data.putObject("difference").put("direction", "LEFT_MINUS_RIGHT");
                delta.put(
                        "actualQuantity",
                        left.balance()
                                .get()
                                .actualQuantity()
                                .subtract(right.balance().get().actualQuantity())
                                .toPlainString());
                delta.put(
                        "availableQuantity",
                        left.balance()
                                .get()
                                .availableQuantity()
                                .subtract(right.balance().get().availableQuantity())
                                .toPlainString());
                delta.put(
                        "frozenQuantity",
                        left.balance()
                                .get()
                                .frozenQuantity()
                                .subtract(right.balance().get().frozenQuantity())
                                .toPlainString());
            }
            message = "同一快照比较两个仓库全部库位；差量为第一个仓库减第二个仓库。缺失余额不能视为零，不产生差量；冻结来源核对仅为范围总量。";
            sources.add(
                    new Source("查看库存", "/inventory/balances?skuId=" + sku.id(), "INVENTORY_READ"));
        }
        enrich(data);
        return result(name, status, message, data, List.of(), sources);
    }

    private static java.time.LocalDate date(JsonNode args, String key) {
        String value = text(args, key);
        if (value == null) throw new ToolStop("NEEDS_CLARIFICATION", "请提供明确的开始与结束日期");
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
            var date = java.time.LocalDate.parse(value);
            if (date.getYear() < 1000 || date.getYear() > 9998)
                throw new IllegalArgumentException();
            return date;
        } catch (RuntimeException e) {
            throw new ToolStop("INVALID_ARGUMENTS", "日期必须是有效的YYYY-MM-DD格式");
        }
    }

    private Evidence trace(String name, String ledgerNo) {
        require("INVENTORY_READ");
        var ledger =
                inventoryQueryService
                        .findLedger(ledgerNo)
                        .orElseThrow(() -> new ToolStop("NO_DATA", "未找到该流水"));
        String type = documentType(ledger.businessType());
        if (type == null)
            return result(
                    name,
                    "OK",
                    meaning(ledger.businessType()) + "；该类型没有可查的业务单据。",
                    json.createObjectNode()
                            .put("ledgerNo", ledger.ledgerNo())
                            .put("businessType", ledger.businessType().name()),
                    List.of(),
                    List.of());
        Evidence document = document(name, type, ledger.businessNo());
        ((ObjectNode) document.data())
                .put("ledgerNo", ledger.ledgerNo())
                .put("businessType", ledger.businessType().name())
                .put("meaning", meaning(ledger.businessType()));
        return document;
    }

    private Evidence document(String name, String type, String number) {
        if (type == null) throw new ToolStop("NEEDS_CLARIFICATION", "请指定采购、销售、调拨或盘点单据类型");
        String permission = permission(type);
        require(permission);
        Object value =
                switch (type) {
                    case "SALES" -> salesOutboundService.getByNumber(number);
                    case "PURCHASE" -> purchaseReceiptService.getByNumber(number);
                    case "TRANSFER" -> stockTransferService.getByNumber(number);
                    case "COUNT" -> inventoryCountService.getByNumber(number);
                    default -> throw new ToolStop("INVALID_ARGUMENTS", "不支持的单据类型");
                };
        ObjectNode data =
                project(
                        value,
                        "id",
                        "outboundNo",
                        "receiptNo",
                        "transferNo",
                        "countNo",
                        "warehouseId",
                        "sourceWarehouseId",
                        "targetWarehouseId",
                        "status",
                        "createdAt",
                        "updatedAt",
                        "reservedAt",
                        "approvedAt",
                        "completedAt",
                        "cancelledAt",
                        "adjustedAt");
        JsonNode tree = json.valueToTree(value);
        ArrayNode lines = data.putArray("lines");
        for (JsonNode line : tree.path("lines")) {
            if (lines.size() == 20) break;
            lines.add(
                    project(
                            line,
                            "lineNo",
                            "locationId",
                            "sourceLocationId",
                            "targetLocationId",
                            "skuId",
                            "quantity",
                            "snapshotActualQuantity",
                            "snapshotAvailableQuantity",
                            "snapshotFrozenQuantity",
                            "countedQuantity",
                            "differenceQuantity"));
        }
        data.put("lineTotal", tree.path("lines").size()).put("lineReturned", lines.size());
        data.put("documentType", type);
        enrich(data);
        return result(
                name,
                "OK",
                "精确单号查询；单据状态不能替代流水事实。明细最多显示二十行，更多请打开来源页面。",
                data,
                List.of(),
                List.of(documentSource(type, number)));
    }

    private ObjectNode pageData(PageResult<?> page, String... fields) {
        ObjectNode node =
                json.createObjectNode()
                        .put("page", page.page())
                        .put("size", page.size())
                        .put("total", page.total())
                        .put("returned", page.records().size())
                        .put("complete", false);
        ArrayNode records = node.putArray("records");
        page.records().forEach(row -> records.add(project(row, fields)));
        return node;
    }

    // A field whitelist excludes remarks, operator identities and security fields before model
    // exposure.
    private ObjectNode project(Object value, String... fields) {
        JsonNode tree = value instanceof JsonNode node ? node : json.valueToTree(value);
        ObjectNode result = json.createObjectNode();
        for (String field : fields)
            if (tree.has(field)) {
                JsonNode v = tree.get(field);
                result.set(
                        field,
                        field.endsWith("Quantity") || field.equals("quantity")
                                ? (v.isNull()
                                        ? v
                                        : TextNode.valueOf(
                                                new BigDecimal(v.asText()).toPlainString()))
                                : v);
            }
        return result;
    }

    private void enrich(JsonNode node) {
        if (!can("MASTER_DATA_READ")) return;
        Set<Long> skus = new LinkedHashSet<>(),
                warehouses = new LinkedHashSet<>(),
                locations = new LinkedHashSet<>();
        collectReferences(node, skus, warehouses, locations);
        require("MASTER_DATA_READ");
        ((ObjectNode) node)
                .set(
                        "references",
                        json.valueToTree(
                                masterDataReferenceQueryService.references(
                                        skus, warehouses, locations)));
    }

    private void collectReferences(
            JsonNode node, Set<Long> skus, Set<Long> warehouses, Set<Long> locations) {
        if (node.isArray()) {
            node.forEach(child -> collectReferences(child, skus, warehouses, locations));
            return;
        }
        if (!node.isObject()) return;
        if (node.path("id").isIntegralNumber()) {
            switch (node.path("kind").asText()) {
                case "sku" -> skus.add(node.path("id").asLong());
                case "warehouse" -> warehouses.add(node.path("id").asLong());
                case "location" -> locations.add(node.path("id").asLong());
                default -> {}
            }
        }
        node.fields()
                .forEachRemaining(
                        entry -> {
                            String field = entry.getKey();
                            JsonNode value = entry.getValue();
                            String kind =
                                    field.equals("skuId")
                                            ? "sku"
                                            : field.endsWith("WarehouseId")
                                                            || field.equals("warehouseId")
                                                    ? "warehouse"
                                                    : field.endsWith("LocationId")
                                                                    || field.equals("locationId")
                                                            ? "location"
                                                            : null;
                            if (kind != null && value.isIntegralNumber()) {
                                switch (kind) {
                                    case "sku" -> skus.add(value.asLong());
                                    case "warehouse" -> warehouses.add(value.asLong());
                                    default -> locations.add(value.asLong());
                                }
                            } else collectReferences(value, skus, warehouses, locations);
                        });
    }

    private static boolean available(String name) {
        return switch (name) {
            case "clarify" -> true;
            case "find_sku", "find_warehouse", "find_location" -> can("MASTER_DATA_READ");
            case "list_inventory",
                            "query_balances",
                            "query_ledgers",
                            "summarize_movements",
                            "summarize_documents" ->
                    can("MASTER_DATA_READ") && can("INVENTORY_READ");
            case "query_sales_orders" -> can("MASTER_DATA_READ") && can("SALES_OUTBOUND_READ");
            case "query_frozen_sources", "compare_inventory" ->
                    can("MASTER_DATA_READ")
                            && can("INVENTORY_READ")
                            && can("SALES_OUTBOUND_READ")
                            && can("TRANSFER_READ");
            case "trace_ledger" -> can("INVENTORY_READ");
            default ->
                    can("SALES_OUTBOUND_READ")
                            || can("PURCHASE_RECEIPT_READ")
                            || can("TRANSFER_READ")
                            || can("INVENTORY_COUNT_READ");
        };
    }

    private static String permission(String type) {
        return switch (type) {
            case "SALES" -> "SALES_OUTBOUND_READ";
            case "PURCHASE" -> "PURCHASE_RECEIPT_READ";
            case "TRANSFER" -> "TRANSFER_READ";
            case "COUNT" -> "INVENTORY_COUNT_READ";
            default -> throw new ToolStop("INVALID_ARGUMENTS", "不支持的单据类型");
        };
    }

    private static Source documentSource(String type, String number) {
        String path =
                switch (type) {
                    case "SALES" -> "/documents/sales-outbound";
                    case "PURCHASE" -> "/documents/purchase-receipts";
                    case "TRANSFER" -> "/documents/transfers";
                    default -> "/documents/inventory-counts";
                };
        return new Source(
                number,
                path
                        + "?businessNo="
                        + java.net.URLEncoder.encode(
                                number, java.nio.charset.StandardCharsets.UTF_8),
                permission(type));
    }

    public static String documentType(InventoryBusinessType type) {
        return switch (type) {
            case PURCHASE_RECEIPT -> "PURCHASE";
            case OUTBOUND_FREEZE, OUTBOUND_RELEASE, OUTBOUND_SHIP -> "SALES";
            case TRANSFER_FREEZE, TRANSFER_RELEASE, TRANSFER_OUT, TRANSFER_IN -> "TRANSFER";
            case INVENTORY_COUNT -> "COUNT";
            default -> null;
        };
    }

    public static String meaning(InventoryBusinessType type) {
        return switch (type) {
            case PURCHASE_RECEIPT -> "采购入库，实际和可用库存增加";
            case OUTBOUND_FREEZE -> "销售冻结，可用转为冻结，实际库存不变";
            case OUTBOUND_RELEASE -> "销售取消释放，冻结转为可用，实际库存不变";
            case OUTBOUND_SHIP -> "销售实际出库，实际和冻结库存减少";
            case TRANSFER_FREEZE -> "调拨冻结，可用转为冻结，实际库存不变";
            case TRANSFER_RELEASE -> "调拨取消释放，冻结转为可用，实际库存不变";
            case TRANSFER_OUT -> "调拨调出，源仓实际和冻结减少，形成独立在途库存";
            case TRANSFER_IN -> "调拨调入，目标仓实际和可用增加，清理在途库存";
            case INVENTORY_COUNT -> "盘点调整，差异同量改变实际和可用，冻结不变";
            case INVENTORY_GAIN -> "盘盈，实际和可用库存增加";
            case INVENTORY_LOSS -> "盘亏，实际和可用库存减少";
            case INITIALIZE -> "库存初始化";
        };
    }

    private static boolean can(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> permission.equals(a.getAuthority()));
    }

    private static void require(String permission) {
        if (!can(permission)) throw new ToolStop("FORBIDDEN", "缺少业务读取权限：" + permission);
    }

    private static String text(JsonNode args, String field) {
        return args.has(field) ? args.get(field).asText().trim() : null;
    }

    private static String number(JsonNode args, String field) {
        String value = text(args, field);
        if (value == null) throw new ToolStop("NEEDS_CLARIFICATION", "请提供准确单号或流水号");
        if (!value.matches("[A-Za-z0-9_-]{2,64}"))
            throw new ToolStop("INVALID_ARGUMENTS", "单号格式不合法");
        return value;
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case "sku" -> "商品";
            case "warehouse" -> "仓库";
            default -> "库位";
        };
    }

    private static Evidence result(
            String tool,
            String status,
            String message,
            JsonNode data,
            List<Candidate> candidates,
            List<Source> sources) {
        return new Evidence(tool, status, message, data, candidates, sources, Instant.now());
    }

    private static class ToolStop extends RuntimeException {
        private final String status;
        private final List<Candidate> candidates;

        ToolStop(String status, String message) {
            this(status, message, List.of());
        }

        ToolStop(String status, String message, List<Candidate> candidates) {
            super(message);
            this.status = status;
            this.candidates = candidates;
        }
    }
}
