package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.LoginRequestDTO;
import com.moga.khronosdemo.dtos.response.TokenResponseDTO;

public interface AuthService {

    // Checks the password and returns a token; throws when the sign-in fails or is blocked for now
    TokenResponseDTO login(LoginRequestDTO request, String clientIp);
}
