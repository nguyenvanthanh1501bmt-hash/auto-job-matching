package com.autojob.modules.jobcrawler.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class FingerprintUtil {

    private FingerprintUtil() {
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create SHA-256 fingerprint", e);
        }
    }
}