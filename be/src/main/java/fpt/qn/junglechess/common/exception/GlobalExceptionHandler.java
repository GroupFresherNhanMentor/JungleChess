package fpt.qn.junglechess.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

import fpt.qn.junglechess.common.dto.ApiResponse;
import fpt.qn.junglechess.user.exception.InvalidOldPasswordException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleValidationException(WebExchangeBindException ex) {
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Invalid request data");
        return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error(message)));
    }

    @ExceptionHandler(org.springframework.web.server.ServerWebInputException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleServerWebInputException(
            org.springframework.web.server.ServerWebInputException ex, ServerWebExchange exchange) {
        log.warn("Malformed request at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error("Invalid request body payload or malformed JSON")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleAccessDeniedException(
            AccessDeniedException ex, ServerWebExchange exchange) {
        log.warn("Access denied at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage())));
    }

    @ExceptionHandler(AppException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleCustomException(AppException ex, ServerWebExchange exchange) {
        log.warn("App exception at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleIllegalArgumentException(
            IllegalArgumentException ex, ServerWebExchange exchange) {
        log.warn("Bad request at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage())));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleIllegalStateException(
            IllegalStateException ex, ServerWebExchange exchange) {
        log.warn("Bad request at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage())));
    }

    @ExceptionHandler(InvalidOldPasswordException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleInvalidOldPasswordException(
            InvalidOldPasswordException ex, ServerWebExchange exchange) {
        log.warn("Invalid old password at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage())));
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleBadCredentialsException(
            org.springframework.security.authentication.BadCredentialsException ex, ServerWebExchange exchange) {
        log.warn("Authentication failed at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Invalid username or password")));
    }

    @ExceptionHandler({
        org.springframework.security.authentication.DisabledException.class,
        org.springframework.security.authentication.LockedException.class
    })
    public Mono<ResponseEntity<ApiResponse<Void>>> handleAccountStatusException(Exception ex, ServerWebExchange exchange) {
        log.warn("Account status restriction at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Account is locked or disabled")));
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleAuthenticationException(
            org.springframework.security.core.AuthenticationException ex, ServerWebExchange exchange) {
        log.warn("Authentication error at {}: {}", exchange.getRequest().getPath(), ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleException(Exception ex, ServerWebExchange exchange) {
        log.error("Unexpected error at {}: {}", exchange.getRequest().getPath(), ex.getMessage(), ex);
        return Mono.just(ResponseEntity.internalServerError().body(ApiResponse.error("An unexpected error occurred. Please try again later.")));
    }
}
