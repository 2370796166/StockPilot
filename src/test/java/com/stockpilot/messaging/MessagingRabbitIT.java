package com.stockpilot.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.StockPilotApplication;
import com.stockpilot.acceptance.IntegrationTestInfrastructure;
import com.stockpilot.inventory.domain.InventoryAvailabilityChanged;
import com.stockpilot.messaging.config.MessagingProperties;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.service.CompletionEventConsumptionApplicationService;
import com.stockpilot.messaging.service.OutboxPublicationApplicationService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = MessagingRabbitIT.TestApplication.class)
@ContextConfiguration(initializers = MessagingRabbitIT.InfrastructureInitializer.class)
class MessagingRabbitIT {
    private static final String DATABASE =
            IntegrationTestInfrastructure.databaseName("stockpilot_rabbit_it");
    private static final String ADMIN_URL =
            System.getenv()
                    .getOrDefault(
                            "STOCKPILOT_IT_ADMIN_URL",
                            "jdbc:mysql://localhost:3307/?allowPublicKeyRetrieval=true&useSSL=false");
    private static final String ADMIN_USER =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_USER", "root");
    private static final String ADMIN_PASSWORD =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_PASSWORD", "root_dev_only");

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired RabbitAdmin rabbitAdmin;
    @Autowired MessagingProperties properties;
    @Autowired OutboxPublicationApplicationService publications;
    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate transactions;

    @Autowired
    @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange;

    @Autowired
    @Qualifier("deadLetterBinding") Binding deadLetterBinding;

    @MockBean CompletionEventConsumptionApplicationService completionEventConsumption;

    @BeforeEach
    void clean() {
        reset(completionEventConsumption);
        rabbitAdmin.declareExchange(deadLetterExchange);
        rabbitAdmin.declareBinding(deadLetterBinding);
        rabbitAdmin.purgeQueue(properties.getCompletionQueue(), false);
        rabbitAdmin.purgeQueue(properties.getDeadLetterQueue(), false);
        jdbc.update("DELETE FROM async_failure_record");
        jdbc.update("DELETE FROM async_message_trace");
        jdbc.update("DELETE FROM async_outbox_message");
    }

    @AfterAll
    static void dropDatabase() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
        }
    }

    @Test
    void outboxPublishesWithConfirmAndConsumerReceivesVersionedContent() throws Exception {
        CompletionBusinessEvent event = insertOutbox("SO-RABBIT-NORMAL");
        when(completionEventConsumption.handle(any())).thenReturn(true);
        assertTrue(publications.publishNextDue());

        verify(completionEventConsumption, org.mockito.Mockito.timeout(10_000)).handle(event);
        assertEquals(
                "PUBLISHED",
                jdbc.queryForObject(
                        "SELECT status FROM async_outbox_message WHERE message_id=?",
                        String.class,
                        event.messageId()));
    }

    @Test
    void temporaryConsumerFailureRetriesThenSucceeds() throws Exception {
        CompletionBusinessEvent event = insertOutbox("SO-RABBIT-RETRY");
        when(completionEventConsumption.handle(any()))
                .thenThrow(new IllegalStateException("temporary-1"))
                .thenThrow(new IllegalStateException("temporary-2"))
                .thenReturn(true);
        assertTrue(publications.publishNextDue());

        verify(completionEventConsumption, org.mockito.Mockito.timeout(15_000).times(3))
                .handle(event);
        await(
                () ->
                        count(
                                        "SELECT COUNT(*) FROM async_message_trace WHERE message_id='"
                                                + event.messageId()
                                                + "' AND stage='CONSUME_RETRY'")
                                >= 2,
                10_000);
    }

    @ParameterizedTest
    @EnumSource(InventoryAvailabilityChanged.Action.class)
    void everyAvailabilityEventIsRoutedThroughTheRealBroker(
            InventoryAvailabilityChanged.Action action) throws Exception {
        var change =
                new InventoryAvailabilityChanged(
                        action,
                        1L,
                        "AVAIL-" + action.name(),
                        2L,
                        List.of(new InventoryAvailabilityChanged.Dimension(3L, 4L)));
        transactions.executeWithoutResult(status -> events.publishEvent(change));
        var event =
                json.readValue(
                        jdbc.queryForObject(
                                "SELECT payload_json FROM async_outbox_message", String.class),
                        CompletionBusinessEvent.class);
        assertEquals(BusinessEventNames.availabilityEventName(action), event.eventName());
        assertEquals(
                BusinessEventNames.AVAILABILITY_ROUTING_KEY,
                jdbc.queryForObject("SELECT routing_key FROM async_outbox_message", String.class));
        when(completionEventConsumption.handle(any())).thenReturn(true);
        assertTrue(publications.publishNextDue());
        verify(completionEventConsumption, org.mockito.Mockito.timeout(10000)).handle(event);
        assertEquals(
                "PUBLISHED",
                jdbc.queryForObject("SELECT status FROM async_outbox_message", String.class));
    }

    @Test
    void permanentConsumerFailureIsRepublishedToDeadLetterAndRecorded() throws Exception {
        CompletionBusinessEvent event = insertOutbox("SO-RABBIT-DEAD");
        when(completionEventConsumption.handle(any()))
                .thenThrow(new IllegalArgumentException("permanent failure"));
        assertTrue(publications.publishNextDue());

        verify(completionEventConsumption, org.mockito.Mockito.timeout(15_000).times(3))
                .handle(event);
        await(
                () ->
                        count(
                                        "SELECT COUNT(*) FROM async_failure_record WHERE message_id='"
                                                + event.messageId()
                                                + "' AND failure_stage='CONSUME'")
                                == 1,
                15_000);
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM async_message_trace WHERE message_id='"
                                + event.messageId()
                                + "' AND stage='DEAD_LETTERED'"));
    }

    @Test
    void deadLetterPublishFailureRequeuesOriginalUntilTopologyRecovers() throws Exception {
        CompletionBusinessEvent event = insertOutbox("SO-RABBIT-DLQ-OUTAGE");
        when(completionEventConsumption.handle(any()))
                .thenThrow(new IllegalArgumentException("permanent failure"));
        rabbitAdmin.deleteExchange(properties.getDeadLetterExchange());

        assertTrue(publications.publishNextDue());
        verify(completionEventConsumption, org.mockito.Mockito.timeout(15_000).atLeast(6))
                .handle(event);
        assertEquals(
                0,
                count(
                        "SELECT COUNT(*) FROM async_failure_record WHERE message_id='"
                                + event.messageId()
                                + "'"));

        rabbitAdmin.declareExchange(deadLetterExchange);
        rabbitAdmin.declareBinding(deadLetterBinding);
        await(
                () ->
                        count(
                                        "SELECT COUNT(*) FROM async_failure_record WHERE message_id='"
                                                + event.messageId()
                                                + "' AND failure_stage='CONSUME'")
                                == 1,
                15_000);
    }

    @Test
    void unsupportedEventVersionIsRejectedAndRecordedInsteadOfCallingOldConsumer()
            throws Exception {
        CompletionBusinessEvent event =
                new CompletionBusinessEvent(
                        UUID.randomUUID().toString(),
                        BusinessEventNames.SALES_OUTBOUND_COMPLETED,
                        2,
                        "SO-RABBIT-V2",
                        Instant.now(),
                        new CompletionBusinessEvent.CompletionData(
                                1L,
                                2L,
                                List.of(new CompletionBusinessEvent.InventoryDimension(3L, 4L))));
        insertOutbox(event);

        assertTrue(publications.publishNextDue());
        await(
                () ->
                        count(
                                        "SELECT COUNT(*) FROM async_failure_record WHERE message_id='"
                                                + event.messageId()
                                                + "' AND event_version=2 AND failure_stage='CONSUME'")
                                == 1,
                15_000);
        verify(completionEventConsumption, never()).handle(any());
    }

    private CompletionBusinessEvent insertOutbox(String businessNo) throws Exception {
        CompletionBusinessEvent event =
                new CompletionBusinessEvent(
                        UUID.randomUUID().toString(),
                        BusinessEventNames.SALES_OUTBOUND_COMPLETED,
                        1,
                        businessNo,
                        Instant.now(),
                        new CompletionBusinessEvent.CompletionData(
                                1L,
                                2L,
                                List.of(new CompletionBusinessEvent.InventoryDimension(3L, 4L))));
        insertOutbox(event);
        return event;
    }

    private void insertOutbox(CompletionBusinessEvent event) throws Exception {
        jdbc.update(
                """
                INSERT INTO async_outbox_message
                  (message_id,event_name,event_version,business_no,routing_key,payload_json,status,publish_attempts,next_attempt_at)
                VALUES (?,?,?,?,?,CAST(? AS JSON),'PENDING',0,CURRENT_TIMESTAMP(3))
                """,
                event.messageId(),
                event.eventName(),
                event.eventVersion(),
                event.businessNo(),
                BusinessEventNames.SALES_OUTBOUND_ROUTING_KEY,
                json.writeValueAsString(event));
    }

    private void await(BooleanSupplier condition, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        assertTrue(condition.getAsBoolean(), "condition was not met before timeout");
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    static class InfrastructureInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            IntegrationTestInfrastructure.isolate(context, DATABASE);
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context, IntegrationTestInfrastructure.rabbitProperties(DATABASE));
            try (Connection connection =
                            DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                    Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
                statement.execute(
                        "CREATE DATABASE "
                                + DATABASE
                                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Cannot prepare RabbitMQ integration-test database", exception);
            }
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "spring.datasource.url="
                            + IntegrationTestInfrastructure.databaseUrl(ADMIN_URL, DATABASE),
                    "spring.datasource.username=" + ADMIN_USER,
                    "spring.datasource.password=" + ADMIN_PASSWORD,
                    "stockpilot.messaging.enabled=true",
                    "stockpilot.messaging.publisher-fixed-delay=3600000",
                    "stockpilot.messaging.consumer-initial-backoff=50ms",
                    "stockpilot.messaging.consumer-max-backoff=100ms",
                    "stockpilot.security.jwt-secret=01234567890123456789012345678901");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = "com.stockpilot",
            excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = StockPilotApplication.class),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com\\.stockpilot\\.security\\.TestProtectedController")
            })
    static class TestApplication {}
}
