package com.study.board.service;


public interface CommentNotificationService {

    void notifyNewComment(String postAuthorEmail, String commentAuthor, String commentContent);
}
