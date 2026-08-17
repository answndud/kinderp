package com.kinderp.global.logging;

import com.kinderp.global.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/img/")
                || uri.startsWith("/images/")
                || "/favicon.ico".equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            log.info("http_request method={} uri={} status={} durationMs={} clientIp={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    clientIpResolver.resolve(request));
        }
    }
}
