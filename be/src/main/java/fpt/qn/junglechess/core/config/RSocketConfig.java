package fpt.qn.junglechess.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;

import fpt.qn.junglechess.security.JwtPrincipalAuthenticationConverter;
import fpt.qn.junglechess.security.RSocketJwtRevocationInterceptor;

@Configuration
@EnableRSocketSecurity
public class RSocketConfig {

    @Bean
    public ReactiveAuthenticationManager rsocketJwtAuthenticationManager(
            ReactiveJwtDecoder jwtDecoder,
            JwtPrincipalAuthenticationConverter jwtAuthenticationConverter) {
        JwtReactiveAuthenticationManager authenticationManager = new JwtReactiveAuthenticationManager(jwtDecoder);
        authenticationManager.setJwtAuthenticationConverter(jwtAuthenticationConverter);
        return authenticationManager;
    }

    @Bean
    public PayloadSocketAcceptorInterceptor rsocketInterceptor(
            RSocketSecurity security,
            ReactiveAuthenticationManager rsocketJwtAuthenticationManager,
            RSocketJwtRevocationInterceptor revocationInterceptor) {
        return security
                .jwt(jwt -> jwt.authenticationManager(rsocketJwtAuthenticationManager))
                .addPayloadInterceptor(revocationInterceptor)
                .authorizePayload(authorize -> authorize
                        .setup().authenticated()
                        .route("admin.eve.rooms").hasAuthority("ADMIN")
                        .route("lobby.rooms").authenticated()
                        .anyRequest().authenticated()
                        .anyExchange().denyAll()
                )
                .build();
    }
}
