package keystrokesmod.utility;

import com.mojang.authlib.Agent;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
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

    public static void loginToken(String username, String token, Consumer<String> statusCallback) {
        new Thread(() -> {
            try {
                YggdrasilAuthenticationService authService =
                        new YggdrasilAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString());
                YggdrasilUserAuthentication auth = (YggdrasilUserAuthentication)
                        authService.createUserAuthentication(Agent.MINECRAFT);

                Map<String, Object> storage = new HashMap<>();
                storage.put("username", username);
                storage.put("accessToken", token);
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

    public static String getCurrentUsername() {
        return Minecraft.getMinecraft().getSession().getUsername();
    }

    private static void setSession(Session session) {
        ((IAccessorMinecraft) Minecraft.getMinecraft()).setSession(session);
    }
}
