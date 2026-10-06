package com.stockpilot.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockpilot.security.domain.RoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface RoleMapper extends BaseMapper<RoleEntity> {
    @Select("SELECT * FROM sys_role WHERE code=#{code} FOR UPDATE")
    RoleEntity findByCodeForUpdate(String code);

    @Select(
            "SELECT permission_id FROM sys_role_permission WHERE role_id=#{roleId} ORDER BY permission_id")
    List<Long> findPermissionIds(long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id=#{roleId}")
    int deletePermissions(long roleId);

    @Insert(
            "<script>INSERT INTO sys_role_permission(role_id,permission_id) VALUES <foreach collection='permissionIds' item='id' separator=','>(#{roleId},#{id})</foreach></script>")
    int insertPermissions(
            @Param("roleId") long roleId, @Param("permissionIds") List<Long> permissionIds);
}
