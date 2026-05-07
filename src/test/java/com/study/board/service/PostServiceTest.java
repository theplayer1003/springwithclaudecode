package com.study.board.service;

import static org.assertj.core.api.Assertions.*;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

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
        List<Post> posts = List.of(
                new Post("테스트 게시글1", "테스트 용입니다.", new Member("test", "test", "test")),
                new Post("테스트 게시글2", "테스트 용입니다.", new Member("test", "test", "test"))
        );
        when(postRepository.findByTitleContaining("dummy", PageRequest.of(0, 20))).thenReturn(
                new PageImpl<>(posts));

        Page<PostResponse> result = postService.searchPosts("dummy", PageRequest.of(0, 20));

        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(2);
        List<PostResponse> list = result.get().toList();

        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).title()).isEqualTo("테스트 게시글1");
        assertThat(list.get(1).title()).isEqualTo("테스트 게시글2");
    }

    @Test
    void searchPosts_NoMatchingKeyword_ReturnsEmptyList() {
        List<Post> posts = List.of();
        when(postRepository.findByTitleContaining("dummy", PageRequest.of(0, 20))).thenReturn(
                new PageImpl<>(posts)
        );

        Page<PostResponse> postResponses = postService.searchPosts("dummy", PageRequest.of(0, 20));

        assertThat(postResponses.getTotalPages()).isEqualTo(1);
        assertThat(postResponses.getTotalElements()).isEqualTo(0);
        List<PostResponse> result = postResponses.get().toList();

        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    void getAllPostsByMemberId_ExistingUsername_ReturnsAllPosts() {
        Member member = new Member("testUser", "test", "test");
        ReflectionTestUtils.setField(member, "id", 1L);
        Post post = new Post("testTitle", "testContent", member);
        List<Post> posts = List.of(post);

        when(memberRepository.findByUsername("test")).thenReturn(Optional.of(member));
        when(postRepository.findByMemberIdWithMember(PageRequest.of(0, 20), member.getId())).thenReturn(
                new PageImpl<>(posts, PageRequest.of(0, 20), posts.size())
        );

        Page<PostResponse> result = postService.getAllPostsByMemberId(PageRequest.of(0, 20), "test");

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        final PostResponse postResponse = result.get().toList().getFirst();
        assertThat(postResponse.author()).isEqualTo("testUser");
        assertThat(postResponse.title()).isEqualTo("testTitle");
        assertThat(postResponse.content()).isEqualTo("testContent");
    }

    @Test
    void getAllPostsByMemberId_NonExistingUsername_ThrowsResourceNotFoundException() {

        assertThatThrownBy(() -> postService.getAllPostsByMemberId(PageRequest.of(0, 20), "NonExistingUsername"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("해당 회원을 찾을 수 없습니다");
    }
}
