package com.moga.khronosdemo.security;

import com.moga.khronosdemo.dtos.response.TokenResponseDTO;

public interface TokenService {

    // Every token is signed with this issuer, and a token with any other one is refused
    String ISSUER = "khronos";

    TokenResponseDTO issue(SecurityUser user);
}
