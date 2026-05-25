package com.study.board.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CommentSmsNotificationServiceImpl implements CommentSmsNotificationService{

    private static final Logger log = LoggerFactory.getLogger(CommentSmsNotificationServiceImpl.class);

    @Override
    @Async("notificationExecutor")
    public void notifyNewComment(String postAuthorPhone, String commentAuthor, String commentContent) {
        log.info("현재 스레드 이름: [{}]", Thread.currentThread().getName());
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        log.info(postAuthorPhone + "로 문자를 보냈습니다.");
    }
}
