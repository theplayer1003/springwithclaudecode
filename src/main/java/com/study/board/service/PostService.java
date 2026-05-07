package com.study.board.service;

import com.study.board.dto.PostCreateRequest;
import com.study.board.dto.PostResponse;
import com.study.board.dto.PostUpdateRequest;
import com.study.board.entity.Member;
import com.study.board.entity.Post;
import com.study.board.exception.ResourceNotFoundException;
import com.study.board.exception.UnauthorizedAccessException;
import com.study.board.repository.MemberRepository;
import com.study.board.repository.PostRepository;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    public PostService(PostRepository postRepository, MemberRepository memberRepository) {
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public PostResponse createPost(PostCreateRequest request, String username) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("해당 회원을 찾을 수 없습니다"));

        Post post = new Post(request.title(), request.content(), member);
        postRepository.save(post);

        return PostResponse.from(post);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getAllPosts(Pageable pageable) {
        return postRepository.findAllWithMember(pageable)
                .map(PostResponse::from);
    }

    @Cacheable("posts")
    @Transactional(readOnly = true)
    public PostResponse getPost(Long id) {
        return PostResponse.from(postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id + " 게시글을 찾을 수 없습니다.")));
    }

    @CacheEvict(value = "posts", key = "#id")
    @Transactional
    public PostResponse updatePost(Long id, PostUpdateRequest request, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id + " 게시글을 찾을 수 없습니다."));

        if (!username.equals(post.getAuthor())) {
            throw new UnauthorizedAccessException("게시글을 수정할 권한이 없습니다");
        }

        post.setTitle(request.title());
        post.setContent(request.content());

        return PostResponse.from(post);
    }

    @CacheEvict(value = "posts", key = "#id")
    @Transactional
    public void deletePost(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id + " 게시글을 찾을 수 없습니다."));

        if (!username.equals(post.getAuthor())) {
            throw new UnauthorizedAccessException("게시글을 삭제할 권한이 없습니다");
        }

        postRepository.deleteById(id);
    }

    @CacheEvict(value = "posts")
    @Transactional
    public void deletePostForced(Long id) {
        if (!postRepository.existsById(id)) {
            throw new ResourceNotFoundException(id + " 게시글을 찾을 수 없습니다.");
        }

        postRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> searchPosts(String keyword, Pageable pageable) {
        return postRepository.findByTitleContaining(keyword, pageable)
                .map(PostResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getAllPostsByMemberId(Pageable pageable, String username) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("해당 회원을 찾을 수 없습니다"));

        return postRepository.findByMemberIdWithMember(pageable, member.getId())
                .map(PostResponse::from);
    }
}
