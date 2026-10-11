package com.stockpilot.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockpilot.security.domain.UserEntity;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface UserMapper extends BaseMapper<UserEntity> {
    @Select("SELECT all_warehouses FROM sys_user WHERE id=#{id}")
    boolean allWarehouses(long id);

    @Select("SELECT warehouse_id FROM sys_user_warehouse WHERE user_id=#{id} ORDER BY warehouse_id")
    List<Long> warehouseIds(long id);

    @Select("SELECT warehouse_scope_version FROM sys_user WHERE id=#{id}")
    Integer warehouseScopeVersion(long id);

    @Update(
            "UPDATE sys_user SET all_warehouses=#{all},warehouse_scope_version=warehouse_scope_version+1 WHERE id=#{id} AND warehouse_scope_version=#{version}")
    int updateWarehouseScope(
            @Param("id") long id, @Param("all") boolean all, @Param("version") int version);

    @Delete("DELETE FROM sys_user_warehouse WHERE user_id=#{id}")
    int deleteWarehouseScope(long id);

    @Insert(
            "<script>INSERT INTO sys_user_warehouse(user_id,warehouse_id) VALUES <foreach collection='ids' item='w' separator=','>(#{id},#{w})</foreach></script>")
    int insertWarehouseScope(@Param("id") long id, @Param("ids") List<Long> ids);

    @Select("SELECT * FROM sys_user WHERE username=#{username}")
    UserEntity findByUsername(String username);

    @Select(
            "SELECT DISTINCT p.code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id JOIN sys_role r ON r.id=rp.role_id JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} AND r.status='ENABLED' AND p.status='ENABLED'")
    List<String> findPermissionCodes(long userId);

    @Select(
            "SELECT DISTINCT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} AND r.status='ENABLED' ORDER BY r.code")
    List<String> findRoleCodes(long userId);

    @Select(
            "SELECT r.id FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} ORDER BY r.id")
    List<Long> findRoleIds(long userId);

    @Delete("DELETE FROM sys_user_role WHERE user_id=#{userId}")
    int deleteRoles(long userId);

    @Insert(
            "<script>INSERT INTO sys_user_role(user_id,role_id) VALUES <foreach collection='roleIds' item='id' separator=','>(#{userId},#{id})</foreach></script>")
    int insertRoles(@Param("userId") long userId, @Param("roleIds") List<Long> roleIds);
}
