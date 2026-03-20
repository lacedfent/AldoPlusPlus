package keystrokesmod.lag.handler;

import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.queue.BiTrackLagNodeQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("DuplicatedCode")
public final class UnifiedLagHandler extends AbstractFastTrackProvider {

    private final @NotNull BiTrackLagNodeQueue queue = new BiTrackLagNodeQueue(this);

    private final @NotNull List<Packet<?>> packetFastTrack = new ArrayList<>();

    public void requestLag(final @NotNull LagRequest request) {
        queue.requestLag(request);
    }

    public void releaseExpiredPackets(final @NotNull EnumLagDirection direction, long maxAgeMs) {
        queue.releaseExpiredPackets(direction, maxAgeMs);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSendPacket(final @NotNull SendPacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            return;
        }

        if (event.isCanceled()) {
            return;
        }

        final @NotNull Packet<?> packet = event.getPacket();

        if (packetFastTrack.remove(packet)) {
            return;
        }

        if (queue.tick(packet, EnumLagDirection.OUTBOUND)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onReceivePacket(final @NotNull ReceivePacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            return;
        }

        if (event.isCanceled()) {
            return;
        }

        final @NotNull Packet<?> packet = event.getPacket();

        if (packetFastTrack.remove(packet)) {
            return;
        }

        if (queue.tick(packet, EnumLagDirection.INBOUND)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGameTick(final @NotNull GameTickEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            return;
        }

        queue.tick(null, null);
    }

    @Override
    public void forPacket(final @NotNull Packet<?> packet) {
        packetFastTrack.add(packet);
    }

}