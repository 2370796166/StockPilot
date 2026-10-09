package com.stockpilot.sales.vo;

import java.math.BigDecimal;

public record SalesInventoryOrderVO(String businessNo, String status, BigDecimal quantity) {}
