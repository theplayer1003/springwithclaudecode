package com.study.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username 은 필수 입력 값입니다")
        @Size(max = 20, message = "username 은 20자 이내여야합니다")
        String username,

        @NotBlank(message = "password 는 필수 입력 값입니다")
        String password
) {
}
