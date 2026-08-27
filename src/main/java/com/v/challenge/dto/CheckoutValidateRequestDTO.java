package com.v.challenge.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutValidateRequestDTO(
    @NotBlank String cavv,
    @NotBlank String xid,
    @NotBlank String eci
) {}
