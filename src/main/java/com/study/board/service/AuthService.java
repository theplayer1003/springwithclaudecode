package com.study.board.service;

import com.study.board.dto.LoginRequest;
import com.study.board.dto.LoginResponse;
import com.study.board.dto.MemberResponse;
import com.study.board.dto.SignupRequest;
import com.study.board.entity.Member;
import com.study.board.exception.AuthenticationFailedException;
import com.study.board.exception.DuplicateUsernameException;
import com.study.board.repository.MemberRepository;
import com.study.board.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        if (memberRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException("이미 존재하는 아이디 입니다");
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        Member member = new Member(request.username(), encodedPassword, request.email());
        memberRepository.save(member);

        return MemberResponse.from(member);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByUsername(request.username())
                .orElseThrow(() -> new AuthenticationFailedException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new AuthenticationFailedException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtTokenProvider.createToken(member.getUsername(), member.getRole().name());

        return LoginResponse.from(token);
    }
}
