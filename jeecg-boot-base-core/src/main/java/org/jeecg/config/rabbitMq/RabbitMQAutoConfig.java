package org.jeecg.config.rabbitMq;

import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * rabbitmq配置类
 * @author yangkun
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.rabbitmq", name = "exchange")
public class RabbitMQAutoConfig {

    @Value("${spring.rabbitmq.exchange}")
    private String exchange;

    @Bean
    public Exchange topicExchange() {
        return new TopicExchange(exchange, true, false);
    }

}
