package com.study.board.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.study.board.dto.PostResponse;
import com.study.board.entity.Member;
import com.study.board.entity.Post;
import com.study.board.exception.ResourceNotFoundException;
import com.study.board.security.JwtAuthenticationFilter;
import com.study.board.security.SecurityConfig;
import com.study.board.service.PostService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = PostController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, SecurityConfig.class}
        )
)
class PostControllerTest {

    @MockitoBean
    private PostService postService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPost_FindExistsEntity_ReturnsCorrectEntity() throws Exception {
        when(postService.getPost(1L)).thenReturn(PostResponse.from(new Post("dummy", "dummy", new Member("dummy",
                "dummy",
                "dummy"))));

        mockMvc.perform(get("/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("dummy"));
    }

    @Test
    void getPost_FindNotExistsEntity_ThrowsResourceNotFoundException() throws Exception {
        when(postService.getPost(999L)).thenThrow(new ResourceNotFoundException(""));

        mockMvc.perform(get("/posts/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllPosts_ReturnsPostList() throws Exception {
        List<PostResponse> responses = List.of(
                PostResponse.from(new Post("dummy", "dummy", new Member("dummy",
                        "dummy",
                        "dummy"))));

        when(postService.getAllPosts(any(Pageable.class))).thenReturn(
                new PageImpl<>(responses, PageRequest.of(0, 20), responses.size()));

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("dummy"));
    }

    @Test
    void getAllPosts_NoPosts_ReturnsEmptyList() throws Exception {
        List<PostResponse> responses = List.of();

        when(postService.getAllPosts(any(Pageable.class))).thenReturn(
                new PageImpl<>(responses, PageRequest.of(0, 20), responses.size()));

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
