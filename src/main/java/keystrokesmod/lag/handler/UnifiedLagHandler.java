package keystrokesmod.lag.handler;

import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.queue.BiTrackLagNodeQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("DuplicatedCode")
public final class UnifiedLagHandler extends AbstractFastTrackProvider {

    private final @NotNull BiTrackLagNodeQueue queue = new BiTrackLagNodeQueue(this);

    private final @NotNull List<Packet<?>> packetFastTrack = new ArrayList<>();
    private @Nullable Vec3 serverPosition;

    public void requestLag(final @NotNull LagRequest request) {
        queue.requestLag(request);
    }

    public void releaseExpiredPackets(final @NotNull EnumLagDirection direction, long maxAgeMs) {
        queue.releaseExpiredPackets(direction, maxAgeMs);
    }

    public @Nullable Vec3 getLastReleasedServerPosition() {
        return serverPosition;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSendPacket(final @NotNull SendPacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            clearServerPositions();
            return;
        }

        if (event.isCanceled()) {
            return;
        }

        final @NotNull Packet<?> packet = event.getPacket();

        if (packetFastTrack.remove(packet)) {
            updateServerPosition(packet);
            return;
        }

        if (queue.tick(packet, EnumLagDirection.OUTBOUND)) {
            event.setCanceled(true);
            return;
        }

        updateServerPosition(packet);
    }

    @SubscribeEvent
    public void onReceivePacket(final @NotNull ReceivePacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            clearServerPositions();
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
            clearServerPositions();
            return;
        }

        queue.tick(null, null);
    }

    @Override
    public void forPacket(final @NotNull Packet<?> packet) {
        packetFastTrack.add(packet);
    }

    private void updateServerPosition(final @NotNull Packet<?> packet) {
        if (!(packet instanceof C03PacketPlayer)) {
            return;
        }

        C03PacketPlayer movementPacket = (C03PacketPlayer) packet;
        if (!movementPacket.isMoving()) {
            return;
        }

        serverPosition = new Vec3(
                movementPacket.getPositionX(),
                movementPacket.getPositionY(),
                movementPacket.getPositionZ()
        );
    }

    private void clearServerPositions() {
        serverPosition = null;
    }

}
