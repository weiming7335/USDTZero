package io.qimo.usdtzero.event;

import org.springframework.context.ApplicationEvent;

public class CallbackNotifyEvent extends ApplicationEvent {
    private final CallbackNotifyMessage message;

    public CallbackNotifyEvent(Object source, CallbackNotifyMessage message) {
        super(source);
        this.message = message;
    }

    public CallbackNotifyMessage getMessage() {
        return message;
    }
} 