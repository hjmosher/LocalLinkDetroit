package com.locallink.granite;

/** Request body for POST /api/needs/decompose and GraniteClient.decompose(). */
public record DecomposeRequest(
        String text,  // owner's plain-language description; capped at 1000 chars in controller
        String city
) {}
