package io.qimo.usdtzero.api;

import io.qimo.usdtzero.api.response.OrderDetailResponse;
import io.qimo.usdtzero.model.ApiResponse;
import io.qimo.usdtzero.service.OrderService;
import io.qimo.usdtzero.api.request.CreateOrderRequest;
import io.qimo.usdtzero.api.request.CancelOrderRequest;
import io.qimo.usdtzero.api.response.CreateOrderResponse;
import io.qimo.usdtzero.api.response.CancelOrderResponse;
import io.qimo.usdtzero.config.SignatureRequired;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

@RestController
@RequestMapping("/api/v1/order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    /**
     * 创建订单
     */
    @PostMapping("/create")
    @SignatureRequired
    public ApiResponse<CreateOrderResponse> createOrder(@RequestBody @Valid CreateOrderRequest dto) {
        return ApiResponse.success(orderService.createOrder(dto));
    }

    /**
     * 取消订单
     */
    @PostMapping("/cancel")
    @SignatureRequired
    public ApiResponse<CancelOrderResponse> cancelOrder(@RequestBody @Valid CancelOrderRequest dto) {
        return ApiResponse.success(orderService.cancelOrder(dto));
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/detail/{tradeNo}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(@PathVariable String tradeNo) {
        return ApiResponse.success(orderService.getOrderDetailByTradeNo(tradeNo));
    }

} 