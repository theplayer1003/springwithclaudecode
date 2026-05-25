package com.study.board.consumer;

public interface CommentSmsNotificationService {

    void notifyNewComment(String postAuthorPhone, String commentAuthor, String commentContent);
}
