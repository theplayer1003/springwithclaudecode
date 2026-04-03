package com.study.board;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController                 // 이 클래스는 REST API를 처리하는 컨트롤러 (JSON 반환)
@RequestMapping("/posts")       // 이 컨트롤러의 모든 URL은 /posts로 시작
public class PostController {

    // --- 메모리 저장소 (DB 대신 Map을 사용) ---
    // Key: 게시글 ID, Value: Post 객체
    private final Map<Long, Post> posts = new HashMap<>();

    // ID 자동 증가를 위한 카운터
    // AtomicLong: 여러 요청이 동시에 와도 안전하게 숫자를 증가시키는 클래스
    private final AtomicLong idCounter = new AtomicLong(1);

    // ==========================================
    // CREATE — 게시글 생성
    // POST /posts
    // ==========================================
    @PostMapping
    public ResponseEntity<Post> createPost(@RequestBody Post request) {
        // 새 게시글 생성: ID는 서버가 자동 부여
        Long newId = idCounter.getAndIncrement();
        Post post = new Post(newId, request.getTitle(), request.getContent());

        // 메모리에 저장
        posts.put(newId, post);

        // 201 Created 상태코드와 함께 생성된 게시글 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    // ==========================================
    // READ — 게시글 목록 조회
    // GET /posts
    // ==========================================
    @GetMapping
    public List<Post> getAllPosts() {
        // Map의 모든 값(Post)을 리스트로 변환해서 반환
        return new ArrayList<>(posts.values());
    }

    // ==========================================
    // READ — 게시글 단건 조회
    // GET /posts/{id}
    // ==========================================
    @GetMapping("/{id}")
    public ResponseEntity<Post> getPost(@PathVariable Long id) {
        Post post = posts.get(id);

        // 해당 ID의 게시글이 없으면 404 반환
        if (post == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(post);
    }

    // ==========================================
    // UPDATE — 게시글 수정
    // PUT /posts/{id}
    // ==========================================
    @PutMapping("/{id}")
    public ResponseEntity<Post> updatePost(@PathVariable Long id,
                                           @RequestBody Post request) {
        Post post = posts.get(id);

        // 해당 ID의 게시글이 없으면 404 반환
        if (post == null) {
            return ResponseEntity.notFound().build();
        }

        // 기존 게시글의 제목과 내용을 수정
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());

        return ResponseEntity.ok(post);
    }

    // ==========================================
    // DELETE — 게시글 삭제
    // DELETE /posts/{id}
    // ==========================================
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        Post removed = posts.remove(id);

        // 해당 ID의 게시글이 없으면 404 반환
        if (removed == null) {
            return ResponseEntity.notFound().build();
        }

        // 204 No Content — 삭제 성공, 반환할 데이터 없음
        return ResponseEntity.noContent().build();
    }
}
