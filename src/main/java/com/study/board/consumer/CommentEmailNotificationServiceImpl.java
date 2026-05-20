package com.study.board.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CommentEmailNotificationServiceImpl implements CommentEmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CommentEmailNotificationServiceImpl.class);

    @Override
    @Async("notificationExecutor")
    public void notifyNewComment(String postAuthorEmail, String commentAuthor, String commentContent) {
        log.info("현재 스레드 이름: [{}]", Thread.currentThread().getName());
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        log.info(postAuthorEmail + "로 메일을 전송했습니다.");
    }
}
