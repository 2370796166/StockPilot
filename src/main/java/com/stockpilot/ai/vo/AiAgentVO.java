package com.stockpilot.ai.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AiAgentVO {
    private AiAgentVO() {}

    public record Fact(String evidenceId, String field, String value) {}

    public record Conclusion(
            String rule,
            String text,
            List<Fact> facts,
            String evidenceId,
            Map<String, String> scope,
            Instant queriedAt,
            String coverage,
            List<String> evidenceIds) {
        public Conclusion(
                String rule,
                String text,
                List<Fact> facts,
                String evidenceId,
                Map<String, String> scope,
                Instant queriedAt,
                String coverage) {
            this(rule, text, facts, evidenceId, scope, queriedAt, coverage, List.of(evidenceId));
        }
    }

    public record Answer(
            List<Conclusion> conclusions, List<String> uncertainties, List<String> suggestions) {}

    public record Turn(String taskId, String question, String status, String answer, Instant at) {}

    public record Step(String label, String status, String evidenceId, Instant at) {}

    public record Session(
            String id, Map<String, String> context, List<Turn> history, Instant expiresAt) {}

    public record Task(
            String id,
            String sessionId,
            long version,
            String status,
            String reason,
            String progress,
            String question,
            List<AiAnswerVO.Evidence> results,
            List<AiAnswerVO.Evidence> previousResults,
            Answer answer,
            Map<String, String> conditions,
            List<String> missingFields,
            List<Step> steps,
            Map<String, String> conditionSources,
            Instant updatedAt,
            int modelCalls,
            int toolCalls) {}
}
