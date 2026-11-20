package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.LoginRequestDTO;
import com.moga.khronosdemo.dtos.response.TokenResponseDTO;
import com.moga.khronosdemo.exceptions.TooManyLoginAttemptsException;
import com.moga.khronosdemo.security.LoginAttemptService;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.security.TokenService;
import com.moga.khronosdemo.services.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;

    public AuthServiceImpl(AuthenticationManager authenticationManager, TokenService tokenService,
                           LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.loginAttemptService = loginAttemptService;
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
        return tokenService.issue((SecurityUser) authentication.getPrincipal());
    }
}
