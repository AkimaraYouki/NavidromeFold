package dev.navifold.player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Active in-memory session; ServerCredentials owns device-encrypted persistence. */
final class SmartSession {
    static volatile Api api;
    static volatile String scope = "";
    static volatile long revision;
    static synchronized void connect(Api client) {
        api = client;
        scope = client == null ? "" : namespace(client.base, client.user);
        revision++;
    }
    static synchronized void changed() { revision++; }
    static String namespace(String server, String user) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((server + "\n" + user).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
