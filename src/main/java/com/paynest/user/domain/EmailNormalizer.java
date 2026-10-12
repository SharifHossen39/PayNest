package com.paynest.user.domain;

import java.util.Locale;

public final class EmailNormalizer {

    private static final int MAX_EMAIL_LENGTH = 254;

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }

        String normalized = stripUnicodeEdges(email).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("Email must not exceed 254 characters");
        }
        return normalized;
    }

    public static String stripUnicodeEdges(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
