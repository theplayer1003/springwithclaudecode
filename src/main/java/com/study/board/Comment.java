package com.study.board;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String author;

    private String content;

    private LocalDateTime createdAt;

    // 다대일 관계: 댓글(N) : 게시글(1)
    // 이 쪽이 관계의 주인 → 외래키(post_id)를 관리
    // fetch = LAZY → 댓글을 조회할 때 게시글을 즉시 가져오지 않음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")  // DB에 생성될 외래키 컬럼 이름
    @JsonIgnore  // JSON 응답에 post 객체를 포함하지 않음 (무한 순환 방지)
    private Post post;

    protected Comment() {
    }

    public Comment(String author, String content, Post post) {
        this.author = author;
        this.content = content;
        this.post = post;
        this.createdAt = LocalDateTime.now();
    }

    // --- getter / setter ---

    public Long getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
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

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }
}
