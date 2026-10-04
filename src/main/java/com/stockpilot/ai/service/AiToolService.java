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
            Map.of(
                    "find_sku",
                    List.of("keyword"),
                    "find_warehouse",
                    List.of("keyword"),
                    "find_location",
                    List.of("keyword", "warehouse"),
                    "query_balances",
                    List.of("sku", "warehouse", "location", "page", "size"),
                    "query_ledgers",
                    List.of("sku", "warehouse", "location", "businessNo", "page", "size"),
                    "get_document",
                    List.of("documentType", "number"),
                    "trace_ledger",
                    List.of("ledgerNo"),
                    "summarize_movements",
                    List.of("sku", "warehouse", "location", "startDate", "endDate"),
                    "query_frozen_sources",
                    List.of("sku", "warehouse", "location", "page", "size"));
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
            AiFrozenInventoryService aiFrozenInventoryService) {
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
    }

    public ArrayNode definitions() {
        ArrayNode tools = json.createArrayNode();
        FIELDS.forEach(
                (name, fields) -> {
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
                            prop.put(
                                    "description",
                                    switch (field) {
                                        case "documentType" -> "PURCHASE, SALES, TRANSFER or COUNT";
                                        case "startDate", "endDate" ->
                                                "YYYY-MM-DD，包含起止日期；Asia/Shanghai时区，最多九十二个自然日";
                                        case "sku", "warehouse", "location", "keyword" ->
                                                "用户提供的编码或名称。不得编造ID。";
                                        default -> "用户提供的准确单号";
                                    });
                        }
                    }
                    List<String> required =
                            switch (name) {
                                case "query_balances" -> List.of("sku");
                                case "query_ledgers" -> List.of("sku", "warehouse");
                                case "summarize_movements" ->
                                        List.of("sku", "warehouse", "startDate", "endDate");
                                case "query_frozen_sources" -> List.of("sku", "warehouse");
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
                                        case "summarize_movements" ->
                                                "指定商品、仓库、可选库位及日期区间的完整流水差量汇总；按业务动作区分销售、调拨、盘点。不是期初期末余额，不证明完整历史。";
                                        case "query_frozen_sources" ->
                                                "查询当前有效销售/调拨冻结来源及全范围总量；两类明细各自分页。核对当前余额冻结总量，不证明逐库位一致。";
                                        default ->
                                                name
                                                        + "：只读查询；名称歧义必须返回候选并停止。query_balances汇总覆盖全部匹配库位，明细分页。query_ledgers为一页流水，不能推断完整历史。";
                                    })
                            .set("parameters", params);
                });
        return tools;
    }

    public Evidence execute(String name, JsonNode args, List<Selection> selections) {
        try {
            validate(name, args);
            return switch (name) {
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
                    yield result(
                            name,
                            "OK",
                            "已找到唯一资料",
                            json.valueToTree(candidate),
                            List.of(),
                            List.of());
                }
                case "query_balances", "query_ledgers" -> inventory(name, args, selections);
                case "summarize_movements", "query_frozen_sources" ->
                        analysis(name, args, selections);
                case "get_document" ->
                        document(name, text(args, "documentType"), number(args, "number"));
                case "trace_ledger" -> trace(name, number(args, "ledgerNo"));
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
        Map<String, JsonNode> references = new LinkedHashMap<>();
        collectReferences(node, references);
        ((ObjectNode) node).set("references", json.valueToTree(references));
    }

    private void collectReferences(JsonNode node, Map<String, JsonNode> references) {
        if (node.isArray()) {
            node.forEach(child -> collectReferences(child, references));
            return;
        }
        if (!node.isObject()) return;
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
                                String key = kind + ":" + value.asLong();
                                if (!references.containsKey(key)) {
                                    require("MASTER_DATA_READ");
                                    Object detail =
                                            switch (kind) {
                                                case "sku" -> skuService.detail(value.asLong());
                                                case "warehouse" ->
                                                        warehouseService.detail(value.asLong());
                                                default -> locationService.detail(value.asLong());
                                            };
                                    references.put(
                                            key,
                                            project(
                                                    detail,
                                                    "id",
                                                    "code",
                                                    "name",
                                                    "unit",
                                                    "warehouseId"));
                                }
                            } else collectReferences(value, references);
                        });
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
