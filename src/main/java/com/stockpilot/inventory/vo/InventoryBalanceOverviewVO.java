package com.stockpilot.inventory.vo;

import com.stockpilot.shared.api.PageResult;

public record InventoryBalanceOverviewVO(
        PageResult<InventoryWarehouseBalanceVO> warehouses,
        PageResult<InventoryBalanceVO> locations) {}
