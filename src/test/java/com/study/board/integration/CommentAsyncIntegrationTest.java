//package com.study.board.integration;
//
//import static java.util.concurrent.TimeUnit.SECONDS;
//import static org.assertj.core.api.Assertions.*;
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.doAnswer;
//
//import com.study.board.dto.CommentCreateRequest;
//import com.study.board.dto.PostCreateRequest;
//import com.study.board.dto.PostResponse;
//import com.study.board.entity.Member;
//import com.study.board.repository.MemberRepository;
//import com.study.board.service.CommentNotificationService;
//import com.study.board.service.CommentNotificationServiceImpl;
//import com.study.board.service.CommentService;
//import com.study.board.service.PostService;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.Executor;
//import java.util.concurrent.atomic.AtomicReference;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.cache.CacheManager;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
//import org.springframework.test.util.AopTestUtils;
//
//@SpringBootTest
//public class CommentAsyncIntegrationTest {
//
//    private PostResponse postResponse;
//
//    @Autowired
//    private PostService postService;
//
//    @Autowired
//    private CommentService commentService;
//
//    @Autowired
//    private MemberRepository memberRepository;
//
//    @Autowired
//    private CacheManager cacheManager;
//
//    @MockitoSpyBean
//    @Qualifier("notificationExecutor")
//    private Executor notificationExecutor;
//
//    @BeforeEach
//    void setup() {
//        memberRepository.save(new Member("alice", "password", "alice@study.com"));
//        memberRepository.save(new Member("bob", "password", "bob@study.com"));
//        postResponse = postService.createPost(new PostCreateRequest("비동기 메서드 테스트 게시글", "테스트 게시글"),
//                "alice");
//    }
//
//    @AfterEach
//    void tearDown() {
//        cacheManager.getCacheNames()
//                .forEach(cacheName -> cacheManager.getCache(cacheName).clear());
//    }
//
//    @Test
//    void createComment_CallAsyncMethod_ThenAnotherThreadWork() throws InterruptedException {
//        CountDownLatch latch = new CountDownLatch(1);
//        AtomicReference<String> capturedThreadName = new AtomicReference<>();
//
////        doAnswer(invocation -> {
////            System.out.println(">>> doAnswer 진입 : " + Thread.currentThread().getName());
////            capturedThreadName.set(Thread.currentThread().getName());
////            invocation.callRealMethod();
////
////
////            latch.countDown();
////            System.out.println(">>> latch.countDown 완료");
////            return null;
////        }).when(commentNotificationService).notifyNewComment(any(), any(), any());
//
//        doAnswer(invocation -> {
//            Runnable runnable = invocation.getArgument(0);
//
//            notificationExecutor.execute(() -> {
//                try {
//                    System.out.println(">>> [비동기 스레드 진입] : " + Thread.currentThread().getName());
//                    capturedThreadName.set(Thread.currentThread().getName());
//
//                    runnable.run();
//                } finally {
//                    latch.countDown();
//                    System.out.println(">>> [비동기 스레드] latch.countDown 완료");
//                }
//            });
//            return null;
//        }).when(notificationExecutor).execute(any(Runnable.class));
//
//        System.out.println(">>> createComment 호출 전");
//        commentService.createComment(postResponse.id(), new CommentCreateRequest("content"), "bob");
//        System.out.println(">>> createComment 호출 후");
//
//        System.out.println(">>> commentNotificationService 클래스: " + notificationExecutor.getClass().getName());
//        System.out.println(">>> Mockito 인식 여부: " + org.mockito.Mockito.mockingDetails(notificationExecutor).isMock());
//        System.out.println(">>> spy 인식 여부: " + org.mockito.Mockito.mockingDetails(notificationExecutor).isSpy());
//
//        boolean awaited = latch.await(3, SECONDS);
//        System.out.println(">>> await 결과 : " + awaited);
//        assertThat(awaited).isTrue();
//
//        //assertThat(capturedThreadName.get()).contains("notify");
//
//        System.out.println(">>> test 종료");
//
//
//    }
//}
///*
//CommentNotificationService commentNotificationService;
//
//    @Autowired
//    private ThreadPoolTaskExecutor notificationExecutor;
// */


package com.study.board.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.study.board.dto.CommentCreateRequest;
import com.study.board.dto.PostCreateRequest;
import com.study.board.dto.PostResponse;
import com.study.board.entity.Member;
import com.study.board.repository.MemberRepository;
import com.study.board.service.CommentService;
import com.study.board.service.PostService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest
public class CommentAsyncIntegrationTest {

    private PostResponse postResponse;

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CacheManager cacheManager;

    // 멀티스레드 동기화 및 캡처를 위한 변수
    private static final CountDownLatch latch = new CountDownLatch(1);
    private static final AtomicReference<String> capturedThreadName = new AtomicReference<>();

    // [규칙 수정] @TestBean은 이와 같이 필드에 선언해야 컴파일됩니다.
    // 기존 컨텍스트에 등록된 notificationExecutor 빈을 아래의 팩토리 메서드(createTestExecutor) 결과물로 대체합니다.
    @TestBean(name = "notificationExecutor", methodName = "createTestExecutor")
    private ThreadPoolTaskExecutor notificationExecutor;

    // @TestBean과 연결될 정적(static) 팩토리 메서드 구현
    static ThreadPoolTaskExecutor createTestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setThreadNamePrefix("test-async-pool-");

        // 래치를 비동기 내부에서 안전하게 다운시키기 위해 데코레이터를 적용합니다.
        executor.setTaskDecorator(runnable -> () -> {
            try {
                // 1. 실제 비동기 스레드 풀 내부에서 도는 순간의 이름을 가로챕니다.
                System.out.println(">>> [진짜 비동기 스레드 진입] : " + Thread.currentThread().getName());
                capturedThreadName.set(Thread.currentThread().getName());

                // 2. 원래 서비스의 비즈니스 로직(1.5초 대기 및 메일 발송 로그)을 수행합니다.
                runnable.run();
            } finally {
                // 3. 정상 수행 또는 예외 발생 여부와 상관없이 안전하게 래치를 해제합니다.
                latch.countDown();
                System.out.println(">>> [비동기 스레드] latch.countDown 완료");
            }
        });

        executor.initialize();
        return executor;
    }

    @BeforeEach
    void setup() {
        memberRepository.save(new Member("alice", "password", "alice@study.com"));
        memberRepository.save(new Member("bob", "password", "bob@study.com"));
        postResponse = postService.createPost(new PostCreateRequest("비동기 테스트", "테스트"), "alice");
    }

    @AfterEach
    void tearDown() {
        cacheManager.getCacheNames()
                .forEach(cacheName -> cacheManager.getCache(cacheName).clear());
    }

    @Test
    void createComment_CallAsyncMethod_ThenAnotherThreadWork() throws InterruptedException {
        System.out.println(">>> createComment 호출 전 (현재 스레드: " + Thread.currentThread().getName() + ")");

        // 비즈니스 로직 작동 (상단에 명시한 커스텀 풀에 의해 동시성 격리 실행됨)
        commentService.createComment(postResponse.id(), new CommentCreateRequest("content"), "bob");

        System.out.println(">>> createComment 호출 후");

        // 비동기 스레드가 메일 발송 시나리오(1.5초)를 다 소화할 때까지 메인 테스트 스레드는 최대 3초 멈춰섭니다.
        boolean awaited = latch.await(3, SECONDS);
        System.out.println(">>> await 완료 결과 : " + awaited);

        // 검증 1: 3초 타임아웃 이전에 비동기 스레드가 로직을 무사히 마치고 카운트다운을 성사시켰는가?
        assertThat(awaited).isTrue();

        // 검증 2: 메인 스레드가 아닌, 새로 주입한 비동기 스레드 풀의 스레드가 일을 처리했는가?
        System.out.println(">>> 최종 캡처된 비동기 스레드 이름: " + capturedThreadName.get());
        assertThat(capturedThreadName.get()).contains("test-async-pool-");

        System.out.println(">>> test 종료");
    }
}
