package keystrokesmod.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.Agent;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class AccountManager {

    private static final String OFFLINE_TOKEN = "0";
    private static final String TYPE_LEGACY = "legacy";
    private static final String TYPE_MOJANG = "mojang";

    public static void loginCracked(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        setSession(new Session(username, uuid, OFFLINE_TOKEN, TYPE_LEGACY));
    }

    public static void loginToken(String token, Consumer<String> statusCallback) {
        final String accessToken = token == null ? "" : token.trim();
        if (accessToken.isEmpty()) {
            statusCallback.accept("Enter a token");
            return;
        }
        new Thread(() -> {
            try {
                JsonObject payload = decodeJwtPayload(accessToken);
                if (payload != null) {
                    String[] profile = extractMcProfile(payload);
                    if (profile != null) {
                        boolean expired = isExpired(payload);
                        setSession(new Session(profile[0], profile[1].replace("-", ""), accessToken, TYPE_MOJANG));
                        statusCallback.accept("Logged in as " + profile[0] + (expired ? " (token expired)" : ""));
                        return;
                    }
                }

                YggdrasilAuthenticationService authService =
                        new YggdrasilAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString());
                YggdrasilUserAuthentication auth = (YggdrasilUserAuthentication)
                        authService.createUserAuthentication(Agent.MINECRAFT);

                Map<String, Object> storage = new HashMap<>();
                storage.put("accessToken", accessToken);
                auth.loadFromStorage(storage);
                auth.logIn();

                if (auth.getSelectedProfile() != null) {
                    String profileName = auth.getSelectedProfile().getName();
                    String profileId = auth.getSelectedProfile().getId().toString().replace("-", "");
                    setSession(new Session(profileName, profileId, auth.getAuthenticatedToken(), TYPE_MOJANG));
                    statusCallback.accept("Logged in as " + profileName);
                } else {
                    statusCallback.accept("Login failed: no profile returned");
                }
            } catch (AuthenticationException e) {
                statusCallback.accept("Login failed: " + e.getMessage());
            } catch (Exception e) {
                statusCallback.accept("Login failed: " + e.getClass().getSimpleName());
            }
        }, "Aldo++ Account Login").start();
    }

    private static JsonObject decodeJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payload = parts[1].replace('-', '+').replace('_', '/');
            int remainder = payload.length() % 4;
            if (remainder > 0) {
                payload += "====".substring(remainder);
            }
            byte[] bytes = Base64.getDecoder().decode(payload);
            return new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] extractMcProfile(JsonObject payload) {
        try {
            JsonArray pfd = payload.getAsJsonArray("pfd");
            if (pfd != null) {
                for (JsonElement element : pfd) {
                    JsonObject profile = element.getAsJsonObject();
                    if ("mc".equals(profile.get("type").getAsString()) && profile.has("name") && profile.has("id")) {
                        return new String[]{profile.get("name").getAsString(), profile.get("id").getAsString()};
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isExpired(JsonObject payload) {
        try {
            if (payload.has("exp")) {
                return payload.get("exp").getAsLong() < System.currentTimeMillis() / 1000L;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String getCurrentUsername() {
        return Minecraft.getMinecraft().getSession().getUsername();
    }

    private static void setSession(Session session) {
        ((IAccessorMinecraft) Minecraft.getMinecraft()).setSession(session);
    }
}