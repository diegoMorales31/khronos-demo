package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.LoginRequestDTO;
import com.moga.khronosdemo.dtos.request.RefreshRequestDTO;
import com.moga.khronosdemo.dtos.response.TokenResponseDTO;

public interface AuthService {

    // Checks the password and returns a token; throws when the sign-in fails or is blocked for now
    TokenResponseDTO login(LoginRequestDTO request, String clientIp);

    // A new access token and refresh token for a refresh token; throws when its session is over
    TokenResponseDTO refresh(RefreshRequestDTO request);

    // Ends the session of the refresh token; nothing happens when it has none
    void logout(RefreshRequestDTO request);
}
