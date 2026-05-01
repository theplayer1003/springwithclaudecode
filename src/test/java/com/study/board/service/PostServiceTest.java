package com.study.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.study.board.dto.PostResponse;
import com.study.board.entity.Member;
import com.study.board.entity.Post;
import com.study.board.exception.ResourceNotFoundException;
import com.study.board.exception.UnauthorizedAccessException;
import com.study.board.repository.MemberRepository;
import com.study.board.repository.PostRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private PostService postService;

    @Test
    void getPost_FindNotExistsEntity_ThrowsResourceNotFoundException() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPost(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("게시글을 찾을 수 없습니다.");
    }

    @Test
    void getPost_FindExistsEntity_ReturnCorrectEntity() {
        final Optional<Post> post = Optional.of(
                new Post("dummy",
                        "dummy",
                        new Member("dummy",
                                "dummy",
                                "dummy")));
        when(postRepository.findById(1L)).thenReturn(post);

        PostResponse response = postService.getPost(1L);

        assertThat(response.title()).isEqualTo("dummy");
        assertThat(response.content()).isEqualTo("dummy");
        assertThat(response.author()).isEqualTo("dummy");
    }

    @Test
    void deletePost_ExistingId_DeleteCorrectPost() {
        final Optional<Post> post = Optional.of(
                new Post("dummy",
                        "dummy",
                        new Member("dummy",
                                "dummy",
                                "dummy")));
        when(postRepository.findById(1L)).thenReturn(post);

        postService.deletePost(1L, "dummy");
        verify(postRepository).deleteById(1L);
    }

    @Test
    void deletePost_NonExistingId_ThrowsResourceNotFoundException() {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.deletePost(999L, "dummy"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 게시글을 찾을 수 없습니다.");
    }

    @Test
    void deletePost_ByNonAuthor_ThrowsUnauthorizedAccessException() {
        final Optional<Post> post = Optional.of(
                new Post("dummy",
                        "dummy",
                        new Member("dummy",
                                "dummy",
                                "dummy")));
        when(postRepository.findById(1L)).thenReturn(post);

        assertThatThrownBy(() -> postService.deletePost(1L, "nonAuthor"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("게시글을 삭제할 권한이 없습니다");
    }

    @Test
    void deletePostForced_ExistingId_DeleteCorrectPost() {
        when(postRepository.existsById(1L)).thenReturn(true);

        postService.deletePostForced(1L);
        verify(postRepository).deleteById(1L);
    }

    @Test
    void deletePostForced_NonExistingId_ThrowsResourceNotFoundException() {
        when(postRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> postService.deletePostForced(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(" 게시글을 찾을 수 없습니다.");
    }

    @Test
    void searchPosts_MatchingKeyword_ReturnsMatchedPosts() {
        when(postRepository.findByTitleContaining("dummy")).thenReturn(List.of(
                new Post("테스트 게시글1", "테스트 용입니다.", new Member("test", "test", "test")),
                new Post("테스트 게시글2", "테스트 용입니다.", new Member("test", "test", "test"))
        ));

        List<PostResponse> postResponses = postService.searchPosts("dummy");

        assertThat(postResponses.size()).isEqualTo(2);
        assertThat(postResponses.get(0).title()).isEqualTo("테스트 게시글1");
        assertThat(postResponses.get(1).title()).isEqualTo("테스트 게시글2");
    }

    @Test
    void searchPosts_NoMatchingKeyword_ReturnsEmptyList() {
        when(postRepository.findByTitleContaining("dummy")).thenReturn(List.of());

        List<PostResponse> postResponses = postService.searchPosts("dummy");

        assertThat(postResponses.size()).isEqualTo(0);
    }
}
