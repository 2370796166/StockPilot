package com.stockpilot.sales.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class SalesOutboundRequests {
    private SalesOutboundRequests() {}

    public record Line(
            @NotNull @Positive Long locationId,
            @NotNull @Positive Long skuId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
                    BigDecimal quantity) {}

    public record Create(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,64}") String outboundNo,
            @NotNull @Positive Long warehouseId,
            @Size(max = 255) String remark,
            @NotEmpty List<@Valid Line> lines) {}

    public record Update(
            @NotNull @Min(0) Integer version,
            @NotNull @Positive Long warehouseId,
            @Size(max = 255) String remark,
            @NotEmpty List<@Valid Line> lines) {}

    public record Transition(@NotNull @Min(0) Integer version) {}

    public static final class PageQuery extends com.stockpilot.shared.query.DocumentDateRangeQuery {
        @Min(1)
        private long page = 1;

        @Min(1)
        @Max(100)
        private long size = 20;

        @Size(max = 64)
        private String outboundNo;

        @Positive private Long warehouseId;
        private SalesOutboundStatusFilter status;

        public long getPage() {
            return page;
        }

        public void setPage(long page) {
            this.page = page;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public String getOutboundNo() {
            return outboundNo;
        }

        public void setOutboundNo(String outboundNo) {
            this.outboundNo = outboundNo;
        }

        public Long getWarehouseId() {
            return warehouseId;
        }

        public void setWarehouseId(Long warehouseId) {
            this.warehouseId = warehouseId;
        }

        public SalesOutboundStatusFilter getStatus() {
            return status;
        }

        public void setStatus(SalesOutboundStatusFilter status) {
            this.status = status;
        }
    }

    public enum SalesOutboundStatusFilter {
        DRAFT,
        RESERVED,
        APPROVED,
        COMPLETED,
        CANCELLED
    }
}
