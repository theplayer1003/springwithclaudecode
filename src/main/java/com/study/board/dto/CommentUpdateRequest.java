package com.study.board.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentUpdateRequest(
        @NotBlank(message = "내용은 필수 입력값 입니다")
        String content
) {
}
