package io.qimo.usdtzero.event;

import org.springframework.context.ApplicationEvent;

public class OrderAmountReleaseEvent extends ApplicationEvent {
    private final String address;
    private final long amount;

    public OrderAmountReleaseEvent(Object source, String address, long amount) {
        super(source);
        this.address = address;
        this.amount = amount;
    }
    public String getAddress() { return address; }
    public long getAmount() { return amount; }
} 