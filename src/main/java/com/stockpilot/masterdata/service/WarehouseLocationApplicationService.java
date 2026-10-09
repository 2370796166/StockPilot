package com.stockpilot.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.masterdata.api.MasterDataErrorCode;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.domain.WarehouseEntity;
import com.stockpilot.masterdata.domain.WarehouseLocationEntity;
import com.stockpilot.masterdata.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.mapper.WarehouseMapper;
import com.stockpilot.masterdata.request.*;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.vo.LocationVO;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.exception.BusinessException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// 库位服务维护“仓库 + 库位编码”维度，并确保库位只能归属于存在且启用的仓库。
@Service
public class WarehouseLocationApplicationService {
    private final WarehouseLocationMapper warehouseLocationMapper;
    private final WarehouseMapper warehouseMapper;

    public WarehouseLocationApplicationService(
            WarehouseLocationMapper warehouseLocationMapper, WarehouseMapper warehouseMapper) {
        this.warehouseLocationMapper = warehouseLocationMapper;
        this.warehouseMapper = warehouseMapper;
    }

    // 创建库位：先校验所属仓库，再由数据库联合唯一约束阻止同一仓库内的重复库位编码。
    public LocationVO create(CreateLocationRequest r) {
        requireEnabledWarehouse(r.warehouseId());
        WarehouseLocationEntity e = new WarehouseLocationEntity();
        e.setWarehouseId(r.warehouseId());
        e.setCode(r.code().trim().toUpperCase(Locale.ROOT));
        e.setName(r.name().trim());
        e.setRemark(trim(r.remark()));
        e.setStatus(MasterDataStatus.ENABLED);
        try {
            warehouseLocationMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(MasterDataErrorCode.DUPLICATE_CODE, "该仓库中的库位编码已存在");
        }
        return toVO(require(e.getId()));
    }

    // 更新库位：允许调整所属仓库和显示信息，但编码保持稳定，并使用乐观锁检测并发修改。
    public LocationVO update(long id, UpdateLocationRequest r) {
        requireEnabledWarehouse(r.warehouseId());
        WarehouseLocationEntity e = require(id);
        e.setWarehouseId(r.warehouseId());
        e.setName(r.name().trim());
        e.setRemark(trim(r.remark()));
        e.setVersion(r.version());
        try {
            if (warehouseLocationMapper.updateById(e) != 1)
                throw new BusinessException(MasterDataErrorCode.CONCURRENT_MODIFICATION);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(MasterDataErrorCode.DUPLICATE_CODE, "该仓库中的库位编码已存在");
        }
        return toVO(require(id));
    }

    // 启停库位而不物理删除；停用库位不能用于创建新的库存业务。
    public LocationVO changeStatus(long id, ChangeStatusRequest r) {
        WarehouseLocationEntity e = require(id);
        e.setStatus(r.status());
        e.setVersion(r.version());
        if (warehouseLocationMapper.updateById(e) != 1)
            throw new BusinessException(MasterDataErrorCode.CONCURRENT_MODIFICATION);
        return toVO(require(id));
    }

    // 查询库位详情，并补充其所属仓库编码供前端展示。
    public LocationVO detail(long id) {
        return toVO(require(id));
    }

    // 分页查询库位，支持仓库、编码、名称、组合关键字和状态筛选。
    public PageResult<LocationVO> page(LocationPageQuery q) {
        LambdaQueryWrapper<WarehouseLocationEntity> w =
                new LambdaQueryWrapper<WarehouseLocationEntity>()
                        .eq(
                                q.getWarehouseId() != null,
                                WarehouseLocationEntity::getWarehouseId,
                                q.getWarehouseId())
                        .like(
                                StringUtils.hasText(q.getCode()),
                                WarehouseLocationEntity::getCode,
                                trim(q.getCode()))
                        .like(
                                StringUtils.hasText(q.getName()),
                                WarehouseLocationEntity::getName,
                                trim(q.getName()))
                        .and(
                                StringUtils.hasText(q.getKeyword()),
                                nested ->
                                        nested.like(
                                                        WarehouseLocationEntity::getCode,
                                                        trim(q.getKeyword()))
                                                .or()
                                                .like(
                                                        WarehouseLocationEntity::getName,
                                                        trim(q.getKeyword())))
                        .eq(
                                q.getStatus() != null,
                                WarehouseLocationEntity::getStatus,
                                q.getStatus())
                        .orderByDesc(WarehouseLocationEntity::getId);
        Page<WarehouseLocationEntity> p =
                warehouseLocationMapper.selectPage(Page.of(q.getPage(), q.getSize()), w);
        var warehouseIds =
                p.getRecords().stream()
                        .map(WarehouseLocationEntity::getWarehouseId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        Map<Long, WarehouseEntity> warehouses =
                warehouseIds.isEmpty()
                        ? Map.of()
                        : warehouseMapper.selectBatchIds(warehouseIds).stream()
                                .collect(Collectors.toMap(WarehouseEntity::getId, value -> value));
        return new PageResult<>(
                p.getRecords().stream()
                        .map(value -> toVO(value, warehouses.get(value.getWarehouseId())))
                        .toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize());
    }

    private WarehouseEntity requireEnabledWarehouse(long id) {
        WarehouseEntity w = warehouseMapper.selectById(id);
        if (w == null) throw new BusinessException(MasterDataErrorCode.WAREHOUSE_NOT_FOUND);
        if (w.getStatus() != MasterDataStatus.ENABLED)
            throw new BusinessException(MasterDataErrorCode.WAREHOUSE_DISABLED);
        return w;
    }

    private WarehouseLocationEntity require(long id) {
        WarehouseLocationEntity e = warehouseLocationMapper.selectById(id);
        if (e == null) throw new BusinessException(MasterDataErrorCode.NOT_FOUND);
        return e;
    }

    private LocationVO toVO(WarehouseLocationEntity e) {
        return toVO(e, warehouseMapper.selectById(e.getWarehouseId()));
    }

    private LocationVO toVO(WarehouseLocationEntity e, WarehouseEntity w) {
        return new LocationVO(
                e.getId(),
                e.getWarehouseId(),
                w == null ? null : w.getCode(),
                e.getCode(),
                e.getName(),
                e.getStatus(),
                e.getRemark(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion());
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
