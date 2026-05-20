package com.study.board.service;

import com.study.board.dto.CommentCreateRequest;
import com.study.board.dto.CommentResponse;
import com.study.board.dto.CommentUpdateRequest;
import com.study.board.entity.Comment;
import com.study.board.entity.Member;
import com.study.board.entity.Post;
import com.study.board.event.CommentCreatedEvent;
import com.study.board.exception.ResourceNotFoundException;
import com.study.board.exception.UnauthorizedAccessException;
import com.study.board.repository.CommentRepository;
import com.study.board.repository.MemberRepository;
import com.study.board.repository.PostRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;

    public CommentService(PostRepository postRepository, CommentRepository commentRepository,
                          MemberRepository memberRepository, CacheManager cacheManager,
                          ApplicationEventPublisher applicationEventPublisher) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.memberRepository = memberRepository;
        this.cacheManager = cacheManager;
        this.eventPublisher = applicationEventPublisher;
    }

    @CacheEvict(value = "posts", key = "#postId")
    @Transactional
    public CommentResponse createComment(Long postId, CommentCreateRequest request, String username) {
        Post post = findPostOrThrow(postId);
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("해당 회원을 찾을 수 없습니다"));

        Comment comment = new Comment(request.content(), post, member);
        commentRepository.save(comment);

        eventPublisher.publishEvent(new CommentCreatedEvent(post.getAuthorId(), member.getId(),
                post.getEmail(), post.getPhone(), username, request.content()));

        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getComments(Long postId, Pageable pageable) {
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException(postId + " 게시글을 찾을 수 없습니다.");
        }

        return commentRepository.findByPostIdWithMember(postId, pageable)
                .map(CommentResponse::from);
    }

    @Transactional(readOnly = true)
    public CommentResponse getComment(Long postId, Long commentId) {
        Comment comment = findCommentOrThrow(commentId);
        checkPostCommentMatch(postId, comment);

        return CommentResponse.from(comment);
    }

    @CacheEvict(value = "posts", key = "#postId")
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

    @CacheEvict(value = "posts", key = "#postId")
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
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException(commentId + " 댓글을 찾을 수 없습니다."));
        Long postId = comment.getPost().getId();

        commentRepository.delete(comment);
        Cache cache = cacheManager.getCache("posts");
        if (cache != null) {
            cache.evict(postId);
        }
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
