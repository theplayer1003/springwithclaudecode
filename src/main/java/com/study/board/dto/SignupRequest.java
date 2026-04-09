package com.study.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "username 은 필수 입력 값입니다")
        @Size(max = 20, message = "username 은 20자 이내여야합니다")
        String username,

        @NotBlank(message = "password 는 필수 입력 값입니다")
        @Size(min = 12, max = 100, message = "비밀번호는 최소 12자 이상 이어야합니다")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\w\\s])[A-Za-z\\d[^\\w\\s]]{12,}$",
                message = "비밀번호는 12자 이상이며, 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "email 는 필수 입력 값입니다")
        @Pattern(
                regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$",
                message = "이메일 형식이 유효하지 않습니다."
        )
        @Size(max = 50, message = "이메일은 50자 이내로 입력해주세요")
        String email) {
}
