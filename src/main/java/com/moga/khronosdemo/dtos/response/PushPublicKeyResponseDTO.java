package com.moga.khronosdemo.dtos.response;

/** The VAPID public key a browser subscribes with (applicationServerKey), base64url. */
public record PushPublicKeyResponseDTO(String publicKey) {
}
