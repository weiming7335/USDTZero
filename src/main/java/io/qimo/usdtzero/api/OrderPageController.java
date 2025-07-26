package io.qimo.usdtzero.api;

import io.qimo.usdtzero.api.response.OrderDetailResponse;
import io.qimo.usdtzero.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/v1/order")
public class OrderPageController {
    @Autowired
    private OrderService orderService;

    @GetMapping("/pay/{tradeNo}")
    public String payPage(@PathVariable String tradeNo, Model model) {
        OrderDetailResponse detail = orderService.getOrderDetailByTradeNo(tradeNo);
        model.addAttribute("trade_no", detail.getTradeNo());
        model.addAttribute("amount", detail.getAmount());
        model.addAttribute("actual_amount", detail.getActualAmount());
        model.addAttribute("status", detail.getStatus());
        model.addAttribute("chain_type", detail.getChainType());
        model.addAttribute("address", detail.getAddress());
        model.addAttribute("timeout", detail.getTimeout());
        return "pay";
    }
} 