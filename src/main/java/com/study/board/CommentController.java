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

@RestController
@RequestMapping("/comments")
public class CommentController {

    private final Map<Long, Comment> comments = new HashMap<>();

    private final AtomicLong idCounter = new AtomicLong(1);

    @PostMapping
    public ResponseEntity<Comment> createComment(@RequestBody Comment request) {
        Long newId = idCounter.getAndIncrement();
        Comment comment = new Comment(newId, request.getAuthor(), request.getContent());

        comments.put(newId, comment);

        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @GetMapping
    public List<Comment> getAllComments() {
        return new ArrayList<>(comments.values());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Comment> getComment(@PathVariable Long id) {
        Comment comment = comments.get(id);

        if (comment == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(comment);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Comment> updateComment(@PathVariable Long id, @RequestBody Comment request) {
        Comment comment = comments.get(id);

        if (comment == null) {
            return ResponseEntity.notFound().build();
        }

        comment.setContent(request.getContent());

        return ResponseEntity.ok(comment);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        Comment removed = comments.remove(id);

        if (removed == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}
