package com.study.board.service;

import com.study.board.dto.CommentCreateRequest;
import com.study.board.dto.CommentResponse;
import com.study.board.dto.CommentUpdateRequest;
import com.study.board.entity.Comment;
import com.study.board.entity.Post;
import com.study.board.exception.ResourceNotFoundException;
import com.study.board.repository.CommentRepository;
import com.study.board.repository.PostRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public CommentService(PostRepository postRepository, CommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    private static void checkPostCommentMatch(Long postId, Comment comment) {
        if (!comment.getPost().getId().equals(postId)) {
            throw new ResourceNotFoundException("게시글 댓글 불일치 오류");
        }
    }

    @Transactional
    public CommentResponse createComment(Long postId, CommentCreateRequest request) {
        Post post = findPostOrThrow(postId);

        Comment comment = new Comment(request.author(), request.content(), post);
        commentRepository.save(comment);

        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException(postId + " 게시글을 찾을 수 없습니다.");
        }

        return commentRepository.findByPostId(postId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommentResponse getComment(Long postId, Long commentId) {
        Comment comment = findCommentOrThrow(commentId);
        checkPostCommentMatch(postId, comment);

        return CommentResponse.from(comment);
    }

    @Transactional
    public CommentResponse updateComment(Long postId, Long commentId, CommentUpdateRequest request) {
        Comment comment = findCommentOrThrow(commentId);
        checkPostCommentMatch(postId, comment);

        comment.setContent(request.content());

        return CommentResponse.from(comment);
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId) {
        Comment comment = findCommentOrThrow(commentId);
        checkPostCommentMatch(postId, comment);
        commentRepository.delete(comment);
    }

    private Post findPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(postId + " 게시글을 찾을 수 없습니다."));
    }

    private Comment findCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException(commentId + " 댓글을 찾을 수 없습니다."));
    }
}
