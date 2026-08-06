package fpt.qn.junglechess.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import fpt.qn.junglechess.jooq.enums.SysRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security SpEL evaluator bean.
 * Used in @PreAuthorize expressions, e.g.:
 *   @PreAuthorize("@security.isAdmin()")
 *   @PreAuthorize("@security.isSelf(#id)")
 */
@Component("security")
@RequiredArgsConstructor
@Slf4j
public class ProjectSecurityEvaluator {

    public Optional<UserPrincipal> getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public boolean isAdmin() {
        return hasRole(SysRole.ADMIN);
    }

    public boolean isUser() {
        return hasRole(SysRole.USER);
    }

    public boolean isBot() {
        return hasRole(SysRole.BOT);
    }

    public boolean isSelf(UUID userId) {
        if (userId == null) return false;
        return getCurrentPrincipal()
                .map(p -> userId.equals(p.getId()))
                .orElse(false);
    }

    public boolean isAdminOrSelf(UUID userId) {
        return isAdmin() || isSelf(userId);
    }

    private boolean hasRole(SysRole role) {
        return getCurrentPrincipal()
                .map(p -> p.getAuthorities().stream()
                        .anyMatch(a -> role.getLiteral().equals(a.getAuthority())))
                .orElse(false);
    }
}
