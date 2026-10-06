package com.stockpilot.messaging.config;

import com.stockpilot.messaging.domain.BusinessEventNames;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecovererWithConfirms;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingInfrastructureConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stockpilot.messaging", name = "enabled", havingValue = "true")
    static class RabbitEnabledConfiguration {
        @Bean
        TopicExchange businessExchange(MessagingProperties properties) {
            return new TopicExchange(properties.getExchange(), true, false);
        }

        @Bean
        Queue completionQueue(MessagingProperties properties) {
            return QueueBuilder.durable(properties.getCompletionQueue()).build();
        }

        @Bean
        Binding purchaseCompletionBinding(Queue completionQueue, TopicExchange businessExchange) {
            return BindingBuilder.bind(completionQueue)
                    .to(businessExchange)
                    .with(BusinessEventNames.PURCHASE_RECEIPT_ROUTING_KEY);
        }

        @Bean
        Binding salesCompletionBinding(Queue completionQueue, TopicExchange businessExchange) {
            return BindingBuilder.bind(completionQueue)
                    .to(businessExchange)
                    .with(BusinessEventNames.SALES_OUTBOUND_ROUTING_KEY);
        }

        @Bean
        Binding inventoryAvailabilityBinding(
                Queue completionQueue, TopicExchange businessExchange) {
            return BindingBuilder.bind(completionQueue)
                    .to(businessExchange)
                    .with(BusinessEventNames.AVAILABILITY_ROUTING_KEY);
        }

        @Bean
        DirectExchange deadLetterExchange(MessagingProperties properties) {
            return new DirectExchange(properties.getDeadLetterExchange(), true, false);
        }

        @Bean
        Queue deadLetterQueue(MessagingProperties properties) {
            return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
        }

        @Bean
        Binding deadLetterBinding(
                Queue deadLetterQueue,
                DirectExchange deadLetterExchange,
                MessagingProperties properties) {
            return BindingBuilder.bind(deadLetterQueue)
                    .to(deadLetterExchange)
                    .with(properties.getDeadLetterRoutingKey());
        }

        @Bean
        RepublishMessageRecovererWithConfirms completionRecoverer(
                RabbitTemplate rabbitTemplate, MessagingProperties properties) {
            RepublishMessageRecovererWithConfirms recoverer =
                    new RepublishMessageRecovererWithConfirms(
                            rabbitTemplate,
                            properties.getDeadLetterExchange(),
                            properties.getDeadLetterRoutingKey(),
                            CachingConnectionFactory.ConfirmType.CORRELATED);
            recoverer.setConfirmTimeout(properties.getPublisherConfirmTimeout().toMillis());
            return recoverer;
        }

        @Bean
        Advice completionRetryAdvice(
                RepublishMessageRecovererWithConfirms completionRecoverer,
                MessagingProperties properties) {
            return RetryInterceptorBuilder.stateless()
                    .maxAttempts(properties.getConsumerMaxAttempts())
                    .backOffOptions(
                            properties.getConsumerInitialBackoff().toMillis(),
                            2.0,
                            properties.getConsumerMaxBackoff().toMillis())
                    .recoverer(completionRecoverer)
                    .build();
        }

        @Bean("completionRabbitListenerContainerFactory")
        SimpleRabbitListenerContainerFactory completionRabbitListenerContainerFactory(
                SimpleRabbitListenerContainerFactoryConfigurer configurer,
                org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
                Advice completionRetryAdvice) {
            SimpleRabbitListenerContainerFactory factory =
                    new SimpleRabbitListenerContainerFactory();
            configurer.configure(factory, connectionFactory);
            factory.setAdviceChain(completionRetryAdvice);
            // A confirmed DLQ republish ends the retry interceptor normally. If the DLQ publish
            // itself
            // fails, requeue the original message instead of discarding the last recoverable copy.
            factory.setDefaultRequeueRejected(true);
            factory.setConcurrentConsumers(1);
            factory.setMaxConcurrentConsumers(1);
            return factory;
        }

        @Bean("deadLetterRabbitListenerContainerFactory")
        SimpleRabbitListenerContainerFactory deadLetterRabbitListenerContainerFactory(
                SimpleRabbitListenerContainerFactoryConfigurer configurer,
                org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
            SimpleRabbitListenerContainerFactory factory =
                    new SimpleRabbitListenerContainerFactory();
            configurer.configure(factory, connectionFactory);
            factory.setDefaultRequeueRejected(true);
            factory.setConcurrentConsumers(1);
            factory.setMaxConcurrentConsumers(1);
            return factory;
        }

        @Bean
        RabbitTemplateMandatoryCustomizer rabbitTemplateMandatoryCustomizer(
                RabbitTemplate rabbitTemplate) {
            rabbitTemplate.setMandatory(true);
            return new RabbitTemplateMandatoryCustomizer();
        }
    }

    static final class RabbitTemplateMandatoryCustomizer {}
}
