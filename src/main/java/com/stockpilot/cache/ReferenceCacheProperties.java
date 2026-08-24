package com.stockpilot.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("stockpilot.cache")
public class ReferenceCacheProperties {
    private boolean enabled;
    private String keyPrefix = "stockpilot:v1";
    private Duration skuTtl = Duration.ofMinutes(30);
    private Duration warehouseTtl = Duration.ofMinutes(30);
    private Duration nullTtl = Duration.ofSeconds(60);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public Duration getSkuTtl() { return skuTtl; }
    public void setSkuTtl(Duration skuTtl) { this.skuTtl = skuTtl; }
    public Duration getWarehouseTtl() { return warehouseTtl; }
    public void setWarehouseTtl(Duration warehouseTtl) { this.warehouseTtl = warehouseTtl; }
    public Duration getNullTtl() { return nullTtl; }
    public void setNullTtl(Duration nullTtl) { this.nullTtl = nullTtl; }

    public Duration ttl(ReferenceCacheKind kind) {
        return kind == ReferenceCacheKind.SKU ? skuTtl : warehouseTtl;
    }
}
