package com.study.board.controller;

import com.study.board.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/posts")
public class AdminPostController {

    private final PostService postService;

    public AdminPostController(PostService postService) {
        this.postService = postService;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePostForced(@PathVariable Long id) {
        postService.deletePostForced(id);
        return ResponseEntity.noContent().build();
    }
}
