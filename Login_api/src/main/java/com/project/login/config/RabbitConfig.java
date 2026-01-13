package com.project.login.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // --- 连接工厂 ---
    @Bean
    public CachingConnectionFactory rabbitConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory("localhost", 5672);
        factory.setUsername("admin");
        factory.setPassword("admin");
        return factory;
    }

    // --- RabbitTemplate ---
    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter()); // 使用 JSON
        return template;
    }

    // --- RabbitAdmin ---
    @Bean
    public RabbitAdmin rabbitAdmin(CachingConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    // --- JSON 消息转换器 ---
    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---------------- 队列 ----------------
    @Bean
    public Queue noteRedisQueue() {
        return new Queue("note.redis.queue", true);
    }

    @Bean
    public Queue noteEsQueue() {
        return new Queue("note.es.queue", true);
    }

    @Bean
    public Queue noteMongoQueue() {
        return new Queue("note.mongo.queue", true);
    }

    public static final String USER_BEHAVIOR_QUEUE = "user_behavior_queue";
    @Bean
    public Queue userBehaviorQueue() {
        return new Queue(USER_BEHAVIOR_QUEUE, true); // durable queue
    }

    @Bean
    public Queue questionEsQueue() {
        return new Queue("question.es.queue", true); // durable queue
    }
    @Bean
    public Queue remarkLikeUsersQueue() {
        return new Queue("remarkLikeUsers.redis.queue", true); // true 表示持久化
    }

    @Bean
    public Queue remarkLikeCountQueue() {
        return new Queue("remarkLikeCount.redis.queue", true);
    }

    public static final String ACTIVE_TOKEN_QUEUE = "active.token.queue";
    @Bean
    public Queue activeTokenQueue() {
        return new Queue(ACTIVE_TOKEN_QUEUE, true);
    }

    @Bean
    public Queue noteAuditQueue() {
        return new Queue("note.audit.queue", true);
    }

    @Bean
    public Queue noteModerationAlertQueue() {
        return new Queue("note.moderation.alert.queue", true);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory auditListenerFactory(ConnectionFactory connectionFactory,
                                                                     Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        factory.setMessageConverter(converter);
        return factory;
    }
}
