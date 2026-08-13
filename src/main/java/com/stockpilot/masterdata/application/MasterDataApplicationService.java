package com.stockpilot.masterdata.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.masterdata.api.MasterDataErrorCode;
import com.stockpilot.masterdata.domain.BaseMasterDataEntity;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.request.CreateMasterDataRequest;
import com.stockpilot.masterdata.request.PageQuery;
import com.stockpilot.masterdata.request.UpdateMasterDataRequest;
import com.stockpilot.masterdata.vo.MasterDataVO;
import com.stockpilot.masterdata.vo.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.function.Supplier;

public class MasterDataApplicationService<T extends BaseMasterDataEntity> {
    private final BaseMapper<T> mapper;
    private final Supplier<T> entityFactory;
    private final String duplicateMessage;

    public MasterDataApplicationService(BaseMapper<T> mapper, Supplier<T> entityFactory, String duplicateMessage) {
        this.mapper = mapper;
        this.entityFactory = entityFactory;
        this.duplicateMessage = duplicateMessage;
    }

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

    public MasterDataVO changeStatus(long id, ChangeStatusRequest request) {
        T entity = require(id);
        entity.setStatus(request.status());
        entity.setVersion(request.version());
        if (mapper.updateById(entity) != 1) {
            throw new BusinessException(MasterDataErrorCode.CONCURRENT_MODIFICATION);
        }
        return toVO(require(id));
    }

    public MasterDataVO detail(long id) { return toVO(require(id)); }

    public PageResult<MasterDataVO> page(PageQuery query) {
        QueryWrapper<T> wrapper = new QueryWrapper<T>()
                .like(StringUtils.hasText(query.getCode()), "code", trimToNull(query.getCode()))
                .like(StringUtils.hasText(query.getName()), "name", trimToNull(query.getName()))
                .eq(query.getStatus() != null, "status", query.getStatus())
                .orderByDesc("id");
        Page<T> result = mapper.selectPage(Page.of(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    protected T require(long id) {
        T entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException(MasterDataErrorCode.NOT_FOUND);
        return entity;
    }

    protected MasterDataVO toVO(T e) {
        return new MasterDataVO(e.getId(), e.getCode(), e.getName(), e.getStatus(), e.getRemark(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }

    protected static String normalizeCode(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    protected static String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
