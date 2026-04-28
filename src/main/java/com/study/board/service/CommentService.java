package com.study.board.service;

import com.study.board.dto.CommentCreateRequest;
import com.study.board.dto.CommentResponse;
import com.study.board.dto.CommentUpdateRequest;
import com.study.board.entity.Comment;
import com.study.board.entity.Member;
import com.study.board.entity.Post;
import com.study.board.exception.ResourceNotFoundException;
import com.study.board.exception.UnauthorizedAccessException;
import com.study.board.repository.CommentRepository;
import com.study.board.repository.MemberRepository;
import com.study.board.repository.PostRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;

    public CommentService(PostRepository postRepository, CommentRepository commentRepository,
                          MemberRepository memberRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public CommentResponse createComment(Long postId, CommentCreateRequest request, String username) {
        Post post = findPostOrThrow(postId);
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("해당 회원을 찾을 수 없습니다"));

        Comment comment = new Comment(request.content(), post, member);
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
    public CommentResponse updateComment(Long postId, Long commentId, CommentUpdateRequest request, String username) {
        Comment comment = findCommentOrThrow(commentId);

        if (!username.equals(comment.getAuthor())) {
            throw new UnauthorizedAccessException("댓글을 수정할 권한이 없습니다");
        }
        checkPostCommentMatch(postId, comment);

        comment.setContent(request.content());

        return CommentResponse.from(comment);
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId, String username) {
        Comment comment = findCommentOrThrow(commentId);

        if (!username.equals(comment.getAuthor())) {
            throw new UnauthorizedAccessException("댓글을 삭제할 권한이 없습니다");
        }
        checkPostCommentMatch(postId, comment);

        commentRepository.delete(comment);
    }

    @Transactional
    public void deleteCommentForced(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new ResourceNotFoundException(commentId + " 댓글을 찾을 수 없습니다.");
        }

        commentRepository.deleteById(commentId);
    }

    private Post findPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(postId + " 게시글을 찾을 수 없습니다."));
    }

    private void checkPostCommentMatch(Long postId, Comment comment) {
        if (!comment.getPost().getId().equals(postId)) {
            throw new ResourceNotFoundException("게시글 댓글 불일치 오류");
        }
    }

    private Comment findCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException(commentId + " 댓글을 찾을 수 없습니다."));
    }
}
