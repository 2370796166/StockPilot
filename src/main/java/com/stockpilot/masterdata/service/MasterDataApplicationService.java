package com.stockpilot.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.masterdata.api.MasterDataErrorCode;
import com.stockpilot.masterdata.domain.BaseMasterDataEntity;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.request.CreateMasterDataRequest;
import com.stockpilot.masterdata.request.PageQuery;
import com.stockpilot.masterdata.request.UpdateMasterDataRequest;
import com.stockpilot.masterdata.vo.MasterDataVO;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.shared.exception.BusinessException;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

// 商品分类、供应商等标准基础资料共用的应用服务，统一实现编码规范、状态管理和乐观锁更新。
public class MasterDataApplicationService<T extends BaseMasterDataEntity> {
    private final BaseMapper<T> mapper;
    private final Supplier<T> entityFactory;
    private final String duplicateMessage;

    public MasterDataApplicationService(
            BaseMapper<T> mapper, Supplier<T> entityFactory, String duplicateMessage) {
        this.mapper = mapper;
        this.entityFactory = entityFactory;
        this.duplicateMessage = duplicateMessage;
    }

    // 创建基础资料：编码统一去空格并转为大写，初始状态为 ENABLED，重复编码转为明确业务错误。
    public MasterDataVO create(CreateMasterDataRequest request) {
        T entity = entityFactory.get();
        entity.setCode(normalizeCode(request.code()));
        entity.setName(request.name().trim());
        entity.setRemark(trimToNull(request.remark()));
        entity.setStatus(MasterDataStatus.ENABLED);
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(MasterDataErrorCode.DUPLICATE_CODE, duplicateMessage);
        }
        return toVO(require(entity.getId()));
    }

    // 更新名称和备注：业务编码创建后保持不变，并通过客户端提交的版本检测并发覆盖。
    public MasterDataVO update(long id, UpdateMasterDataRequest request) {
        T entity = require(id);
        entity.setName(request.name().trim());
        entity.setRemark(trimToNull(request.remark()));
        entity.setVersion(request.version());
        if (mapper.updateById(entity) != 1) {
            throw new BusinessException(MasterDataErrorCode.CONCURRENT_MODIFICATION);
        }
        return toVO(require(id));
    }

    // 启用或停用基础资料，不执行物理删除；历史单据仍可保留对停用数据的引用。
    public MasterDataVO changeStatus(long id, ChangeStatusRequest request) {
        T entity = require(id);
        entity.setStatus(request.status());
        entity.setVersion(request.version());
        if (mapper.updateById(entity) != 1) {
            throw new BusinessException(MasterDataErrorCode.CONCURRENT_MODIFICATION);
        }
        return toVO(require(id));
    }

    // 按主键查询基础资料详情，不存在时统一返回业务层 NOT_FOUND。
    public MasterDataVO detail(long id) {
        return toVO(require(id));
    }

    // 分页查询基础资料，支持编码、名称、组合关键字和启停状态筛选，并按新记录优先返回。
    public PageResult<MasterDataVO> page(PageQuery query) {
        QueryWrapper<T> wrapper =
                new QueryWrapper<T>()
                        .like(
                                StringUtils.hasText(query.getCode()),
                                "code",
                                trimToNull(query.getCode()))
                        .like(
                                StringUtils.hasText(query.getName()),
                                "name",
                                trimToNull(query.getName()))
                        .and(
                                StringUtils.hasText(query.getKeyword()),
                                nested ->
                                        nested.like("code", trimToNull(query.getKeyword()))
                                                .or()
                                                .like("name", trimToNull(query.getKeyword())))
                        .eq(query.getStatus() != null, "status", query.getStatus())
                        .orderByDesc("id");
        Page<T> result = mapper.selectPage(Page.of(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    protected T require(long id) {
        T entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException(MasterDataErrorCode.NOT_FOUND);
        return entity;
    }

    protected MasterDataVO toVO(T e) {
        return new MasterDataVO(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getStatus(),
                e.getRemark(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion());
    }

    protected static String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    protected static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
