package com.rag.knowledge.filter;

import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        Exception error = null;

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            error = exception;
            throw exception;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            LoginUser loginUser = UserContext.get();
            Long userId = loginUser == null ? null : loginUser.userId();
            String queryString = request.getQueryString();
            String path = queryString == null ? request.getRequestURI() : request.getRequestURI() + "?" + queryString;

            if (error == null) {
                log.info(
                        "API method={} path={} status={} userId={} durationMs={}",
                        request.getMethod(),
                        path,
                        response.getStatus(),
                        userId,
                        durationMs
                );
            } else {
                log.warn(
                        "API method={} path={} status={} userId={} durationMs={} error={}",
                        request.getMethod(),
                        path,
                        response.getStatus(),
                        userId,
                        durationMs,
                        error.getMessage()
                );
            }
        }
    }
}
