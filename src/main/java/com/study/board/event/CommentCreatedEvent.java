package com.study.board.event;


public record CommentCreatedEvent(Long postAuthorId, Long commentAuthorId, String postAuthorEmail, String postAuthorPhone,
                                  String commentUsername, String commentContent) {
}
