package io.qimo.usdtzero.event;

import io.qimo.usdtzero.service.AmountPoolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
public class OrderAmountPoolEventListener {
    @Autowired
    private AmountPoolService amountPoolService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderAmountAllocateEvent(OrderAmountAllocateEvent event) {
        amountPoolService.allocateAmount(event.getAddress(), event.getAmount());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderAmountReleaseEvent(OrderAmountReleaseEvent event) {
        amountPoolService.releaseAmount(event.getAddress(), event.getAmount());
    }
} 