package com.stockpilot.masterdata.vo;

/**
 * Minimal display fields shared by read-only queries; excludes remarks and operator information.
 */
public record ReferenceDataVO(Long id, String code, String name, String unit, Long warehouseId) {}
