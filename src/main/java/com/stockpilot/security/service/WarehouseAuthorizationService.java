package com.stockpilot.security.service;

import com.stockpilot.masterdata.service.WarehouseApplicationService;
import com.stockpilot.security.audit.AuditService;
import com.stockpilot.security.mapper.UserMapper;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.vo.SecurityVO;
import com.stockpilot.shared.api.CommonErrorCode;
import com.stockpilot.shared.auth.WarehouseAccess;
import com.stockpilot.shared.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseAuthorizationService {
    private final UserMapper userMapper;
    private final WarehouseApplicationService warehouseService;
    private final AuditService auditService;

    public WarehouseAuthorizationService(
            UserMapper userMapper,
            WarehouseApplicationService warehouseService,
            AuditService auditService) {
        this.userMapper = userMapper;
        this.warehouseService = warehouseService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public SecurityVO.WarehouseScope get(long id) {
        WarehouseAccess.requireAll();
        Integer version = userMapper.warehouseScopeVersion(id);
        if (version == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        return new SecurityVO.WarehouseScope(
                userMapper.allWarehouses(id), userMapper.warehouseIds(id), version);
    }

    @Transactional
    public SecurityVO.WarehouseScope update(long id, SecurityRequests.WarehouseScope request) {
        WarehouseAccess.requireAll();
        if (userMapper.selectById(id) == null)
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        var authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (!request.allWarehouses()
                && authentication != null
                && authentication.getPrincipal()
                        instanceof com.stockpilot.shared.auth.AuthenticatedActor actor
                && actor.userId().equals(id))
            throw new BusinessException(
                    CommonErrorCode.BUSINESS_CONFLICT, "不能限制当前登录管理员自己的仓库范围，请由另一名授权管理员处理");
        if (request.allWarehouses() && !request.warehouseIds().isEmpty())
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "全部仓库模式不能同时指定仓库列表");
        List<Long> ids = request.warehouseIds().stream().distinct().sorted().toList();
        ids.forEach(warehouseService::detail);
        if (userMapper.updateWarehouseScope(id, request.allWarehouses(), request.version()) != 1)
            throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT, "数据权限已变化，请刷新后重试");
        userMapper.deleteWarehouseScope(id);
        if (!ids.isEmpty() && userMapper.insertWarehouseScope(id, ids) != ids.size())
            throw new IllegalStateException("Cannot persist warehouse authorization");
        auditService.record(
                "ASSIGN_USER_WAREHOUSES",
                "USER",
                id,
                "SUCCESS",
                "allWarehouses=" + request.allWarehouses() + ",warehouseCount=" + ids.size());
        return get(id);
    }
}
