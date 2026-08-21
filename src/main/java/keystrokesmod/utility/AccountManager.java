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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
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
    private static final String MSA_CLIENT_ID = "00000000402b5328";

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

                String refreshToken = extractRefreshToken(accessToken);
                if (refreshToken != null) {
                    loginRefreshToken(refreshToken, statusCallback);
                    return;
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
                statusCallback.accept("Login failed: " + e.getMessage());
            }
        }, "Aldo++ Account Login").start();
    }

    private static String extractRefreshToken(String input) {
        String candidate = input;
        int colon = input.indexOf(':');
        if (colon > 0 && colon < input.length() - 1) {
            candidate = input.substring(colon + 1).trim();
        }
        return candidate.startsWith("M.") ? candidate : null;
    }

    private static void loginRefreshToken(String refreshToken, Consumer<String> statusCallback) throws Exception {
        JsonObject msa = postForm("https://login.live.com/oauth20_token.srf",
                "client_id=" + MSA_CLIENT_ID
                        + "&grant_type=refresh_token&refresh_token=" + urlEncode(refreshToken)
                        + "&scope=" + urlEncode("service::user.auth.xboxlive.com::MBI_SSL"));
        String msaAccessToken = requireField(msa, "access_token");

        JsonObject xblRequest = new JsonObject();
        JsonObject xblProperties = new JsonObject();
        xblProperties.addProperty("AuthMethod", "RPS");
        xblProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProperties.addProperty("RpsTicket", "d=" + msaAccessToken);
        xblRequest.add("Properties", xblProperties);
        xblRequest.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblRequest.addProperty("TokenType", "JWT");
        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", xblRequest.toString());
        String xblToken = requireField(xbl, "Token");
        String uhs = extractUhs(xbl);

        JsonObject xstsRequest = new JsonObject();
        JsonObject xstsProperties = new JsonObject();
        JsonArray userTokens = new JsonArray();
        userTokens.add(new com.google.gson.JsonPrimitive(xblToken));
        xstsProperties.add("UserTokens", userTokens);
        xstsProperties.addProperty("SandboxId", "RETAIL");
        xstsRequest.add("Properties", xstsProperties);
        xstsRequest.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsRequest.addProperty("TokenType", "JWT");
        JsonObject xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsRequest.toString());
        String xstsToken = requireField(xsts, "Token");
        uhs = extractUhs(xsts);

        JsonObject mcRequest = new JsonObject();
        mcRequest.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mcLogin = postJson("https://api.minecraftservices.com/authentication/login_with_xbox",
                mcRequest.toString());
        String mcAccessToken = requireField(mcLogin, "access_token");

        JsonObject profile = getJson("https://api.minecraftservices.com/minecraft/profile", mcAccessToken);
        String profileName = requireField(profile, "name");
        String profileId = requireField(profile, "id").replace("-", "");
        setSession(new Session(profileName, profileId, mcAccessToken, TYPE_MOJANG));
        statusCallback.accept("Logged in as " + profileName);
    }

    private static String extractUhs(JsonObject response) throws IOException {
        try {
            return response.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")
                    .get(0).getAsJsonObject().get("uhs").getAsString();
        } catch (Exception e) {
            throw new IOException("missing UHS claim");
        }
    }

    private static String requireField(JsonObject object, String field) throws IOException {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            throw new IOException("missing " + field);
        }
        return object.get(field).getAsString();
    }

    private static JsonObject postForm(String url, String body) throws Exception {
        HttpURLConnection connection = openConnection(url);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(connection);
    }

    private static JsonObject postJson(String url, String body) throws Exception {
        HttpURLConnection connection = openConnection(url);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(connection);
    }

    private static JsonObject getJson(String url, String bearerToken) throws Exception {
        HttpURLConnection connection = openConnection(url);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        connection.setRequestProperty("Accept", "application/json");
        return readResponse(connection);
    }

    private static HttpURLConnection openConnection(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        return connection;
    }

    private static JsonObject readResponse(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder builder = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
        }
        String body = builder.toString();
        if (code >= 400) {
            throw new IOException("HTTP " + code + (body.isEmpty() ? "" : ": " + body.substring(0, Math.min(body.length(), 120))));
        }
        return new JsonParser().parse(body).getAsJsonObject();
    }

    private static String urlEncode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
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