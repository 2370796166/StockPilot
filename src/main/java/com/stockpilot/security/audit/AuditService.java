package com.stockpilot.security.audit;

import com.stockpilot.security.domain.AuditLogEntity;
import com.stockpilot.security.mapper.AuditLogMapper;
import com.stockpilot.shared.auth.AuthenticatedActor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(
            String action, String objectType, Object objectId, String result, String summary) {
        AuditLogEntity e = new AuditLogEntity();
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthenticatedActor p) {
            e.setOperatorId(p.userId());
            e.setOperatorName(p.username());
        }
        e.setActionType(action);
        e.setObjectType(objectType);
        e.setObjectId(objectId == null ? null : String.valueOf(objectId));
        e.setResult(result);
        e.setSummary(safe(summary));
        auditLogMapper.insert(e);
    }

    public void recordLogin(Long userId, String username, String result, String reason) {
        AuditLogEntity e = new AuditLogEntity();
        e.setOperatorId(userId);
        e.setOperatorName(username);
        e.setActionType("LOGIN");
        e.setObjectType("USER");
        e.setObjectId(userId == null ? null : String.valueOf(userId));
        e.setResult(result);
        e.setSummary(safe(reason));
        auditLogMapper.insert(e);
    }

    private static String safe(String s) {
        if (s == null) return null;
        String value =
                s.replaceAll("(?i)(password|token|secret)\\s*[=:]\\s*[^,;\\s]+", "$1=[REDACTED]");
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
