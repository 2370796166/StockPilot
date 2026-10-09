package com.stockpilot.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.service.*;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AiReadOnlyToolExecutorTest {
    @Test
    void aliasLookupUsesRemainingReadOnlyBudgetAndSkipsNonCodes() {
        var manager = mock(PlatformTransactionManager.class);
        var tools = mock(AiToolService.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(tools.mentionedName("warehouse", "W1", "一号仓")).thenReturn("一号仓");
        var executor = new AiReadOnlyToolExecutor(tools, manager);
        assertEquals(
                "一号仓", executor.mentionedName("warehouse", "W1", "一号仓", Duration.ofMillis(50)));
        var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager).getTransaction(definition.capture());
        assertTrue(definition.getValue().isReadOnly());
        assertEquals(1, definition.getValue().getTimeout());
        clearInvocations(manager);
        assertNull(executor.mentionedName("warehouse", "默认仓库", "查询库存", Duration.ofSeconds(2)));
        verifyNoInteractions(manager);
    }

    @Test
    void remainingBudgetBoundsTheWholeReadOnlySnapshot() {
        for (Duration remaining :
                List.of(Duration.ofMillis(50), Duration.ofSeconds(2), Duration.ofSeconds(90))) {
            var manager = mock(PlatformTransactionManager.class);
            var tools = mock(AiToolService.class);
            when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            var data = new ObjectMapper().createObjectNode();
            Evidence expected =
                    new Evidence(
                            "query_balances",
                            "OK",
                            "查询完成",
                            data,
                            List.of(),
                            List.of(),
                            Instant.now());
            when(tools.execute(any(), any(), any())).thenReturn(expected);
            assertSame(
                    expected,
                    new AiReadOnlyToolExecutor(tools, manager)
                            .execute("query_balances", data, List.of(), remaining));
            var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
            verify(manager).getTransaction(definition.capture());
            assertTrue(definition.getValue().isReadOnly());
            assertEquals(
                    TransactionDefinition.ISOLATION_REPEATABLE_READ,
                    definition.getValue().getIsolationLevel());
            assertEquals(
                    Math.min(10, Math.max(1, (remaining.toMillis() + 999) / 1000)),
                    definition.getValue().getTimeout());
            verify(manager).commit(any());
        }
    }

    @Test
    void missingDocumentStatusSurvivesANestedReadRollback() {
        var manager = mock(PlatformTransactionManager.class);
        var transactionStatus = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transactionStatus);
        doAnswer(
                        invocation -> {
                            if (!transactionStatus.isRollbackOnly())
                                throw new UnexpectedRollbackException(
                                        "Nested read marked rollback");
                            return null;
                        })
                .when(manager)
                .commit(transactionStatus);
        var tools = mock(AiToolService.class);
        var data = new ObjectMapper().createObjectNode();
        Evidence missing =
                new Evidence(
                        "get_document",
                        "NO_DATA",
                        "未找到匹配记录，不能视为库存为零",
                        data,
                        List.of(),
                        List.of(),
                        Instant.now());
        when(tools.execute(any(), any(), any())).thenReturn(missing);
        assertSame(
                missing,
                new AiReadOnlyToolExecutor(tools, manager)
                        .execute("get_document", data, List.of(), Duration.ofSeconds(10)));
        assertTrue(transactionStatus.isRollbackOnly());
        verify(manager).commit(transactionStatus);
    }

    @Test
    void databaseFailureDoesNotBecomeAModelErrorOrExposeSql() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any()))
                .thenThrow(new IllegalStateException("private database credentials"));
        var tools = mock(AiToolService.class);
        var result =
                new AiReadOnlyToolExecutor(tools, manager)
                        .execute(
                                "query_balances",
                                new ObjectMapper().createObjectNode(),
                                List.of(),
                                Duration.ofSeconds(10));
        assertEquals("QUERY_FAILED", result.status());
        assertFalse(result.message().contains("credentials"));
        verifyNoInteractions(tools);
    }
}
