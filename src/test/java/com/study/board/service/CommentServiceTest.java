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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private Member member;
    private Post post;
    private Comment comment;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private CommentService commentService;

    @BeforeEach
    void setUp() {
        member = new Member("dummy", "dummy", "dummy");
        post = new Post("dummy", "dummy", member);
        comment = new Comment("test", post, member);
    }

    @Test
    void createComment_ValidRequest_ReturnsCreatedComment() {
        when(postRepository.findById(1L)).thenReturn(
                Optional.of(new Post("dummy", "dummy", member)));
        when(memberRepository.findByUsername("dummy")).thenReturn(Optional.of(member));

        CommentResponse comment = commentService.createComment(1L, new CommentCreateRequest("test"), "dummy");

        assertThat(comment.author()).isEqualTo("dummy");
        assertThat(comment.content()).isEqualTo("test");
    }

    @Test
    void createComment_NonExistingMember_ThrowsResourceNotFoundException() {
        when(postRepository.findById(1L)).thenReturn(
                Optional.of(new Post("dummy", "dummy", member)));

        assertThatThrownBy(
                () -> commentService.createComment(1L, new CommentCreateRequest("test"), "nonExistingUsername"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("해당 회원을 찾을 수 없습니다");
    }

    @Test
    void createComment_NonExistingPost_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> commentService.createComment(1L, new CommentCreateRequest("test"), "dummy"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 게시글을 찾을 수 없습니다.");
    }

    @Test
    void getComments_ExistingPost_ReturnsCorrectCommentList() {
        when(postRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByPostId(1L)).thenReturn(List.of(
                new Comment("test1", new Post("dummy", "dummy", member), member),
                new Comment("test2", new Post("dummy", "dummy", member), member)
        ));

        List<CommentResponse> comments = commentService.getComments(1L);

        assertThat(comments.get(0).author()).isEqualTo("dummy");
        assertThat(comments.get(0).content()).isEqualTo("test1");
        assertThat(comments.get(1).content()).isEqualTo("test2");
    }

    @Test
    void getComments_NonExistingPost_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> commentService.getComments(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 게시글을 찾을 수 없습니다.");
    }

    @Test
    void getComment_ExistingCommentAndPost_ReturnsCorrectComment() {
        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(comment, "id", 1L);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        CommentResponse commentResponse = commentService.getComment(1L, 1L);

        assertThat(commentResponse.content()).isEqualTo(comment.getContent());
        assertThat(commentResponse.author()).isEqualTo(comment.getAuthor());
        assertThat(commentResponse.id()).isEqualTo(comment.getId());
    }

    @Test
    void getComment_NonExistingComment_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> commentService.getComment(1L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 댓글을 찾을 수 없습니다.");
    }

    @Test
    void getComment_PostCommentMismatch_ThrowsResourceNotFoundException() {
        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(comment, "id", 1L);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.getComment(2L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("게시글 댓글 불일치 오류");
    }

    @Test
    void updateComment_ValidRequest_ReturnsUpdatedComment() {
        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(comment, "id", 1L);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        CommentResponse commentResponse = commentService.updateComment(1L, 1L,
                new CommentUpdateRequest("update content"), "dummy");

        assertThat(commentResponse.id()).isEqualTo(comment.getId());
        assertThat(commentResponse.author()).isEqualTo(comment.getAuthor());
        assertThat(commentResponse.content()).isEqualTo("update content");
    }

    @Test
    void updateComment_NonExistingComment_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> commentService.updateComment(1L, 1L, new CommentUpdateRequest("dummy"), "username"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 댓글을 찾을 수 없습니다.");
    }

    @Test
    void updateComment_ByNonAuthor_ThrowsUnauthorizedAccessException() {
        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(comment, "id", 1L);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(
                () -> commentService.updateComment(1L, 1L, new CommentUpdateRequest("update content"), "NonAthor"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("댓글을 수정할 권한이 없습니다");
    }

    @Test
    void deleteComment_ValidRequest_DeletesComment() {
        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(comment, "id", 1L);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(1L, 1L, "dummy");
        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_ByNonAuthor_ThrowsUnauthorizedAccessException() {
        ReflectionTestUtils.setField(post, "id", 1L);
        ReflectionTestUtils.setField(comment, "id", 1L);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(1L, 1L, "nonAuthor"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("댓글을 삭제할 권한이 없습니다");
    }

    @Test
    void deleteComment_NonExistingComment_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> commentService.deleteComment(1L, 1L, "username"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 댓글을 찾을 수 없습니다.");
    }

    @Test
    void deleteCommentForced_ValidRequest_DeletesComment() {
        when(commentRepository.existsById(1L)).thenReturn(true);

        commentService.deleteCommentForced(1L);
        verify(commentRepository).deleteById(1L);
    }

    @Test
    void deleteCommentForced_NonExistingComment_ThrowsResourceNotFoundException() {
        when(commentRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.deleteCommentForced(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 댓글을 찾을 수 없습니다.");
    }
}
