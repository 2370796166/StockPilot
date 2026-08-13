package com.stockpilot.security.audit;
import com.stockpilot.security.auth.StockPilotPrincipal; import com.stockpilot.security.domain.AuditLogEntity; import com.stockpilot.security.infrastructure.mapper.AuditLogMapper;
import org.springframework.security.core.Authentication; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Service;
@Service public class AuditService {
 private final AuditLogMapper mapper; public AuditService(AuditLogMapper mapper){this.mapper=mapper;}
 public void record(String action,String objectType,Object objectId,String result,String summary){AuditLogEntity e=new AuditLogEntity();Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a!=null&&a.getPrincipal() instanceof StockPilotPrincipal p){e.setOperatorId(p.userId());e.setOperatorName(p.username());}e.setActionType(action);e.setObjectType(objectType);e.setObjectId(objectId==null?null:String.valueOf(objectId));e.setResult(result);e.setSummary(safe(summary));mapper.insert(e);}
 public void recordLogin(Long userId,String username,String result,String reason){AuditLogEntity e=new AuditLogEntity();e.setOperatorId(userId);e.setOperatorName(username);e.setActionType("LOGIN");e.setObjectType("USER");e.setObjectId(userId==null?null:String.valueOf(userId));e.setResult(result);e.setSummary(safe(reason));mapper.insert(e);}
 private static String safe(String s){if(s==null)return null;String value=s.replaceAll("(?i)(password|token|secret)\\s*[=:]\\s*[^,;\\s]+","$1=[REDACTED]");return value.length()>500?value.substring(0,500):value;}
}
