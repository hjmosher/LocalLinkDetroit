package com.locallink.granite;

/**
 * Response body for POST /api/intros/draft.
 * body is a draft only — never sent automatically.
 */
public record IntroResponse(
        String body,    // draft message text
        String source   // "granite" | "template"
) {}
