package com.stockpilot.shared.auth;

import com.stockpilot.shared.api.AccessErrorCode;
import com.stockpilot.shared.exception.BusinessException;

/** Bound only for authenticated HTTP/AI work; internal transaction workers have no user scope. */
public final class WarehouseAccess {
    private static final ThreadLocal<WarehouseScope> CURRENT = new ThreadLocal<>();

    private WarehouseAccess() {}

    public static void bind(WarehouseScope scope) {
        CURRENT.set(scope);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static WarehouseScope current() {
        var scope = CURRENT.get();
        return scope == null ? WarehouseScope.unrestricted() : scope;
    }

    public static void require(long warehouseId) {
        if (!current().permits(warehouseId)) throw new BusinessException(AccessErrorCode.FORBIDDEN);
    }

    public static void requireAll() {
        if (!current().allWarehouses()) throw new BusinessException(AccessErrorCode.FORBIDDEN);
    }
}
