package com.stockpilot.ai.infrastructure;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;

/** Vendor-specific wire settings; model identifiers are always supplied by the operator. */
public enum AiProvider {
    CUSTOM(""),
    QWEN(""),
    DEEPSEEK("https://api.deepseek.com"),
    GLM("https://open.bigmodel.cn/api/paas/v4"),
    DOUBAO("https://ark.cn-beijing.volces.com/api/v3"),
    MOONSHOT("https://api.moonshot.cn/v1");

    private final String defaultBaseUrl;

    AiProvider(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public static AiProvider parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public String baseUrl(String configured) {
        return configured.isBlank() ? defaultBaseUrl : configured.trim();
    }

    public void configureRequest(ObjectNode body) {
        switch (this) {
            case CUSTOM -> body.put("parallel_tool_calls", false);
            case QWEN -> body.put("enable_thinking", false);
            case DEEPSEEK, GLM, DOUBAO, MOONSHOT ->
                    body.putObject("thinking").put("type", "disabled");
        }
        if (this == MOONSHOT) body.put("temperature", 0.6);
    }
}
