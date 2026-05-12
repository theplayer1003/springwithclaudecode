package com.study.board.integration;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.board.dto.PostCreateRequest;
import com.study.board.dto.PostResponse;
import com.study.board.dto.PostUpdateRequest;
import com.study.board.entity.Member;
import com.study.board.exception.ResourceNotFoundException;
import com.study.board.exception.UnauthorizedAccessException;
import com.study.board.repository.MemberRepository;
import com.study.board.service.PostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
class PostIntegrationTest {

    private PostResponse postResponse;

    @Autowired
    private PostService postService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setup() {
        memberRepository.save(new Member("username", "password", "user@study.com"));

        postResponse = postService.createPost(new PostCreateRequest("test", "테스트용 게시글입니다."),
                "username");
    }

    @AfterEach
    void tearDown() {
        cacheManager.getCacheNames()
                .forEach(cacheName -> cacheManager.getCache(cacheName).clear());
    }

    @Test
    void getPost_ExistingId_ReturnsCorrectEntity() {
        Long id = postResponse.id();

        PostResponse post = postService.getPost(id);

        assertThat(post.id()).isEqualTo(id);
        assertThat(post.content()).isEqualTo("테스트용 게시글입니다.");
        assertThat(post.author()).isEqualTo("username");
    }

    @Test
    void getPost_NonExistingId_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> postService.getPost(postResponse.id() + 1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining((postResponse.id() + 1) + " 게시글을 찾을 수 없습니다.");
    }

    @Test
    void updatePost_NonExistingId_ThrowsResourceNotFoundException() {
        assertThatThrownBy(
                () -> postService.updatePost(postResponse.id() + 1, new PostUpdateRequest("수정하려는 제목", "수정하려는 내용"),
                        "username"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining((postResponse.id() + 1) + " 게시글을 찾을 수 없습니다.");
    }

    @Test
    void updatePost_ByAuthor_UpdatePost() {
        Long id = postResponse.id();
        postService.updatePost(id, new PostUpdateRequest("수정하려는 제목", "수정하려는 내용"),
                "username");

        assertThat(postService.getPost(id).title()).isEqualTo("수정하려는 제목");
        assertThat(postService.getPost(id).content()).isEqualTo("수정하려는 내용");
    }

    @Test
    void updatePost_ByNonAuthor_ThrowsUnauthorizedAccessException() {
        Long id = postResponse.id();

        assertThatThrownBy(() -> postService.updatePost(id, new PostUpdateRequest("수정하려는 제목", "수정하려는 내용"),
                "username1234"))
                .isInstanceOf(UnauthorizedAccessException.class);
    }
}
