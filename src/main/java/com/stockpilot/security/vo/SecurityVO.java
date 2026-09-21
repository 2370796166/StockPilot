package com.stockpilot.security.vo;

import com.stockpilot.security.domain.SecurityStatus;
import java.time.*;
import java.util.List;

public final class SecurityVO {
    private SecurityVO() {}

    public record Token(String accessToken, String tokenType, Instant expiresAt) {}

    public record CurrentUser(
            Long userId,
            String username,
            String displayName,
            List<String> roles,
            List<String> authorities) {}

    public record User(
            Long id,
            String username,
            String displayName,
            SecurityStatus status,
            List<Long> roleIds,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Integer version) {}

    public record Role(
            Long id,
            String code,
            String name,
            SecurityStatus status,
            List<Long> permissionIds,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Integer version) {}

    public record Permission(
            Long id,
            String code,
            String name,
            String description,
            SecurityStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Integer version) {}

    public record Audit(
            Long id,
            Long operatorId,
            String operatorName,
            String actionType,
            String objectType,
            String objectId,
            String result,
            String summary,
            LocalDateTime occurredAt) {}
}
