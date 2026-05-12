package com.study.board.repository;

import com.study.board.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c JOIN FETCH c.member WHERE c.post.id = :postId")
    Page<Comment> findByPostIdWithMember(@Param("postId") Long postId, Pageable pageable);
}
