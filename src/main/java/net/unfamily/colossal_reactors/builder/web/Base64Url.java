package net.unfamily.colossal_reactors.builder.web;

import java.util.Base64;

/**
 * Base64 URL-safe encoding without padding, matching ModDecoded {@code share.js} {@code b64u}.
 */
public final class Base64Url {

    private Base64Url() {}

    public static String encode(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes");
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Decodes a base64url string (no padding required). Returns null on invalid input.
     */
    public static byte[] decode(String s) {
        if (s == null) {
            return null;
        }
        if (!s.matches("^[A-Za-z0-9_-]*$")) {
            return null;
        }
        try {
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
