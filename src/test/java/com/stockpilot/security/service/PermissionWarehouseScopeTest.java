package com.stockpilot.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.stockpilot.security.audit.AuditService;
import com.stockpilot.security.domain.SecurityStatus;
import com.stockpilot.security.mapper.PermissionMapper;
import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.shared.auth.WarehouseAccess;
import com.stockpilot.shared.auth.WarehouseScope;
import com.stockpilot.shared.exception.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionWarehouseScopeTest {
    @Test
    void everyGlobalPermissionWriteRejectsLimitedScopeBeforeDataAccess() {
        var mapper = mock(PermissionMapper.class);
        var audit = mock(AuditService.class);
        var service = new PermissionManagementApplicationService(mapper, audit);
        WarehouseAccess.bind(new WarehouseScope(false, Set.of(3L)));
        try {
            for (Runnable action :
                    java.util.List.<Runnable>of(
                            () ->
                                    service.createPermission(
                                            new SecurityRequests.CreatePermission(
                                                    "TEST_PERM", "测试", null)),
                            () ->
                                    service.updatePermission(
                                            1,
                                            new SecurityRequests.UpdatePermission("测试", null, 0)),
                            () ->
                                    service.changeStatus(
                                            1,
                                            new SecurityRequests.Status(
                                                    SecurityStatus.DISABLED, 0)))) {
                var error = assertThrows(BusinessException.class, action::run);
                assertEquals(403, error.getErrorCode().httpStatus().value());
            }
            verifyNoInteractions(mapper, audit);
        } finally {
            WarehouseAccess.clear();
        }
    }
}
