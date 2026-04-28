package com.study.board.init;

import com.study.board.entity.Member;
import com.study.board.entity.Role;
import com.study.board.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.password}")
    private String adminPassword;

    public AdminInitializer(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (memberRepository.existsByUsername("admin")) {
            return;
        }

        Member admin = new Member("admin", passwordEncoder.encode(adminPassword), "admin@admin.com", Role.ADMIN);
        memberRepository.save(admin);
    }
}
