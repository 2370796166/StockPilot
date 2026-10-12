package com.stockpilot.messaging.service;

/** Keeps failure context within MySQL character limits without splitting surrogate pairs. */
final class MessageFailureText {
    private MessageFailureText() {}

    static String bounded(String value, int maxCharacters) {
        return value.codePointCount(0, value.length()) <= maxCharacters
                ? value
                : value.substring(0, value.offsetByCodePoints(0, maxCharacters));
    }
}
