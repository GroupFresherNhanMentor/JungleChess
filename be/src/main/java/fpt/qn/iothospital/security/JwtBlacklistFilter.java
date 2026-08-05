package fpt.qn.iothospital.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtBlacklistFilter extends OncePerRequestFilter {

    JwtDecoder jwtDecoder;
    RedisTokenBlacklistService redisTokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Jwt jwt = jwtDecoder.decode(token);
                String tokenId = jwt.getId();
                if (tokenId != null && redisTokenBlacklistService.isBlacklisted(tokenId)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Token has been revoked/blacklisted\"}");
                    return;
                }
            } catch (JwtException ignored) {
                // Handled downstream by Spring Security BearerTokenAuthenticationFilter
            }
        }

        filterChain.doFilter(request, response);
    }
}
