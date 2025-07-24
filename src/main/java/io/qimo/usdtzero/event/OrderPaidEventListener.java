package io.qimo.usdtzero.event;

import io.qimo.usdtzero.notify.TelegramBotNotifier;
import io.qimo.usdtzero.util.DateTimeFormatUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
public class OrderPaidEventListener {
    @Autowired(required = false)
    private TelegramBotNotifier telegramBotNotifier;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        OrderMessage msg = event.getOrderMessage();
        log.info("监听到订单支付事件: tradeNo={}, to={}, amount={}, txHash={}",
                msg.getTradeNo(), msg.getToAddress(), msg.getAmount(), msg.getTxHash());
        if (telegramBotNotifier != null) {
            String text = String.format(
                "🎉【订单支付成功】\n" +
                "🆔 商户订单: %s\n" +
                "🏦 收款地址: %s\n" +
                "💴 法币金额: %s\n" +
                "💵 USDT金额: %s\n" +
                "🔗 链类型: %s\n" +
                "🔑 交易哈希: %s\n" +
                "🕒 下单时间: %s\n" +
                "✅ 支付时间: %s",
                msg.getTradeNo(),
                msg.getToAddress(),
                msg.getAmount(),
                msg.getActualAmount(),
                msg.getChainType(),
                msg.getTxHash(),
                DateTimeFormatUtils.formatDateTime(msg.getCreateTime()),
                DateTimeFormatUtils.formatDateTime(msg.getPayTime())
            );
            telegramBotNotifier.sendToAdmin(text);
            telegramBotNotifier.sendToGroup(text);
        }
    }
} 