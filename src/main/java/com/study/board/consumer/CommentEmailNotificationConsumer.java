package com.study.board.consumer;

import com.study.board.config.RabbitMQConsumerConfig;
import com.study.board.event.CommentCreatedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CommentEmailNotificationConsumer {

    private final CommentEmailNotificationService commentEmailNotificationService;

    public CommentEmailNotificationConsumer(CommentEmailNotificationService commentEmailNotificationService) {
        this.commentEmailNotificationService = commentEmailNotificationService;
    }

    @RabbitListener(queues = RabbitMQConsumerConfig.EMAIL_QUEUE_NAME)
    public void handleCommentNotification(CommentCreatedEvent event) {
        commentEmailNotificationService.notifyNewComment(
                event.postAuthorEmail(),
                event.commentUsername(),
                event.commentContent()
        );
    }
}
