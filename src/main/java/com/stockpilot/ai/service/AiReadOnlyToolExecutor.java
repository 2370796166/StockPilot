package com.stockpilot.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockpilot.ai.request.AiQuestionRequest.Selection;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** A short database snapshot covers the whole tool, including name/reference resolution. */
@Service
public class AiReadOnlyToolExecutor {
    private final AiToolService aiToolService;
    private final PlatformTransactionManager transactionManager;

    public AiReadOnlyToolExecutor(
            AiToolService aiToolService, PlatformTransactionManager transactionManager) {
        this.aiToolService = aiToolService;
        this.transactionManager = transactionManager;
    }

    public Evidence execute(
            String name, JsonNode args, List<Selection> selections, Duration remaining) {
        TransactionTemplate transaction = snapshot(remaining);
        try {
            return transaction.execute(
                    status -> {
                        Evidence result = aiToolService.execute(name, args, selections);
                        // A nested read service may mark this snapshot rollback-only before its
                        // business exception becomes evidence (e.g. a missing document). Roll back
                        // deliberately so commit cannot replace NO_DATA with QUERY_FAILED.
                        if (!"OK".equals(result.status())) status.setRollbackOnly();
                        return result;
                    });
        } catch (RuntimeException e) {
            return new Evidence(
                    name,
                    "QUERY_FAILED",
                    "业务查询失败，未得到有效数据",
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
                    List.of(),
                    List.of(),
                    Instant.now());
        }
    }

    public String mentionedName(String kind, String code, String question, Duration remaining) {
        if (!java.util.Set.of("sku", "warehouse").contains(kind)
                || !code.matches("[A-Za-z0-9_-]{1,64}")) return null;
        return snapshot(remaining)
                .execute(status -> aiToolService.mentionedName(kind, code, question));
    }

    private TransactionTemplate snapshot(Duration remaining) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // JDBC transaction timeouts have second resolution; round up, cap each tool at ten seconds.
        transaction.setTimeout(
                (int) Math.max(1, Math.min(10, (remaining.toMillis() + 999) / 1000)));
        return transaction;
    }
}
