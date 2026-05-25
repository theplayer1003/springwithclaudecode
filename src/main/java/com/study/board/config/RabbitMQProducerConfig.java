package com.study.board.config;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이벤트 발행 측 RabbitMQ 설정.
 * <p>
 * 책임:
 * - Exchange 등록 (발행지점 정의)
 * - 메시지 직렬화를 위한 MessageConverter
 * - 메시지 발행 도구 RabbitTemplate
 */
@Configuration
public class RabbitMQProducerConfig {

    public static final String EXCHANGE_NAME = "comment.event.exchange";

    @Bean
    public FanoutExchange commentEventExchange() {
        return new FanoutExchange(EXCHANGE_NAME);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
