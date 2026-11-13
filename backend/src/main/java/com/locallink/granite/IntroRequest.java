package com.locallink.granite;

/** Request body for POST /api/intros/draft and GraniteClient.draftIntro(). */
public record IntroRequest(
        String needText,
        String city,
        String providerId,
        String skillId
) {}
