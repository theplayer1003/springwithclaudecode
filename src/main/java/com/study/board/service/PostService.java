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
    public List<PostResponse> getAllPosts() {
        return postRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(PostResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(Long id) {
        return PostResponse.from(postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id + " 게시글을 찾을 수 없습니다.")));
    }

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

    @Transactional
    public void deletePost(Long id, String username) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id + " 게시글을 찾을 수 없습니다."));

        if (!username.equals(post.getAuthor())) {
            throw new UnauthorizedAccessException("게시글을 삭제할 권한이 없습니다");
        }

        postRepository.deleteById(id);
    }

    @Transactional
    public void deletePostForced(Long id) {
        if (!postRepository.existsById(id)) {
            throw new ResourceNotFoundException(id + " 게시글을 찾을 수 없습니다.");
        }

        postRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PostResponse> searchPosts(String keyword) {
        return postRepository.findByTitleContaining(keyword).stream()
                .map(PostResponse::from)
                .toList();
    }

}
