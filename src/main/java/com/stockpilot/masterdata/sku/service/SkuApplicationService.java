package com.stockpilot.masterdata.sku.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.masterdata.api.MasterDataErrorCode;
import com.stockpilot.masterdata.category.domain.ProductCategoryEntity;
import com.stockpilot.masterdata.category.mapper.ProductCategoryMapper;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.infrastructure.cache.*;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.sku.domain.SkuEntity;
import com.stockpilot.masterdata.sku.mapper.SkuMapper;
import com.stockpilot.masterdata.sku.request.*;
import com.stockpilot.masterdata.sku.vo.SkuVO;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.shared.exception.BusinessException;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// SKU 基础资料服务：维护分类归属和计量单位，并对单键详情应用可降级的 Cache Aside 缓存。
@Service
public class SkuApplicationService {
    private final SkuMapper mapper;
    private final ProductCategoryMapper categoryMapper;
    private final ReferenceDataCache cache;

    public SkuApplicationService(SkuMapper m, ProductCategoryMapper c, ReferenceDataCache cache) {
        mapper = m;
        categoryMapper = c;
        this.cache = cache;
    }

    // 创建 SKU：分类存在时必须处于启用状态，SKU 编码规范化后由数据库保证唯一。
    public SkuVO create(CreateSkuRequest r) {
        requireEnabledCategory(r.categoryId());
        SkuEntity e = new SkuEntity();
        e.setCode(r.code().trim().toUpperCase(Locale.ROOT));
        e.setName(r.name().trim());
        e.setCategoryId(r.categoryId());
        e.setUnit(r.unit().trim());
        e.setRemark(trim(r.remark()));
        e.setStatus(MasterDataStatus.ENABLED);
        try {
            mapper.insert(e);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(MasterDataErrorCode.DUPLICATE_CODE, "SKU编码已存在");
        }
        cache.evict(ReferenceCacheKind.SKU, e.getId());
        return toVO(require(e.getId()));
    }

    // 更新 SKU：编码保持不变，版本冲突时拒绝覆盖；数据库成功后删除详情缓存。
    public SkuVO update(long id, UpdateSkuRequest r) {
        requireEnabledCategory(r.categoryId());
        SkuEntity e = require(id);
        e.setName(r.name().trim());
        e.setCategoryId(r.categoryId());
        e.setUnit(r.unit().trim());
        e.setRemark(trim(r.remark()));
        e.setVersion(r.version());
        if (mapper.updateById(e) != 1)
            throw new BusinessException(MasterDataErrorCode.CONCURRENT_MODIFICATION);
        cache.evict(ReferenceCacheKind.SKU, id);
        return toVO(require(id));
    }

    // 启停 SKU：保留历史记录；停用后新业务不能使用，但已冻结业务仍可继续完成或释放。
    public SkuVO changeStatus(long id, ChangeStatusRequest r) {
        SkuEntity e = require(id);
        e.setStatus(r.status());
        e.setVersion(r.version());
        if (mapper.updateById(e) != 1)
            throw new BusinessException(MasterDataErrorCode.CONCURRENT_MODIFICATION);
        cache.evict(ReferenceCacheKind.SKU, id);
        return toVO(require(id));
    }

    // 查询 SKU 详情：优先读取 Redis，未命中时回查 MySQL 并缓存；不存在结果使用短 TTL 防穿透。
    // 分类名称不存入 SKU 缓存，每次从 MySQL 补齐，避免分类改名后派生名称长期陈旧。
    public SkuVO detail(long id) {
        ReferenceCacheLookup<SkuVO> cached = cache.get(ReferenceCacheKind.SKU, id, SkuVO.class);
        if (cached.hit()) {
            if (!cached.found()) throw new BusinessException(MasterDataErrorCode.NOT_FOUND);
            return withCurrentCategoryName(cached.value());
        }
        SkuEntity entity = mapper.selectById(id);
        if (entity == null) {
            cache.putMissing(ReferenceCacheKind.SKU, id);
            throw new BusinessException(MasterDataErrorCode.NOT_FOUND);
        }
        SkuVO value = toVO(entity);
        cache.put(ReferenceCacheKind.SKU, id, withoutCategoryName(value));
        return value;
    }

    // 分页查询 SKU：支持分类、编码、名称、组合关键字和状态筛选；分页列表直接以 MySQL 为准。
    public PageResult<SkuVO> page(SkuPageQuery q) {
        LambdaQueryWrapper<SkuEntity> w =
                new LambdaQueryWrapper<SkuEntity>()
                        .eq(q.getCategoryId() != null, SkuEntity::getCategoryId, q.getCategoryId())
                        .like(
                                StringUtils.hasText(q.getCode()),
                                SkuEntity::getCode,
                                trim(q.getCode()))
                        .like(
                                StringUtils.hasText(q.getName()),
                                SkuEntity::getName,
                                trim(q.getName()))
                        .and(
                                StringUtils.hasText(q.getKeyword()),
                                nested ->
                                        nested.like(SkuEntity::getCode, trim(q.getKeyword()))
                                                .or()
                                                .like(SkuEntity::getName, trim(q.getKeyword())))
                        .eq(q.getStatus() != null, SkuEntity::getStatus, q.getStatus())
                        .orderByDesc(SkuEntity::getId);
        Page<SkuEntity> p = mapper.selectPage(Page.of(q.getPage(), q.getSize()), w);
        return new PageResult<>(
                p.getRecords().stream().map(this::toVO).toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize());
    }

    private void requireEnabledCategory(Long id) {
        if (id == null) return;
        ProductCategoryEntity c = categoryMapper.selectById(id);
        if (c == null) throw new BusinessException(MasterDataErrorCode.CATEGORY_NOT_FOUND);
        if (c.getStatus() != MasterDataStatus.ENABLED)
            throw new BusinessException(MasterDataErrorCode.CATEGORY_DISABLED);
    }

    private SkuEntity require(long id) {
        SkuEntity e = mapper.selectById(id);
        if (e == null) throw new BusinessException(MasterDataErrorCode.NOT_FOUND);
        return e;
    }

    private SkuVO toVO(SkuEntity e) {
        ProductCategoryEntity c =
                e.getCategoryId() == null ? null : categoryMapper.selectById(e.getCategoryId());
        return new SkuVO(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getCategoryId(),
                c == null ? null : c.getName(),
                e.getUnit(),
                e.getStatus(),
                e.getRemark(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion());
    }

    private SkuVO withCurrentCategoryName(SkuVO value) {
        ProductCategoryEntity c =
                value.categoryId() == null ? null : categoryMapper.selectById(value.categoryId());
        return new SkuVO(
                value.id(),
                value.code(),
                value.name(),
                value.categoryId(),
                c == null ? null : c.getName(),
                value.unit(),
                value.status(),
                value.remark(),
                value.createdAt(),
                value.updatedAt(),
                value.version());
    }

    private SkuVO withoutCategoryName(SkuVO value) {
        return new SkuVO(
                value.id(),
                value.code(),
                value.name(),
                value.categoryId(),
                null,
                value.unit(),
                value.status(),
                value.remark(),
                value.createdAt(),
                value.updatedAt(),
                value.version());
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
