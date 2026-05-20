package com.study.board.consumer;

import com.study.board.config.RabbitMQConsumerConfig;
import com.study.board.event.CommentCreatedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CommentSmsNotificationConsumer {

    private final CommentSmsNotificationService commentSmsNotificationService;

    public CommentSmsNotificationConsumer(CommentSmsNotificationService commentSmsNotificationService) {
        this.commentSmsNotificationService = commentSmsNotificationService;
    }

    @RabbitListener(queues = RabbitMQConsumerConfig.SMS_QUEUE_NAME)
    public void handleCommentNotification(CommentCreatedEvent event) {
        commentSmsNotificationService.notifyNewComment(
                event.postAuthorPhone(),
                event.commentUsername(),
                event.commentContent()
        );
    }
}
