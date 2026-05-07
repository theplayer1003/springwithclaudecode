package com.study.board.repository;

import com.study.board.entity.Post;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// JpaRepository<Entity 타입, ID 타입>을 상속하면 CRUD 메서드가 자동 제공됨
// 인터페이스만 만들면 스프링이 구현체를 자동 생성하여 빈으로 등록
public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByTitleContaining(String keyword, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.member ORDER BY p.createdAt DESC")
    Page<Post> findAllWithMember(Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.member WHERE p.member.id = :memberId ORDER BY p.createdAt DESC")
    Page<Post> findByMemberIdWithMember(Pageable pageable, @Param("memberId") Long memberId);
}
