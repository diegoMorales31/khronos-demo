package com.moga.khronosdemo.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The browser's PushSubscription as its toJSON() gives it; expirationTime is ignored. */
public record PushSubscriptionRequestDTO(
        @NotBlank @Size(max = 1000)
        String endpoint,

        @NotNull @Valid
        Keys keys
) {

    public record Keys(
            @NotBlank @Size(max = 100)
            String p256dh,

            @NotBlank @Size(max = 50)
            String auth
    ) {
    }
}
