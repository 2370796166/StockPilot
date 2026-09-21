package com.stockpilot.masterdata.request;

import com.stockpilot.masterdata.domain.MasterDataStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class PageQuery {
    @Min(1)
    private long page = 1;

    @Min(1)
    @Max(100)
    private long size = 20;

    @Size(max = 32)
    private String code;

    @Size(max = 100)
    private String name;

    @Size(max = 100)
    private String keyword;

    private MasterDataStatus status;

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public MasterDataStatus getStatus() {
        return status;
    }

    public void setStatus(MasterDataStatus status) {
        this.status = status;
    }
}
