package com.study.board.dto;

import com.study.board.entity.Post;
import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        String author,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer commentsCount) {
    public static PostResponse from(Post post) {
        return new PostResponse(post.getId(),
                post.getAuthor(),
                post.getTitle(),
                post.getContent(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getCommentsCount()
        );
    }
}
