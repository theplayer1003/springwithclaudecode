package com.study.board;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository<Entity 타입, ID 타입>을 상속하면 CRUD 메서드가 자동 제공됨
// 인터페이스만 만들면 스프링이 구현체를 자동 생성하여 빈으로 등록
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByTitleContaining(String keyword);
    List<Post> findAllByOrderByCreatedAtDesc();
}
