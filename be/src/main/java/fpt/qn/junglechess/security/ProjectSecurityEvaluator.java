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

    /** Returns true if the current user has the ADMIN role. */
    public boolean isAdmin() {
        return getCurrentPrincipal()
                .map(p -> p.getAuthorities().stream()
                        .anyMatch(a -> SysRole.ADMIN.getLiteral().equals(a.getAuthority())))
                .orElse(false);
    }

    /** Returns true if the current user has the DOCTOR role. */
    public boolean isDoctor() {
        return hasRole(SysRole.DOCTOR);
    }

    /** Returns true if the current user has the NURSE role. */
    public boolean isNurse() {
        return hasRole(SysRole.NURSE);
    }

    /** Returns true if the current user has the RECEPTIONIST role. */
    public boolean isReceptionist() {
        return hasRole(SysRole.RECEPTIONIST);
    }

    /** Returns true if the current user has the PHARMACIST role. */
    public boolean isPharmacist() {
        return hasRole(SysRole.PHARMACIST);
    }

    /** Returns true if the current user has the LAB_TECHNICIAN role. */
    public boolean isLabTechnician() {
        return hasRole(SysRole.LAB_TECHNICIAN);
    }

    /** Returns true if the current user has the BILLING_STAFF role. */
    public boolean isBillingStaff() {
        return hasRole(SysRole.BILLING_STAFF);
    }

    /** Returns true if the current user has the PATIENT role. */
    public boolean isPatient() {
        return hasRole(SysRole.PATIENT);
    }

    /**
     * Returns true if the current authenticated user's ID matches the given userId.
     * Useful for allowing users to access/modify their own resources.
     */
    public boolean isSelf(UUID userId) {
        if (userId == null) return false;
        return getCurrentPrincipal()
                .map(p -> userId.equals(p.getId()))
                .orElse(false);
    }

    /**
     * Returns true if the caller is an ADMIN OR is the user themselves.
     * Common pattern: admins can manage all users, users can manage themselves.
     */
    public boolean isAdminOrSelf(UUID userId) {
        return isAdmin() || isSelf(userId);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private boolean hasRole(SysRole role) {
        return getCurrentPrincipal()
                .map(p -> p.getAuthorities().stream()
                        .anyMatch(a -> role.getLiteral().equals(a.getAuthority())))
                .orElse(false);
    }
}
