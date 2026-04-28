package com.study.board.dto;

import com.study.board.entity.Member;
import com.study.board.entity.Role;

public record MemberResponse(
        Long id,
        String username,
        String email,
        Role role) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getUsername(),
                member.getEmail(),
                member.getRole());
    }
}
