package com.moga.khronosdemo.security;

import com.moga.khronosdemo.entities.enums.PlanFeature;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.exceptions.PlanFeatureUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Keeps owners to the features of their plan (see {@link RequiresPlan}). It only looks at the caller's
 * own plan, which was read with the rest of the account for this request, so a change of plan applies
 * from the next request. Who may touch which establishment is still up to each endpoint's @PreAuthorize,
 * which runs after this: a worker gets the usual 403 there, and a platform admin is never limited.
 * It runs before the request body is read or validated, so a refused owner always gets this 403.
 */
@Component
public class PlanFeatureInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        PlanFeature feature = requiredFeature(method);
        if (feature == null) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser user
                && user.getRole() == Role.OWNER && user.getPlan() != null && !user.getPlan().includes(feature)) {
            throw new PlanFeatureUnavailableException(user.getPlan(), feature);
        }
        return true;
    }

    private static PlanFeature requiredFeature(HandlerMethod method) {
        RequiresPlan annotation = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresPlan.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresPlan.class);
        }
        return annotation != null ? annotation.value() : null;
    }
}
