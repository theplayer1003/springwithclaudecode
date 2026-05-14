package com.study.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private CommentNotificationService commentNotificationService;

    @InjectMocks
    private CommentService commentService;

    @Test
    void createComment_ValidRequest_ReturnsCreatedComment() {
        Long postId = 1L;
        String username = "Alice";
        String commentContent = "댓글 내용";

        Member alice = createMember(1L, username, "alice@email.com");
        Post post = createPost(postId, "게시글 제목", "게시글 본문", alice);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(memberRepository.findByUsername(username)).thenReturn(Optional.of(alice));

        CommentResponse response = commentService.createComment(postId, new CommentCreateRequest(commentContent),
                username);

        assertThat(response.author()).isEqualTo(username);
        assertThat(response.content()).isEqualTo(commentContent);
    }


    @Test
    void createComment_NonExistingMember_ThrowsResourceNotFoundException() {
        Long postId = 1L;
        String username = "Alice";
        String commentContent = "댓글 내용";
        String nonExistingUsername = "존재하지 않는 사용자이름";

        Member alice = createMember(1L, username, "alice@email.com");
        Post post = createPost(postId, "게시글 제목", "게시글 본문", alice);

        when(postRepository.findById(postId)).thenReturn(
                Optional.of(post));

        assertThatThrownBy(
                () -> commentService.createComment(postId, new CommentCreateRequest(commentContent),
                        nonExistingUsername))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("해당 회원을 찾을 수 없습니다");
    }

    @Test
    void createComment_NonExistingPost_ThrowsResourceNotFoundException() {
        Long nonExistingPostId = 999L;
        String commentContent = "댓글 내용";

        assertThatThrownBy(
                () -> commentService.createComment(nonExistingPostId, new CommentCreateRequest(commentContent),
                        "username"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 게시글을 찾을 수 없습니다.");
    }

    @Test
    void getComments_ExistingPost_ReturnsCorrectCommentList() {
        Long postId = 1L;
        String username = "Alice";
        String commentContent1 = "댓글 내용1";
        String commentContent2 = "댓글 내용2";

        PageRequest pageRequest = PageRequest.of(0, 20);
        Member alice = createMember(1L, username, "alice@email.com");
        Post post = createPost(postId, "게시글 제목", "게시글 본문", alice);
        List<Comment> comments = List.of(createComment(1L, commentContent1, post, alice),
                createComment(2L, commentContent2, post, alice));

        when(postRepository.existsById(postId)).thenReturn(true);
        when(commentRepository.findByPostIdWithMember(postId, pageRequest)).thenReturn(
                new PageImpl<>(comments, pageRequest, comments.size())
        );

        Page<CommentResponse> pageResponse = commentService.getComments(postId, pageRequest);

        assertThat(pageResponse.getTotalPages()).isEqualTo(1);
        assertThat(pageResponse.getTotalElements()).isEqualTo(2);
        List<CommentResponse> list = pageResponse.get().toList();

        assertThat(list.get(0).content()).isEqualTo(commentContent1);
        assertThat(list.get(1).content()).isEqualTo(commentContent2);
    }

    @Test
    void getComments_NonExistingPost_ThrowsResourceNotFoundException() {
        Long nonExistingPostId = 999L;

        assertThatThrownBy(() -> commentService.getComments(nonExistingPostId, PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 게시글을 찾을 수 없습니다.");
    }

    @Test
    void getComment_ExistingCommentAndPost_ReturnsCorrectComment() {
        String username = "Alice";
        Long postId = 1L;
        Long commentId = 1L;
        String commentContent = "댓글 내용";

        Member alice = createMember(1L, username, "alice@email.com");
        Post post = createPost(postId, "게시글 제목", "게시글 본문", alice);
        Comment comment = createComment(commentId, commentContent, post, alice);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        CommentResponse commentResponse = commentService.getComment(postId, commentId);

        assertThat(commentResponse.content()).isEqualTo(commentContent);
        assertThat(commentResponse.author()).isEqualTo(username);
        assertThat(commentResponse.id()).isEqualTo(commentId);
    }

    @Test
    void getComment_NonExistingComment_ThrowsResourceNotFoundException() {
        Long postId = 1L;
        Long commentId = 1L;

        assertThatThrownBy(() -> commentService.getComment(postId, commentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 댓글을 찾을 수 없습니다.");
    }

    @Test
    void getComment_PostCommentMismatch_ThrowsResourceNotFoundException() {
        String username = "Alice";
        Long postId = 1L;
        Long mismatchPostId = 2L;
        Long commentId = 1L;
        String commentContent = "댓글 내용";

        Member alice = createMember(1L, username, "alice@email.com");
        Post post = createPost(postId, "게시글 제목", "게시글 본문", alice);
        Comment comment = createComment(commentId, commentContent, post, alice);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.getComment(mismatchPostId, commentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("게시글 댓글 불일치 오류");
    }

    @Test
    void updateComment_ValidRequest_ReturnsUpdatedComment() {
        String username = "Alice";
        Long postId = 1L;
        Long commentId = 1L;
        String originalCommentContent = "댓글 내용";
        String updateCommentContent = "변경된 댓글 내용";

        Member alice = createMember(1L, username, "alice@email.com");
        Post post = createPost(postId, "게시글 제목", "게시글 본문", alice);
        Comment comment = createComment(commentId, originalCommentContent, post, alice);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        CommentResponse commentResponse = commentService.updateComment(postId, commentId,
                new CommentUpdateRequest(updateCommentContent), username);

        assertThat(commentResponse.id()).isEqualTo(commentId);
        assertThat(commentResponse.author()).isEqualTo(username);
        assertThat(commentResponse.content()).isNotEqualTo(originalCommentContent);
        assertThat(commentResponse.content()).isEqualTo(updateCommentContent);
    }

    @Test
    void updateComment_NonExistingComment_ThrowsResourceNotFoundException() {
        Long postId = 1L;
        Long commentId = 1L;

        assertThatThrownBy(
                () -> commentService.updateComment(postId, commentId, new CommentUpdateRequest("변경된 댓글 내용"), "사용자 이름"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 댓글을 찾을 수 없습니다.");
    }

    @Test
    void updateComment_ByNonAuthor_ThrowsUnauthorizedAccessException() {
        Long postId = 1L;
        Long commentId = 1L;

        Member alice = createMember(1L, "Alice", "alice@email.com");
        Post post = createPost(postId, "게시글 제목", "게시글 본문", alice);
        Comment comment = createComment(commentId, "댓글 내용", post, alice);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(
                () -> commentService.updateComment(postId, commentId, new CommentUpdateRequest("변경된 댓글 내용"), "권한이 없는 사용자"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("댓글을 수정할 권한이 없습니다");
    }

    @Test
    void deleteComment_ValidRequest_DeletesComment() {
        String username = "Alice";
        Long postId = 1L;
        Long commentId = 1L;

        Member alice = createMember(1L, username, "alice@email.com");
        Post post = createPost(postId, "게시글 제목", "게시글 본문", alice);
        Comment comment = createComment(commentId, "댓글 내용", post, alice);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        commentService.deleteComment(postId, commentId, username);
        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_ByNonAuthor_ThrowsUnauthorizedAccessException() {
        Long postId = 1L;
        Long commentId = 1L;

        Member alice = createMember(1L, "Alice", "alice@email.com");
        Post post = createPost(postId, "게시글 제목", "게시글 본문", alice);
        Comment comment = createComment(commentId, "댓글 내용", post, alice);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(postId, commentId, "nonAuthor"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("댓글을 삭제할 권한이 없습니다");
    }

    @Test
    void deleteComment_NonExistingComment_ThrowsResourceNotFoundException() {
        Long postId = 1L;
        Long commentId = 1L;

        assertThatThrownBy(() -> commentService.deleteComment(postId, commentId, "username"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 댓글을 찾을 수 없습니다.");
    }

    @Test
    void deleteCommentForced_ValidRequest_DeletesComment() {
        Long postId = 1L;
        Long commentId = 1L;

        Member alice = createMember(1L, "Alice", "alice@email.com");
        Post post = createPost(postId, "게시글 제목", "게시글 본문", alice);
        Comment comment = createComment(commentId, "댓글 내용", post, alice);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(cacheManager.getCache("posts")).thenReturn(cache);

        commentService.deleteCommentForced(commentId);
        verify(commentRepository).delete(comment);
        verify(cache).evict(postId);
    }

    @Test
    void deleteCommentForced_NonExistingComment_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> commentService.deleteCommentForced(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 댓글을 찾을 수 없습니다.");
    }

    @Test
    void createComment_ValidRequest_CallNotifyNewComment(){
        String aliceUsername = "Alice";
        String aliceEmail = "alice@email.com";
        String bobUsername = "Bob";
        String bobEmail = "bob@email.com";
        String commentContent = "Bob 이 쓴 댓글 내용";
        Long alicePostId = 1L;

        Member alice = createMember(1L, aliceUsername, aliceEmail);
        Member bob = createMember(2L, bobUsername, bobEmail);
        Post post = createPost(alicePostId, "Alice가 쓴 게시글 제목", "Alice가 쓴 게시글 본문", alice);

        when(postRepository.findById(alicePostId)).thenReturn(
                Optional.of(post));
        when(memberRepository.findByUsername(bobUsername)).thenReturn(Optional.of(bob));

        commentService.createComment(alicePostId, new CommentCreateRequest(commentContent), bobUsername);

        verify(commentNotificationService).notifyNewComment(aliceEmail, bobUsername, commentContent);
    }

    private Member createMember(Long memberId, String username, String password, String email) {
        Member member = new Member(username, password, email);
        ReflectionTestUtils.setField(member, "id", memberId);

        return member;
    }

    private Member createMember(long memberId, String username, String email) {
        Member member = new Member(username, "password1234!@#$", email);
        ReflectionTestUtils.setField(member, "id", memberId);

        return member;
    }

    private Post createPost(Long postId, String title, String content, Member member) {
        Post post = new Post(title, content, member);
        ReflectionTestUtils.setField(post, "id", postId);

        return post;
    }

    private Comment createComment(Long commentId, String content, Post post, Member member) {
        Comment comment = new Comment(content, post, member);
        ReflectionTestUtils.setField(comment, "id", commentId);

        return comment;
    }
}
