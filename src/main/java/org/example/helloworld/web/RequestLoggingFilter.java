package org.example.helloworld.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives every request an ID and writes one summary line per request:
 * {@code GET /hello-world -> 200 (3 ms)}.
 * <ul>
 *   <li>The ID is taken from the {@code X-Request-Id} header when the client sends a safe value,
 *       otherwise a new UUID is generated. It is returned in the response header and stored in
 *       the logging context (MDC) so every log line for the request includes it.</li>
 *   <li>Only the path is logged, never the query string: it contains the caller's name, which is
 *       personal data.</li>
 *   <li>Health checks are not logged, so load-balancer polling does not flood the log.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    /**
     * Client-supplied IDs are accepted only if short and made of safe characters, so they cannot
     * be used to inject fake lines or control characters into the logs.
     */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final String HEALTH_PATH = "/actuator/health";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String requestId = requestIdFrom(request);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
            logSummary(request, response.getStatus(), start);
        } catch (IOException | ServletException | RuntimeException ex) {
            // The container turns this into a 500 after the filter returns.
            log.error("{} {} -> 500 ({} ms)", request.getMethod(), path(request), elapsedMillis(start), ex);
            throw ex;
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private static String requestIdFrom(HttpServletRequest request) {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        if (supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    private static void logSummary(HttpServletRequest request, int status, long start) {
        String path = path(request);
        if (path.startsWith(HEALTH_PATH)) {
            return;
        }
        log.info("{} {} -> {} ({} ms)", request.getMethod(), path, status, elapsedMillis(start));
    }

    /** Path within the application, without the query string or the deployment context path. */
    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
