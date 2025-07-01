package keystrokesmod.module.impl.other;

import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.network.Packet;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

public class LatencyAlerts extends Module {
    private SliderSetting interval;
    private SliderSetting highLatency;
    private ButtonSetting ignoreLimbo;

    private long lastPacketTime = 0L;
    private long lastAlert = 0L;

    private Packet<?> lastPacket = null;

    public LatencyAlerts() {
        super("Latency Alerts", category.other);
        this.registerSetting(new DescriptionSetting("Detects packet loss."));
        this.registerSetting(interval = new SliderSetting("Alert interval", " second", 3.0, 0.0, 5.0, 0.1));
        this.registerSetting(highLatency = new SliderSetting("High latency", " second", 0.5, 0.1, 5.0, 0.1));
        this.registerSetting(ignoreLimbo = new ButtonSetting("Ignore limbo", true));
        this.closetModule = true;
    }

    @SubscribeEvent
    public void onPacketReceive(ReceivePacketEvent e) {
        this.lastPacketTime = System.currentTimeMillis();
        this.lastPacket = e.getPacket();
    }

    public void onUpdate() {
        if (mc.isSingleplayer() || (this.ignoreLimbo.isToggled() && inLimbo())) {
            this.lastPacketTime = System.currentTimeMillis();
            this.lastAlert = System.currentTimeMillis();
            return;
        }
        long currentMs = System.currentTimeMillis();
        if (currentMs - this.lastPacketTime >= this.highLatency.getInput() * 1000 && currentMs - this.lastAlert >= this.interval.getInput() * 1000) {
            String msSinceLastPacket = String.valueOf(Math.abs(System.currentTimeMillis() - this.lastPacketTime));
            Utils.sendMessage("&7Packet loss detected: &c" + msSinceLastPacket + "&7ms");
            this.lastAlert = System.currentTimeMillis();
        }
    }

    public void onDisable() {
        this.lastPacketTime = 0;
        this.lastAlert = 0;
        this.lastPacket = null;
    }

    public void onEnable() {
        this.lastPacketTime = System.currentTimeMillis();
    }

    public boolean inLimbo() {
        List<String> scoreboard = Utils.getSidebarLines();
        if (scoreboard == null || scoreboard.isEmpty()) {
            if (mc.theWorld.provider.getDimensionName().equals("The End")) {
                return true;
            }
        }
        return false;
    }

}
