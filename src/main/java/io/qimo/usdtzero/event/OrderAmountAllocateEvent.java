package io.qimo.usdtzero.event;

import org.springframework.context.ApplicationEvent;

public class OrderAmountAllocateEvent extends ApplicationEvent {
    private final String address;
    private final long amount;
    private final Long id;

    public OrderAmountAllocateEvent(Object source, String address, long amount, Long id) {
        super(source);
        this.address = address;
        this.amount = amount;
        this.id = id;
    }
    public String getAddress() { return address; }
    public long getAmount() { return amount; }
    public Long getId() { return id; }
} 