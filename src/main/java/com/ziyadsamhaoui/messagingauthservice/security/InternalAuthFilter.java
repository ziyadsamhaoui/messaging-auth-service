package com.ziyadsamhaoui.messagingauthservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Token";
    private static final String INTERNAL_PREFIX = "/internal/";

    private final byte[] secret;

    public InternalAuthFilter(@Value("${badrlink.security.internal-token}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(INTERNAL_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader(HEADER);
        if (provided == null || !MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), secret)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid internal token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
