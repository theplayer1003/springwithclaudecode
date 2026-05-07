package com.study.board.controller;

import com.study.board.dto.PostCreateRequest;
import com.study.board.dto.PostResponse;
import com.study.board.dto.PostUpdateRequest;
import com.study.board.service.PostService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody PostCreateRequest request,
                                                   @AuthenticationPrincipal String username) {
        PostResponse post = postService.createPost(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    // READ — 목록
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getAllPosts(Pageable pageable) {
        return ResponseEntity.ok(postService.getAllPosts(pageable));
    }

    // READ — 단건
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long id) {
        PostResponse post = postService.getPost(id);
        return ResponseEntity.ok(post);
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(@PathVariable Long id,
                                                   @Valid @RequestBody PostUpdateRequest request,
                                                   @AuthenticationPrincipal String username) {
        PostResponse postResponse = postService.updatePost(id, request, username);
        return ResponseEntity.ok(postResponse);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id,
                                           @AuthenticationPrincipal String username) {
        postService.deletePost(id, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<Page<PostResponse>> findByTitleContaining(@RequestParam String keyword, Pageable pageable) {
        Page<PostResponse> postResponses = postService.searchPosts(keyword, pageable);
        return ResponseEntity.ok(postResponses);
    }

    @GetMapping("/my")
    public ResponseEntity<Page<PostResponse>> getMyPosts(Pageable pageable,
                                                         @AuthenticationPrincipal String username) {
        return ResponseEntity.ok(postService.getAllPostsByMemberId(pageable, username));
    }
}
