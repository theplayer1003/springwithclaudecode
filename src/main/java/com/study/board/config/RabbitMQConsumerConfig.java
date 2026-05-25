package com.study.board.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이벤트 소비 측 RabbitMQ 설정.
 * <p>
 * 책임: - Queue 등록 (수신지점 정의) - Binding 등록 (어느 Exchange로부터 받을지)
 * <p>
 * Exchange는 Producer 측에서 등록한 빈을 주입받아 참조한다. (실제 시스템 분리 시 Consumer는 같은 이름으로 Exchange를 별도 declare하면 됨 — 멱등)
 */
@Configuration
public class RabbitMQConsumerConfig {

    public static final String EMAIL_QUEUE_NAME = "comment.email.queue";
    public static final String SMS_QUEUE_NAME = "comment.sms.queue";

    @Bean
    public Queue commentEmailQueue() {
        return new Queue(EMAIL_QUEUE_NAME);
    }

    @Bean
    public Queue commentSmsQueue() {
        return new Queue(SMS_QUEUE_NAME);
    }

    @Bean
    public Binding commentEmailBinding(FanoutExchange commentEventExchange,
                                       Queue commentEmailQueue) {
        return BindingBuilder.bind(commentEmailQueue).to(commentEventExchange);
    }

    @Bean
    public Binding commentSmsBinding(FanoutExchange commentEventExchange,
                                     Queue commentSmsQueue) {
        return BindingBuilder.bind(commentSmsQueue).to(commentEventExchange);
    }
}
