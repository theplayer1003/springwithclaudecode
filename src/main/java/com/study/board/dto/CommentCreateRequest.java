package com.study.board.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentCreateRequest(
        @NotBlank(message = "작성자는 필수 입력값 입니다")
        String author,

        @NotBlank(message = "내용은 필수 입력값 입니다")
        String content) {
}
