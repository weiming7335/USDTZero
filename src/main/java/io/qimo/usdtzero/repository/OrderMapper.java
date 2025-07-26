package io.qimo.usdtzero.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.qimo.usdtzero.constant.NotifyStatus;
import io.qimo.usdtzero.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    
    /**
     * 更新订单状态（只有当订单处于指定状态时才能更新成功）
     * @param id 订单ID
     * @param oldStatus 当前状态（只有匹配此状态才能更新）
     * @param newStatus 新状态
     * @return 更新的记录数
     */
    default int updateStatusIfMatch(Long id, String oldStatus, String newStatus) {
        LambdaQueryWrapper<Order> updateWrapper = new LambdaQueryWrapper<>();
        updateWrapper.eq(Order::getId, id)
                    .eq(Order::getStatus, oldStatus);
        
        Order updateOrder = new Order();
        updateOrder.setStatus(newStatus);
        updateOrder.setUpdateTime(LocalDateTime.now());
        
        return update(updateOrder, updateWrapper);
    }

    /**
     * 根据 tradeNo 删除订单
     */
    default int deleteByTradeNo(String tradeNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getTradeNo, tradeNo);
        return delete(wrapper);
    }

    default int updatePayTimeAndTxHashById(Long id, LocalDateTime payTime, String txHash) {
        return this.update(new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, id)
                .set(Order::getPayTime, payTime)
                .set(Order::getTxHash, txHash));
    }

} 