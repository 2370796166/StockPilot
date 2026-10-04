package com.stockpilot.inventory.vo;

import com.stockpilot.shared.api.PageResult;
import java.math.BigDecimal;

/** Total covers all active matching lines; source details are independently paginated. */
public record InventoryFrozenSourcePageVO(
        BigDecimal totalQuantity, PageResult<InventoryFrozenSourceVO> sources) {}
