package com.fm.be.common.security;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.constants.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CurrentUserArgumentResolver}.
 */
class CurrentUserArgumentResolverTest {

    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserArgumentResolver();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("supportsParameter returns true for @CurrentUser UserPrincipal param")
    void supportsParameter_annotatedUserPrincipal_returnsTrue() throws Exception {
        MethodParameter param = getMethodParameter("annotatedMethod", UserPrincipal.class);
        assertThat(resolver.supportsParameter(param)).isTrue();
    }

    @Test
    @DisplayName("supportsParameter returns false for param without @CurrentUser")
    void supportsParameter_noAnnotation_returnsFalse() throws Exception {
        MethodParameter param = getMethodParameter("nonAnnotatedMethod", UserPrincipal.class);
        assertThat(resolver.supportsParameter(param)).isFalse();
    }

    @Test
    @DisplayName("supportsParameter returns false for wrong parameter type")
    void supportsParameter_wrongType_returnsFalse() throws Exception {
        MethodParameter param = getMethodParameter("wrongTypeMethod", String.class);
        assertThat(resolver.supportsParameter(param)).isFalse();
    }

    @Test
    @DisplayName("resolveArgument returns UserPrincipal when authenticated")
    void resolveArgument_authenticated_returnsPrincipal() throws Exception {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.ADMIN)
                .organizationId(UUID.randomUUID())
                .siteIds(List.of())
                .build();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Object result = resolver.resolveArgument(null, null, null, null);
        assertThat(result).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) result).getUserId()).isEqualTo(principal.getUserId());
    }

    @Test
    @DisplayName("resolveArgument returns null when no authentication")
    void resolveArgument_noAuth_returnsNull() throws Exception {
        Object result = resolver.resolveArgument(null, null, null, null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolveArgument returns null when principal is not UserPrincipal")
    void resolveArgument_wrongPrincipalType_returnsNull() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken("stringPrincipal", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Object result = resolver.resolveArgument(null, null, null, null);
        assertThat(result).isNull();
    }

    @SuppressWarnings("unused")
    static void annotatedMethod(@CurrentUser UserPrincipal principal) {}

    @SuppressWarnings("unused")
    static void nonAnnotatedMethod(UserPrincipal principal) {}

    @SuppressWarnings("unused")
    static void wrongTypeMethod(@CurrentUser String value) {}

    private MethodParameter getMethodParameter(String methodName, Class<?> paramType) throws Exception {
        Method method = CurrentUserArgumentResolverTest.class
                .getDeclaredMethod(methodName, paramType);
        return new MethodParameter(method, 0);
    }
}
