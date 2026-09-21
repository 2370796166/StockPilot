package com.stockpilot.inventory.count.request;

import com.stockpilot.inventory.count.domain.InventoryCountStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class InventoryCountRequests {
    private InventoryCountRequests() {}

    public record Dimension(@NotNull @Positive Long locationId, @NotNull @Positive Long skuId) {}

    public record Create(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,64}") String countNo,
            @NotNull @Positive Long warehouseId,
            @Size(max = 255) String remark,
            @NotEmpty List<@Valid Dimension> dimensions) {}

    public record Transition(@NotNull @Min(0) Integer version) {}

    public record Result(
            @NotNull @Positive Long lineId,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4)
                    BigDecimal countedQuantity,
            @NotBlank @Size(max = 255) String reason) {}

    public record RecordResults(
            @NotNull @Min(0) Integer version, @NotEmpty List<@Valid Result> results) {}

    public static final class PageQuery {
        @Min(1)
        private long page = 1;

        @Min(1)
        @Max(100)
        private long size = 20;

        @Size(max = 64)
        private String countNo;

        @Positive private Long warehouseId;
        private InventoryCountStatus status;

        public long getPage() {
            return page;
        }

        public void setPage(long v) {
            page = v;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long v) {
            size = v;
        }

        public String getCountNo() {
            return countNo;
        }

        public void setCountNo(String v) {
            countNo = v;
        }

        public Long getWarehouseId() {
            return warehouseId;
        }

        public void setWarehouseId(Long v) {
            warehouseId = v;
        }

        public InventoryCountStatus getStatus() {
            return status;
        }

        public void setStatus(InventoryCountStatus v) {
            status = v;
        }
    }
}
