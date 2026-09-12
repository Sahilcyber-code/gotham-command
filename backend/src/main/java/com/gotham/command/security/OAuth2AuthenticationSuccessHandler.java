package com.gotham.command.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.gotham.command.config.AuthCookieProperties;
import com.gotham.command.entity.RefreshToken;
import com.gotham.command.entity.User;
import com.gotham.command.service.RefreshTokenService;
import com.gotham.command.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieProperties authCookieProperties;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public OAuth2AuthenticationSuccessHandler(
        UserService userService,
        JwtService jwtService,
        RefreshTokenService refreshTokenService,
        AuthCookieProperties authCookieProperties
    ) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authCookieProperties = authCookieProperties;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        if (response.isCommitted()) {
            logger.debug("Response has already been committed. Unable to redirect.");
            return;
        }

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        String providerId = oauth2User.getAttribute("sub");
        if (providerId == null || providerId.isBlank()) {
            providerId = oauth2User.getName();
        }

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String picture = oauth2User.getAttribute("picture");

        try {
            User user = userService.processGoogleOAuthUser(providerId, email, name, picture);

            RefreshToken refreshToken = refreshTokenService.generateAndSaveRefreshToken(user);

            ResponseCookie cookie = ResponseCookie.from(authCookieProperties.getName(), refreshToken.getToken())
                .httpOnly(true)
                .secure(authCookieProperties.isSecure())
                .path(authCookieProperties.getPath())
                .maxAge(authCookieProperties.getMaxAgeSeconds())
                .sameSite(authCookieProperties.getSameSite())
                .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            String targetUrl = frontendUrl + "/auth/callback?auth=success";
            getRedirectStrategy().sendRedirect(request, response, targetUrl);

        } catch (Exception ex) {
            logger.error("Error processing Google OAuth login: {}", ex.getMessage());
            String errorUrl = frontendUrl + "/auth/callback?error=authentication_failed";
            getRedirectStrategy().sendRedirect(request, response, errorUrl);
        }
    }
}
