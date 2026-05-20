package com.study.board.listener;

import com.study.board.config.RabbitMQProducerConfig;
import com.study.board.event.CommentCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CommentNotificationListener {

    private final RabbitTemplate rabbitTemplate;

    public CommentNotificationListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CommentCreatedEvent event) {
        if (event.postAuthorId().equals(event.commentAuthorId())) {
            return;
        }

//        commentNotificationService.notifyNewComment(event.postAuthorEmail(), event.commentUsername(),
//                event.commentContent());
        rabbitTemplate.convertAndSend(
                RabbitMQProducerConfig.EXCHANGE_NAME,
                "",
                event
        );
    }
}
