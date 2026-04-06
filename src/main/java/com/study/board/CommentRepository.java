package com.study.board;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 쿼리 메서드: 특정 게시글의 댓글 목록 조회
    // → SELECT * FROM comment WHERE post_id = ? 자동 생성
    List<Comment> findByPostId(Long postId);
}
