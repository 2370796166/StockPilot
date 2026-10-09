package com.stockpilot.ai.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record AiAnswerVO(
        String status,
        String answer,
        List<Evidence> results,
        Instant queriedAt,
        String continuationToken) {
    public AiAnswerVO(String status, String answer, List<Evidence> results, Instant queriedAt) {
        this(status, answer, results, queriedAt, null);
    }

    public record Candidate(String kind, String keyword, Long id, String code, String name) {}

    public record Source(String label, String path, String authority) {}

    public record Evidence(
            String tool,
            String status,
            String message,
            JsonNode data,
            List<Candidate> candidates,
            List<Source> sources,
            Instant queriedAt,
            String evidenceId) {
        public Evidence(
                String tool,
                String status,
                String message,
                JsonNode data,
                List<Candidate> candidates,
                List<Source> sources,
                Instant queriedAt) {
            this(tool, status, message, data, candidates, sources, queriedAt, null);
        }

        public Evidence identified(String id) {
            return new Evidence(tool, status, message, data, candidates, sources, queriedAt, id);
        }
    }
}
