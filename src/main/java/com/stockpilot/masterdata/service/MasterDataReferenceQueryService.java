package com.stockpilot.masterdata.service;

import com.stockpilot.masterdata.mapper.SkuMapper;
import com.stockpilot.masterdata.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.mapper.WarehouseMapper;
import com.stockpilot.masterdata.vo.ReferenceDataVO;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterDataReferenceQueryService {
    private final SkuMapper skuMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseLocationMapper warehouseLocationMapper;

    public MasterDataReferenceQueryService(
            SkuMapper skuMapper,
            WarehouseMapper warehouseMapper,
            WarehouseLocationMapper warehouseLocationMapper) {
        this.skuMapper = skuMapper;
        this.warehouseMapper = warehouseMapper;
        this.warehouseLocationMapper = warehouseLocationMapper;
    }

    @Transactional(readOnly = true, timeout = 10)
    public Optional<ReferenceDataVO> exactCode(String kind, String code, Long warehouseId) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{1,64}")) return Optional.empty();
        String normalized = code.toUpperCase(Locale.ROOT);
        return switch (kind) {
            case "sku" -> {
                var v =
                        skuMapper.selectOne(
                                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<
                                                com.stockpilot.masterdata.domain.SkuEntity>()
                                        .eq("code", normalized));
                yield v == null
                        ? Optional.empty()
                        : Optional.of(
                                new ReferenceDataVO(
                                        v.getId(), v.getCode(), v.getName(), v.getUnit(), null));
            }
            case "warehouse" -> {
                var v =
                        warehouseMapper.selectOne(
                                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<
                                                com.stockpilot.masterdata.domain.WarehouseEntity>()
                                        .eq("code", normalized));
                yield v == null
                        ? Optional.empty()
                        : Optional.of(
                                new ReferenceDataVO(
                                        v.getId(), v.getCode(), v.getName(), null, null));
            }
            case "location" -> {
                if (warehouseId == null || warehouseId <= 0)
                    throw new IllegalArgumentException("Missing warehouse");
                var v =
                        warehouseLocationMapper.selectOne(
                                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<
                                                com.stockpilot.masterdata.domain
                                                        .WarehouseLocationEntity>()
                                        .eq("code", normalized)
                                        .eq("warehouse_id", warehouseId));
                yield v == null
                        ? Optional.empty()
                        : Optional.of(
                                new ReferenceDataVO(
                                        v.getId(),
                                        v.getCode(),
                                        v.getName(),
                                        null,
                                        v.getWarehouseId()));
            }
            default -> throw new IllegalArgumentException("Invalid reference kind");
        };
    }

    @Transactional(readOnly = true, timeout = 10)
    public Map<String, ReferenceDataVO> references(
            Set<Long> skuIds, Set<Long> warehouseIds, Set<Long> locationIds) {
        validate(skuIds);
        validate(warehouseIds);
        validate(locationIds);
        Map<String, ReferenceDataVO> values = new LinkedHashMap<>();
        if (!skuIds.isEmpty())
            skuMapper
                    .selectBatchIds(skuIds)
                    .forEach(
                            v ->
                                    values.put(
                                            "sku:" + v.getId(),
                                            new ReferenceDataVO(
                                                    v.getId(),
                                                    v.getCode(),
                                                    v.getName(),
                                                    v.getUnit(),
                                                    null)));
        if (!warehouseIds.isEmpty())
            warehouseMapper
                    .selectBatchIds(warehouseIds)
                    .forEach(
                            v ->
                                    values.put(
                                            "warehouse:" + v.getId(),
                                            new ReferenceDataVO(
                                                    v.getId(),
                                                    v.getCode(),
                                                    v.getName(),
                                                    null,
                                                    null)));
        if (!locationIds.isEmpty())
            warehouseLocationMapper
                    .selectBatchIds(locationIds)
                    .forEach(
                            v ->
                                    values.put(
                                            "location:" + v.getId(),
                                            new ReferenceDataVO(
                                                    v.getId(),
                                                    v.getCode(),
                                                    v.getName(),
                                                    null,
                                                    v.getWarehouseId())));
        return Map.copyOf(values);
    }

    private static void validate(Set<Long> ids) {
        if (ids == null || ids.size() > 128 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("Invalid reference query scope");
    }
}
