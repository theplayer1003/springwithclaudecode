package com.study.board.consumer;


public interface CommentEmailNotificationService {

    void notifyNewComment(String postAuthorEmail, String commentAuthor, String commentContent);
}
