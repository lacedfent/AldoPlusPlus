package keystrokesmod.module.impl.fun;

import com.google.gson.JsonObject;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.ActivityType;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.User;
import com.jagrosh.discordipc.exceptions.NoDiscordClientException;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.multiplayer.ServerData;

public class DiscordRPC extends Module {
    private static final long APPLICATION_ID = 1537284163202449458L;

    private volatile boolean running;
    private volatile boolean connected;
    private volatile boolean forceSend;
    private Thread worker;
    private IPCClient client;
    private String lastPresenceKey = "";
    private long sessionStart;

    public DiscordRPC() {
        super("Discord RPC", category.fun);
        this.registerSetting(new DescriptionSetting("Shows your current server on Discord"));
    }

    @Override
    public void onEnable() {
        sessionStart = System.currentTimeMillis() / 1000L;
        startWorker();
    }

    @Override
    public void onDisable() {
        stopWorker();
    }

    @Override
    public String getInfo() {
        return connected ? "Connected" : "No Discord";
    }

    private synchronized void startWorker() {
        stopWorker();
        running = true;
        worker = new Thread(this::run, "AldoRPC");
        worker.setDaemon(true);
        worker.start();
    }

    private synchronized void stopWorker() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        closeClient();
    }

    private void closeClient() {
        connected = false;
        forceSend = false;
        if (client != null) {
            try {
                client.close();
            }
            catch (Exception ignored) {
            }
            client = null;
        }
    }

    private void run() {
        while (running) {
            if (client == null) {
                client = new IPCClient(APPLICATION_ID);
                client.setListener(listener);
            }
            if (!connected) {
                try {
                    client.connect();
                }
                catch (NoDiscordClientException ignored) {
                }
                catch (Exception e) {
                    System.out.println("[DiscordRPC] connect failed: " + e);
                }
            }
            if (connected) {
                updatePresence();
                sleep(1000);
            }
            else {
                sleep(3000);
            }
        }
    }

    private final IPCListener listener = new IPCListener() {
        @Override
        public void onReady(IPCClient client) {
            connected = true;
            forceSend = true;
            System.out.println("[DiscordRPC] connected");
        }

        @Override
        public void onClose(IPCClient client, JsonObject json) {
            connected = false;
        }

        @Override
        public void onDisconnect(IPCClient client, Throwable t) {
            connected = false;
        }

        @Override
        public void onPacketSent(IPCClient client, Packet packet) {
        }

        @Override
        public void onPacketReceived(IPCClient client, Packet packet) {
        }

        @Override
        public void onActivityJoin(IPCClient client, String secret) {
        }

        @Override
        public void onActivitySpectate(IPCClient client, String secret) {
        }

        @Override
        public void onActivityJoinRequest(IPCClient client, String secret, User user) {
        }
    };

    private void updatePresence() {
        String details;
        String state;
        if (Utils.nullCheck()) {
            ServerData server = mc.getCurrentServerData();
            if (server != null) {
                details = server.serverIP;
            }
            else if (mc.isSingleplayer()) {
                details = "Singleplayer";
            }
            else {
                details = "Offline mode";
            }
            int players = mc.theWorld.playerEntities.size();
            state = players + " player" + (players == 1 ? "" : "s") + " online";
        }
        else {
            details = "In the main menu";
            state = "";
        }
        String key = details + "|" + state;
        if (key.equals(lastPresenceKey) && !forceSend) {
            return;
        }
        lastPresenceKey = key;
        forceSend = false;
        RichPresence.Builder builder = new RichPresence.Builder()
                .setActivityType(ActivityType.Playing)
                .setDetails(details)
                .setState(state)
                .setStartTimestamp(sessionStart);
        try {
            client.sendRichPresence(builder.build());
        }
        catch (Exception e) {
            System.out.println("[DiscordRPC] presence send failed: " + e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
