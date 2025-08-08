package io.qimo.usdtzero.event;

import org.springframework.context.ApplicationEvent;
import lombok.Getter;

@Getter
public class OrderPaidEvent extends ApplicationEvent {
    private final OrderMessage orderMessage;

    public OrderPaidEvent(Object source, OrderMessage orderMessage) {
        super(source);
        this.orderMessage = orderMessage;
    }
} 