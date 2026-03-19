package keystrokesmod.utility;

import keystrokesmod.module.impl.world.AntiBot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

public final class CombatTargeting implements IMinecraftInstance {
    private CombatTargeting() {
    }

    public static EntityPlayer findTarget(double maxDistanceSq) {
        EntityPlayer mouseOverTarget = getMouseOverTarget(maxDistanceSq);
        if (mouseOverTarget != null) {
            return mouseOverTarget;
        }

        return findClosestTarget(maxDistanceSq);
    }

    public static EntityPlayer findClosestTarget(double maxDistanceSq) {
        if (mc == null || mc.theWorld == null) {
            return null;
        }

        EntityPlayer closest = null;
        double closestDistanceSq = Double.MAX_VALUE;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!isValidPlayer(player, maxDistanceSq)) {
                continue;
            }

            double distanceSq = RotationUtils.distanceSqFromEyeToClosestOnAABB(player);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = player;
            }
        }

        return closest;
    }

    public static EntityPlayer getMouseOverTarget(double maxDistanceSq) {
        if (mc == null || mc.objectMouseOver == null) {
            return null;
        }

        MovingObjectPosition objectMouseOver = mc.objectMouseOver;
        return asValidPlayer(objectMouseOver.entityHit, maxDistanceSq);
    }

    public static EntityPlayer asValidPlayer(Entity entity, double maxDistanceSq) {
        if (!(entity instanceof EntityPlayer)) {
            return null;
        }

        EntityPlayer player = (EntityPlayer) entity;
        return isValidPlayer(player, maxDistanceSq) ? player : null;
    }

    public static boolean isValidPlayer(EntityPlayer player, double maxDistanceSq) {
        return isTrackablePlayer(player) && isWithinRange(player, maxDistanceSq);
    }

    public static boolean isTrackablePlayer(EntityPlayer player) {
        if (!Utils.nullCheck() || player == null || player == mc.thePlayer || player.isDead || player.deathTime != 0) {
            return false;
        }

        if (Utils.isFriended(player) || Utils.isTeammate(player) || AntiBot.isBot(player)) {
            return false;
        }

        return true;
    }

    public static boolean isWithinRange(EntityPlayer player, double maxDistanceSq) {
        if (player == null) {
            return false;
        }

        return RotationUtils.distanceSqFromEyeToClosestOnAABB(player) <= maxDistanceSq;
    }
}
