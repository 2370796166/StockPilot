package com.stockpilot.security.config;

import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.stockpilot.shared.auth.WarehouseAccess;
import java.sql.Connection;
import java.sql.SQLException;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

@Component
public class WarehouseScopeInterceptor extends DataPermissionInterceptor {
    public WarehouseScopeInterceptor(WarehouseDataPermissionHandler handler) {
        super(handler);
    }

    @Override
    public void beforeQuery(
            Executor executor,
            MappedStatement ms,
            Object parameter,
            RowBounds bounds,
            ResultHandler handler,
            BoundSql sql)
            throws SQLException {
        if (!WarehouseAccess.current().allWarehouses())
            super.beforeQuery(executor, ms, parameter, bounds, handler, sql);
    }

    @Override
    public void beforePrepare(StatementHandler handler, Connection connection, Integer timeout) {
        if (!WarehouseAccess.current().allWarehouses())
            super.beforePrepare(handler, connection, timeout);
    }
}
