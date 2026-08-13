package com.stockpilot.security.domain;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime;
@TableName("audit_log") public class AuditLogEntity {
 @TableId(type=IdType.AUTO) private Long id; private Long operatorId; private String operatorName; private String actionType; private String objectType; private String objectId; private String result; private String summary;
 @TableField(insertStrategy=FieldStrategy.NEVER,updateStrategy=FieldStrategy.NEVER) private LocalDateTime occurredAt;
 public Long getId(){return id;} public void setOperatorId(Long v){operatorId=v;} public Long getOperatorId(){return operatorId;} public void setOperatorName(String v){operatorName=v;} public String getOperatorName(){return operatorName;}
 public void setActionType(String v){actionType=v;} public String getActionType(){return actionType;} public void setObjectType(String v){objectType=v;} public String getObjectType(){return objectType;} public void setObjectId(String v){objectId=v;} public String getObjectId(){return objectId;}
 public void setResult(String v){result=v;} public String getResult(){return result;} public void setSummary(String v){summary=v;} public String getSummary(){return summary;} public LocalDateTime getOccurredAt(){return occurredAt;}
}
