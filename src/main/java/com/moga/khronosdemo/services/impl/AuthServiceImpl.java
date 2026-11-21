package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.LoginRequestDTO;
import com.moga.khronosdemo.dtos.request.RefreshRequestDTO;
import com.moga.khronosdemo.dtos.response.TokenResponseDTO;
import com.moga.khronosdemo.exceptions.TooManyLoginAttemptsException;
import com.moga.khronosdemo.security.DatabaseUserDetailsService;
import com.moga.khronosdemo.security.LoginAttemptService;
import com.moga.khronosdemo.security.RefreshTokenService;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.security.TokenService;
import com.moga.khronosdemo.services.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final DatabaseUserDetailsService userDetailsService;

    public AuthServiceImpl(AuthenticationManager authenticationManager, TokenService tokenService,
                           LoginAttemptService loginAttemptService, RefreshTokenService refreshTokenService,
                           DatabaseUserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.loginAttemptService = loginAttemptService;
        this.refreshTokenService = refreshTokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public TokenResponseDTO login(LoginRequestDTO request, String clientIp) {
        // Checked before the password, so a blocked pair learns nothing even when the password is right
        long retryAfter = loginAttemptService.secondsUntilAllowed(request.username(), clientIp);
        if (retryAfter > 0) {
            throw new TooManyLoginAttemptsException(retryAfter);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(request.username(), clientIp);
            throw ex;
        }

        loginAttemptService.recordSuccess(request.username(), clientIp);
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        return tokenService.issue(user).withRefreshToken(refreshTokenService.start(user.getId()));
    }

    // The user is checked as on any request with a token: a deactivated worker gets 401 "This account is disabled"
    @Override
    public TokenResponseDTO refresh(RefreshRequestDTO request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());
        SecurityUser user = userDetailsService.loadUserById(rotation.userId())
                .orElseThrow(() -> new CredentialsExpiredException("The user of this session no longer exists"));
        if (!user.isEnabled()) {
            throw new DisabledException("This account is disabled");
        }
        return tokenService.issue(user).withRefreshToken(rotation.refreshToken());
    }

    @Override
    public void logout(RefreshRequestDTO request) {
        refreshTokenService.end(request.refreshToken());
    }
}
