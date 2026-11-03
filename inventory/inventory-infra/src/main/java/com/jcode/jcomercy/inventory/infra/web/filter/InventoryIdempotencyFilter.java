package com.jcode.jcomercy.inventory.infra.web.filter;

import com.jcode.jcomercy.inventory.infra.idempotency.InventoryIdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(10) // Executes after JwtAuthenticationFilter (Order 1)
public class InventoryIdempotencyFilter extends OncePerRequestFilter {

    private static final Pattern COMPANY_ID_PATTERN = Pattern.compile("/api/v1/companies/([^/]+)");
    private final InventoryIdempotencyService idempotencyService;

    public InventoryIdempotencyFilter(InventoryIdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String key = request.getHeader("Idempotency-Key");
        return key == null || key.isBlank();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String idempotencyKey = request.getHeader("Idempotency-Key").trim();
        String companyId = extractCompanyId(request.getRequestURI());
        String actorUserId = extractActorUserId();

        ResettableStreamRequestWrapper wrappedRequest = new ResettableStreamRequestWrapper(request);
        String requestBody = new String(wrappedRequest.getRawData(), StandardCharsets.UTF_8);

        var claimResult = idempotencyService.claim(companyId, actorUserId, idempotencyKey, requestBody);

        if (claimResult.isConflict()) {
            response.setStatus(HttpStatus.CONFLICT.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"IDEMPOTENCY_KEY_CONFLICT\",\"message\":\"Idempotency key reused with different request payload or concurrent execution\"}");
            return;
        }

        if (claimResult.isCompleted()) {
            response.setStatus(claimResult.cachedStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            if (claimResult.cachedResponseBody() != null) {
                response.getWriter().write(claimResult.cachedResponseBody());
            }
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        wrappedResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            byte[] respBytes = wrappedResponse.getContentAsByteArray();
            String encoding = wrappedResponse.getCharacterEncoding();
            java.nio.charset.Charset charset;
            try {
                charset = encoding != null ? java.nio.charset.Charset.forName(encoding) : StandardCharsets.UTF_8;
            } catch (Exception e) {
                charset = StandardCharsets.UTF_8;
            }
            String responseBody = new String(respBytes, charset);
            int status = wrappedResponse.getStatus();

            if (status >= 500) {
                // Un error del servidor es transitorio: permite reintentar la operación
                // con la misma clave sin reproducir indefinidamente la respuesta 500.
                idempotencyService.release(claimResult.id());
            } else if (status > 0) {
                idempotencyService.complete(claimResult.id(), status, responseBody);
            }

            wrappedResponse.copyBodyToResponse();
        }
    }

    private String extractCompanyId(String uri) {
        Matcher matcher = COMPANY_ID_PATTERN.matcher(uri);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "GLOBAL";
    }

    private String extractActorUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return "ANONYMOUS";
    }

    private static class ResettableStreamRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] rawData;

        public ResettableStreamRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.rawData = request.getInputStream().readAllBytes();
        }

        public byte[] getRawData() {
            return rawData;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffer = new ByteArrayInputStream(rawData);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() { return buffer.available() == 0; }
                @Override
                public boolean isReady() { return true; }
                @Override
                public void setReadListener(ReadListener listener) {}
                @Override
                public int read() { return buffer.read(); }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    encoding != null ? encoding : StandardCharsets.UTF_8.name()));
        }
    }
}
