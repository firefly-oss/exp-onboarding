package com.firefly.experience.onboarding.core.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Derives deterministic idempotency keys from stable business inputs.
 *
 * <p>The key contract is: <em>same logical operation, same inputs → same key</em>.
 * This is what downstream services need to reject duplicate writes when a saga
 * step is replayed, a retry policy fires, or the network drops a response.</p>
 *
 * <p>Implementation uses {@link UUID#nameUUIDFromBytes(byte[])} (RFC&nbsp;4122 v3)
 * over a {@code ":"}-joined input string in UTF-8. The output is always a valid
 * UUID string so it can flow through the standard {@code Idempotency-Key} HTTP
 * header on the SDK calls.</p>
 *
 * <p>Choose inputs that uniquely identify the <em>intent</em>: workflow id,
 * execution correlation id (or a stable per-execution variable like {@code partyId}),
 * step id, and a per-iteration discriminator when a step fans out (signer document
 * number, UBO document number, etc.).</p>
 */
public final class IdempotencyKeys {

    private IdempotencyKeys() {
        // utility
    }

    /**
     * Derives a deterministic UUID-shaped idempotency key from the given parts.
     * Null parts are coerced to the literal string {@code "null"} so the call
     * never throws on missing inputs (still deterministic, still distinct from
     * a legitimate empty string).
     *
     * @param parts identifying inputs, joined with {@code ":"}
     * @return a stable v3 UUID string derived from the parts
     */
    public static String of(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(parts[i] == null ? "null" : parts[i]);
        }
        return UUID.nameUUIDFromBytes(sb.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }
}
