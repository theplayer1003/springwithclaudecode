package com.study.board.controller;

import com.study.board.dto.CommentCreateRequest;
import com.study.board.dto.CommentResponse;
import com.study.board.dto.CommentUpdateRequest;
import com.study.board.service.CommentService;
import jakarta.validation.Valid;
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

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    // CREATE — 특정 게시글에 댓글 생성
    // POST /posts/1/comments
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(@PathVariable Long postId,
                                                         @Valid @RequestBody CommentCreateRequest request) {
        CommentResponse comment = commentService.createComment(postId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    // READ — 특정 게시글의 댓글 목록
    // GET /posts/1/comments
    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long postId) {
        List<CommentResponse> comments = commentService.getComments(postId);
        return ResponseEntity.ok(comments);
    }

    // READ — 댓글 단건 조회
    // GET /posts/1/comments/1
    @GetMapping("/{commentId}")
    public ResponseEntity<CommentResponse> getComment(@PathVariable Long postId,
                                                      @PathVariable Long commentId) {
        CommentResponse comment = commentService.getComment(postId, commentId);
        return ResponseEntity.ok(comment);
    }

    // UPDATE — 댓글 수정
    // PUT /posts/1/comments/1
    @PutMapping("/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(@PathVariable Long postId,
                                                         @PathVariable Long commentId,
                                                         @Valid @RequestBody CommentUpdateRequest request) {
        CommentResponse commentResponse = commentService.updateComment(postId, commentId, request);
        return ResponseEntity.ok(commentResponse);
    }

    // DELETE — 댓글 삭제
    // DELETE /posts/1/comments/1
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long postId,
                                              @PathVariable Long commentId) {
        commentService.deleteComment(postId, commentId);
        return ResponseEntity.noContent().build();
    }
}
