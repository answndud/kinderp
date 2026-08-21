package com.kinderp.global.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Content-Length, header, parameter 수를 애플리케이션 진입 전에 제한한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RequestLimitFilter extends OncePerRequestFilter {

    private final RequestLimitProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > properties.getMaxRequestSize().toBytes()
                || countHeaders(request) > properties.getMaxHeaderCount()
                || request.getParameterMap().size() > properties.getMaxParameterCount()) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Request exceeds configured resource limits");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int countHeaders(HttpServletRequest request) {
        int count = 0;
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            headerNames.nextElement();
            count++;
        }
        return count;
    }
}
