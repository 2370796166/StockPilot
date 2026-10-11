package com.stockpilot.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String previous = MDC.get("requestId");
        String id = UUID.randomUUID().toString();
        long start = System.nanoTime();
        MDC.put("requestId", id);
        response.setHeader("X-Request-ID", id);
        try {
            chain.doFilter(request, response);
        } finally {
            log.info(
                    "HTTP requestId={} method={} status={} durationMs={}",
                    id,
                    request.getMethod(),
                    response.getStatus(),
                    (System.nanoTime() - start) / 1000000);
            if (previous == null) MDC.remove("requestId");
            else MDC.put("requestId", previous);
        }
    }
}
