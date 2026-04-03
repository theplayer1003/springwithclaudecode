package com.study.board;

import java.time.LocalDateTime;

public class Post {

    private Long id;                    // 게시글 고유 번호
    private String title;               // 제목
    private String content;             // 내용
    private LocalDateTime createdAt;    // 작성 시간

    // 기본 생성자 — Jackson이 JSON → 객체 변환할 때 필요
    public Post() {
    }

    public Post(Long id, String title, String content) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    // --- getter / setter ---
    // Jackson은 getter로 객체 → JSON 변환, setter로 JSON → 객체 변환

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
