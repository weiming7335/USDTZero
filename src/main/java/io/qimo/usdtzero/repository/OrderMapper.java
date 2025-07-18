package io.qimo.usdtzero.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.qimo.usdtzero.model.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
} 