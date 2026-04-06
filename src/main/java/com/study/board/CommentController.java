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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts/{postId}/comments")  // 댓글은 게시글 하위 리소스
public class CommentController {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    // 두 개의 Repository를 생성자 주입
    public CommentController(CommentRepository commentRepository,
                             PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
    }

    // CREATE — 특정 게시글에 댓글 생성
    // POST /posts/1/comments
    @PostMapping
    public ResponseEntity<Comment> createComment(@PathVariable Long postId,
                                                 @RequestBody Comment request) {
        return postRepository.findById(postId)
                .map(post -> {
                    // 양방향 관계 설정: 주인 쪽(Comment)에서 Post 설정
                    Comment comment = new Comment(request.getAuthor(), request.getContent(), post);
                    // 반대쪽(Post)에도 설정 → 객체 일관성 유지
                    post.getComments().add(comment);
                    Comment saved = commentRepository.save(comment);
                    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
                })
                .orElse(ResponseEntity.notFound().build());  // 게시글이 없으면 404
    }

    // READ — 특정 게시글의 댓글 목록
    // GET /posts/1/comments
    @GetMapping
    public ResponseEntity<List<Comment>> getComments(@PathVariable Long postId) {
        if (!postRepository.existsById(postId)) {
            return ResponseEntity.notFound().build();
        }
        List<Comment> comments = commentRepository.findByPostId(postId);
        return ResponseEntity.ok(comments);
    }

    // READ — 댓글 단건 조회
    // GET /posts/1/comments/1
    @GetMapping("/{commentId}")
    public ResponseEntity<Comment> getComment(@PathVariable Long postId,
                                              @PathVariable Long commentId) {
        return commentRepository.findById(commentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // UPDATE — 댓글 수정
    // PUT /posts/1/comments/1
    @PutMapping("/{commentId}")
    public ResponseEntity<Comment> updateComment(@PathVariable Long postId,
                                                 @PathVariable Long commentId,
                                                 @RequestBody Comment request) {
        return commentRepository.findById(commentId)
                .map(comment -> {
                    comment.setContent(request.getContent());
                    Comment updated = commentRepository.save(comment);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE — 댓글 삭제
    // DELETE /posts/1/comments/1
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long postId,
                                              @PathVariable Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            return ResponseEntity.notFound().build();
        }
        commentRepository.deleteById(commentId);
        return ResponseEntity.noContent().build();
    }
}
