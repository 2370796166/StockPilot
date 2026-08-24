package com.stockpilot.security.infrastructure.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.stockpilot.security.domain.UserEntity; import org.apache.ibatis.annotations.*; import java.util.List;
public interface UserMapper extends BaseMapper<UserEntity> {
 @Select("SELECT * FROM sys_user WHERE username=#{username}") UserEntity findByUsername(String username);
 @Select("SELECT DISTINCT p.code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id JOIN sys_role r ON r.id=rp.role_id JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} AND r.status='ENABLED' AND p.status='ENABLED'") List<String> findPermissionCodes(long userId);
 @Select("SELECT DISTINCT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} AND r.status='ENABLED' ORDER BY r.code") List<String> findRoleCodes(long userId);
 @Select("SELECT r.id FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} ORDER BY r.id") List<Long> findRoleIds(long userId);
 @Delete("DELETE FROM sys_user_role WHERE user_id=#{userId}") int deleteRoles(long userId);
 @Insert("<script>INSERT INTO sys_user_role(user_id,role_id) VALUES <foreach collection='roleIds' item='id' separator=','>(#{userId},#{id})</foreach></script>") int insertRoles(@Param("userId") long userId,@Param("roleIds") List<Long> roleIds);
}
