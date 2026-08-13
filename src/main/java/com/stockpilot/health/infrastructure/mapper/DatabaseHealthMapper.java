package com.stockpilot.health.infrastructure.mapper;

import org.apache.ibatis.annotations.Select;

public interface DatabaseHealthMapper {

    @Select("SELECT 1")
    Integer selectOne();
}
