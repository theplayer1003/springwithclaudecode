package com.study.board;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

@Entity  // 이 클래스가 DB 테이블과 매핑됨 → "posts" 테이블이 자동 생성됨
public class Post {

    @Id  // 기본키(Primary Key)
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // DB가 ID를 자동 증가시킴
    private Long id;

    private String title;

    private String content;

    private LocalDateTime createdAt;

    // 양방향 관계: 게시글(1) : 댓글(N)
    // mappedBy = "post" → Comment 클래스의 post 필드에 의해 매핑됨 (읽기 전용)
    // cascade = ALL → 게시글 저장/삭제 시 댓글도 함께 저장/삭제
    // orphanRemoval = true → 댓글 목록에서 제거된 댓글은 DB에서도 삭제
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    // JPA는 기본 생성자가 필수. protected로 외부 직접 사용을 제한
    protected Post() {
    }

    public Post(String title, String content) {
        this.title = title;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    // --- getter / setter ---

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Comment> getComments() {
        return comments;
    }
}
