package com.study.board;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts")
public class PostController {

    // HashMap과 AtomicLong이 사라지고, Repository로 교체됨
    // 생성자 주입 — Phase 1에서 배운 DI 방식
    private final PostRepository postRepository;

    public PostController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<Post> createPost(@RequestBody Post request) {
        // ID는 DB가 자동 생성하므로 title, content만 넘김
        Post post = new Post(request.getTitle(), request.getContent());
        Post saved = postRepository.save(post);  // DB에 저장
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // READ — 목록
    @GetMapping
    public List<Post> getAllPosts() {
        return postRepository.findAll();  // DB에서 전체 조회
    }

    // READ — 단건
    @GetMapping("/{id}")
    public ResponseEntity<Post> getPost(@PathVariable Long id) {
        return postRepository.findById(id)          // Optional<Post> 반환
                .map(ResponseEntity::ok)             // 있으면 200 + body
                .orElse(ResponseEntity.notFound().build());  // 없으면 404
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<Post> updatePost(@PathVariable Long id,
                                           @RequestBody Post request) {
        return postRepository.findById(id)
                .map(post -> {
                    post.setTitle(request.getTitle());
                    post.setContent(request.getContent());
                    Post updated = postRepository.save(post);  // 변경된 내용 저장
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        if (!postRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        postRepository.deleteById(id);  // DB에서 삭제 (cascade로 댓글도 함께 삭제)
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Post>> findByTitleContaining(@RequestParam String keyword) {
        List<Post> posts = postRepository.findByTitleContaining(keyword);

        return ResponseEntity.ok(posts);
    }
}
