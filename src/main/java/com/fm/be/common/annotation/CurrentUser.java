package com.fm.be.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to inject the currently authenticated {@link com.fm.be.common.security.UserPrincipal}
 * into controller method parameters.
 *
 * <p>Usage:</p>
 * <pre>
 * @GetMapping("/me")
 * public UserResponseDto getProfile(@CurrentUser UserPrincipal principal) {
 *     return userService.getProfile(principal.getUserId());
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
