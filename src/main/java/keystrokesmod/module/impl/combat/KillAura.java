package keystrokesmod.module.impl.combat;

import keystrokesmod.Raven;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.minigames.SkyWars;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityGiantZombie;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.*;

public class KillAura extends Module {
    private SliderSetting targetCPS;
    private SliderSetting fov;
    private SliderSetting attackRange;
    private SliderSetting aimRange;
    public SliderSetting rotationMode;
    private SliderSetting speed;
    private SliderSetting sortMode;
    private SliderSetting switchDelay;
    private SliderSetting targets;
    private ButtonSetting attackMobs;
    private ButtonSetting targetInvis;
    private ButtonSetting disableInInventory;
    private ButtonSetting disableWhileMining;
    private ButtonSetting aimThroughBlocks;
    private ButtonSetting ignoreTeammates;
    private ButtonSetting prioritizeEnemies;
    private ButtonSetting notUsingItem;
    private ButtonSetting requireMouseDown;
    private ButtonSetting weaponOnly;

    private String[] rotationModes = new String[]{"Silent", "Lock view", "None"};
    private String[] sortModes = new String[]{"Distance", "Health", "Hurt time", "Yaw"};

    public static EntityLivingBase target;
    public static EntityLivingBase attackingEntity;
    private HashMap<Integer, Integer> hitMap = new HashMap<>();
    private List<Entity> hostileMobs = new ArrayList<>();
    private Map<Integer, Boolean> golems = new HashMap<>();

    private long nextClickTime;
    private Random rand;

    public KillAura() {
        super("KillAura", category.combat);
        this.registerSetting(targetCPS = new SliderSetting("Target CPS", 10.0, 1.0, 20.0, 0.5));
        this.registerSetting(fov = new SliderSetting("FOV", "°", 360.0, 30.0, 360.0, 4.0));
        this.registerSetting(attackRange = new SliderSetting("Range (attack)", 3.0, 3.0, 6.0, 0.05));
        this.registerSetting(aimRange = new SliderSetting("Range (aim)", 4.5, 3.0, 8.0, 0.05));
        this.registerSetting(rotationMode = new SliderSetting("Rotation mode", 0, rotationModes));
        this.registerSetting(speed = new SliderSetting("Speed", 10, 1, 30, 1));
        this.registerSetting(sortMode = new SliderSetting("Sort mode", 0, sortModes));
        this.registerSetting(switchDelay = new SliderSetting("Switch delay", "ms", 200.0, 50.0, 1000.0, 25.0));
        this.registerSetting(targets = new SliderSetting("Targets", 3.0, 1.0, 10.0, 1.0));
        this.registerSetting(targetInvis = new ButtonSetting("Target invis", true));
        this.registerSetting(attackMobs = new ButtonSetting("Attack mobs", false));
        this.registerSetting(aimThroughBlocks = new ButtonSetting("Aim through blocks", true));
        this.registerSetting(disableInInventory = new ButtonSetting("Disable in inventory", true));
        this.registerSetting(disableWhileMining = new ButtonSetting("Disable while mining", false));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(notUsingItem = new ButtonSetting("Not using item", false));
        this.registerSetting(prioritizeEnemies = new ButtonSetting("Prioritize enemies", false));
        this.registerSetting(requireMouseDown = new ButtonSetting("Require mouse down", false));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
    }

    @Override
    public String getInfo() {
        if (rotationMode.getInput() == 2) {
            return (int) this.fov.getInput() + fov.getSuffix();
        }
        return rotationModes[(int) rotationMode.getInput()];
    }

    @Override
    public void onEnable() {
        rand = new Random();
        nextClickTime = 0L;
    }

    @Override
    public void onDisable() {
        hitMap.clear();
        setTarget(null);
        nextClickTime = 0L;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onClientRotation(ClientRotationEvent e) {
        if (!basicCondition() || !settingCondition()) {
            setTarget(null);
            return;
        }
        handleTarget();
        if (target == null) {
            return;
        }
        if (rotationMode.getInput() == 0) {
            double aimRangeVal = aimRange.getInput();
            if (RotationUtils.distanceFromEyeToClosestOnAABB(target) <= aimRangeVal) {
                int speedVal = (int) speed.getInput();
                boolean useBackup = !aimThroughBlocks.isToggled();
                float[] rot = RotationHelper.get().getRotationsToTarget(target, e, speedVal, 100, 100, 0f, useBackup, aimRangeVal);
                if (rot != null) {
                    e.yaw = rot[0];
                    e.pitch = rot[1];
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        if (rotationMode.getInput() == 1 && target != null) {
            double aimRangeVal = aimRange.getInput();
            if (RotationUtils.distanceFromEyeToClosestOnAABB(target) <= aimRangeVal) {
                int speedVal = (int) speed.getInput();
                boolean useBackup = !aimThroughBlocks.isToggled();
                float[] rot = RotationHelper.get().getRotationsToTarget(target, speedVal, 100, 100, 0f, useBackup, aimRangeVal);
                if (rot != null) {
                    mc.thePlayer.rotationYaw = rot[0];
                    mc.thePlayer.rotationPitch = rot[1];
                }
            }
        }

        if (target != null && RotationUtils.distanceFromEyeToClosestOnAABB(target) <= attackRange.getInput()) {
            attackingEntity = target;
        } else {
            attackingEntity = null;
        }
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!Utils.nullCheck()) return;
        if (target == null) return;
        if (RotationUtils.distanceFromEyeToClosestOnAABB(target) > attackRange.getInput()) return;

        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        long now = System.currentTimeMillis();
        if (nextClickTime == 0) {
            nextClickTime = now;
        }
        int clicks = 0;
        while (nextClickTime <= now) {
            clicks++;
            nextClickTime += nextDelay();
        }

        if (!basicCondition() || !settingCondition()) return;
        if (notUsingItem.isToggled() && mc.thePlayer.isUsingItem()) return;

        for (int i = 0; i < clicks; i++) {
            //KeyBinding.setKeyBindState(key, true);
            KeyBinding.onTick(key);
            ReflectionUtils.setButton(0, true);
        }
    }

    @SubscribeEvent
    public void onSetAttackTarget(LivingSetAttackTargetEvent e) {
        if (e.entity != null && !hostileMobs.contains(e.entity)) {
            if (!(e.target instanceof EntityPlayer) || !e.target.getName().equals(mc.thePlayer.getName())) {
                return;
            }
            if (Utils.getBedwarsStatus() == 2 && e.entity instanceof EntityPigZombie) {
                return;
            }
            hostileMobs.add(e.entity);
        }
        if (e.target == null && hostileMobs.contains(e.entity)) {
            hostileMobs.remove(e.entity);
        }
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            hitMap.clear();
            hostileMobs.clear();
            golems.clear();
        }
    }

    private void setTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) {
            target = null;
            attackingEntity = null;
            nextClickTime = 0L;
        } else {
            target = (EntityLivingBase) entity;
        }
    }

    private void handleTarget() {
        List<EntityLivingBase> availableTargets = new ArrayList<>();
        double maxRange = Math.max(attackRange.getInput(), aimRange.getInput());
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == null || entity == mc.thePlayer || entity.isDead) {
                continue;
            }
            if (entity instanceof EntityPlayer) {
                if (Utils.isFriended((EntityPlayer) entity)) {
                    continue;
                }
                if (((EntityPlayer) entity).deathTime != 0) {
                    continue;
                }
                if (AntiBot.isBot(entity) || (Utils.isTeammate(entity) && ignoreTeammates.isToggled())) {
                    continue;
                }
            } else if (entity instanceof EntityCreature && attackMobs.isToggled()) {
                if (((EntityCreature) entity).tasks == null || ((EntityCreature) entity).isAIDisabled() || ((EntityCreature) entity).deathTime != 0) {
                    continue;
                }
                if (!entity.getClass().getCanonicalName().startsWith("net.minecraft.entity.monster.")) {
                    continue;
                }
            } else {
                continue;
            }
            if (entity.isInvisible() && !targetInvis.isToggled()) {
                continue;
            }
            float fovInput = (float) fov.getInput();
            if (fovInput != 360.0f && !Utils.inFov(fovInput, entity)) {
                continue;
            }
            if (mc.thePlayer.getDistanceToEntity(entity) < maxRange + maxRange / 3) {
                availableTargets.add((EntityLivingBase) entity);
            }
        }

        List<KillAuraTarget> toClassTargets = new ArrayList<>();
        for (EntityLivingBase target : availableTargets) {
            double distanceToBB = RotationUtils.distanceFromEyeToClosestOnAABB(target);
            if (distanceToBB > maxRange) {
                continue;
            }
            if (!(target instanceof EntityPlayer) && attackMobs.isToggled() && !isHostile((EntityCreature) target)) {
                continue;
            }
            if (!aimThroughBlocks.isToggled()) {
                double multipointH = 0;
                double multipointV = 0;
                if (!RotationUtils.hasValidAimPoint(target, multipointH, multipointV, maxRange)) {
                    continue;
                }
            }
            toClassTargets.add(new KillAuraTarget(distanceToBB, target.getHealth(), target.hurtTime, RotationUtils.distanceFromYaw(target, false), target.getEntityId(), target instanceof EntityPlayer && Utils.isEnemy((EntityPlayer) target)));
        }

        Comparator<KillAuraTarget> comparator = null;
        switch ((int) sortMode.getInput()) {
            case 0:
                comparator = Comparator.comparingDouble(entity -> entity.distance);
                break;
            case 1:
                comparator = Comparator.comparingDouble(t -> (double) t.health);
                break;
            case 2:
                comparator = Comparator.comparingDouble(t -> (double) t.hurttime);
                break;
            case 3:
                comparator = Comparator.comparingDouble(t -> t.yawDelta);
                break;
        }
        if (prioritizeEnemies.isToggled()) {
            List<KillAuraTarget> enemies = new ArrayList<>();
            for (KillAuraTarget entity : toClassTargets) {
                if (entity.isEnemy) {
                    enemies.add(entity);
                }
            }
            if (!enemies.isEmpty()) {
                toClassTargets = new ArrayList<>(enemies);
            }
        }
        if (sortMode.getInput() != 0) {
            toClassTargets.sort(Comparator.comparingDouble(entity -> entity.distance));
        }
        toClassTargets.sort(comparator);

        double atkRange = attackRange.getInput();
        List<KillAuraTarget> attackTargets = new ArrayList<>();
        for (KillAuraTarget killAuraTarget : toClassTargets) {
            if (killAuraTarget.distance <= atkRange) {
                attackTargets.add(killAuraTarget);
            }
        }

        if (!attackTargets.isEmpty()) {
            int ticksExisted = mc.thePlayer.ticksExisted;
            int switchDelayTicks = (int) (switchDelay.getInput() / 50);
            long noHitTicks = (long) Math.min(attackTargets.size(), targets.getInput()) * switchDelayTicks;
            for (KillAuraTarget auraTarget : attackTargets) {
                Integer firstHit = hitMap.get(auraTarget.entityId);
                if (firstHit == null || ticksExisted - firstHit >= switchDelayTicks) {
                    continue;
                }
                if (auraTarget.distance <= atkRange) {
                    setTarget(mc.theWorld.getEntityByID(auraTarget.entityId));
                    return;
                }
            }

            for (KillAuraTarget auraTarget : attackTargets) {
                Integer firstHit = hitMap.get(auraTarget.entityId);
                if (firstHit == null || ticksExisted >= firstHit + noHitTicks) {
                    hitMap.put(auraTarget.entityId, ticksExisted);
                    setTarget(mc.theWorld.getEntityByID(auraTarget.entityId));
                    return;
                }
            }
        } else if (!toClassTargets.isEmpty()) {
            KillAuraTarget killAuraTarget = toClassTargets.get(0);
            setTarget(mc.theWorld.getEntityByID(killAuraTarget.entityId));
        } else {
            setTarget(null);
        }
    }

    private boolean isHostile(EntityCreature entityCreature) {
        if (SkyWars.onlyAuraHostiles()) {
            if (entityCreature instanceof EntityGiantZombie) {
                return false;
            }
            return !ModuleManager.skyWars.spawnedMobs.contains(entityCreature.getEntityId());
        } else if (entityCreature instanceof EntitySilverfish) {
            String teamColor = Utils.getFirstColorCode(entityCreature.getCustomNameTag());
            String teamColorSelf = Utils.getFirstColorCode(mc.thePlayer.getDisplayName().getFormattedText());
            return teamColor.isEmpty() || (!teamColorSelf.equals(teamColor) && !Utils.isTeammate(entityCreature));
        } else if (entityCreature instanceof EntityIronGolem) {
            if (Utils.getBedwarsStatus() != 2) {
                return true;
            }
            if (!golems.containsKey(entityCreature.getEntityId())) {
                double nearestDistance = -1;
                EntityArmorStand nearestArmorStand = null;
                for (Entity entity : mc.theWorld.loadedEntityList) {
                    if (!(entity instanceof EntityArmorStand)) {
                        continue;
                    }
                    String stripped = Utils.stripString(entity.getDisplayName().getFormattedText());
                    if (stripped.contains("[") && stripped.endsWith("]")) {
                        double distanceSq = entity.getDistanceSq(entityCreature.posX, entityCreature.posY, entityCreature.posZ);
                        if (distanceSq < nearestDistance || nearestDistance == -1) {
                            nearestDistance = distanceSq;
                            nearestArmorStand = (EntityArmorStand) entity;
                        }
                    }
                }
                if (nearestArmorStand != null) {
                    String teamColor = Utils.getFirstColorCode(nearestArmorStand.getDisplayName().getFormattedText());
                    String teamColorSelf = Utils.getFirstColorCode(mc.thePlayer.getDisplayName().getFormattedText());
                    boolean isTeam = !teamColor.isEmpty() && (teamColorSelf.equals(teamColor) || Utils.isTeammate(nearestArmorStand));
                    golems.put(entityCreature.getEntityId(), isTeam);
                    return !isTeam;
                }
                return !ModuleManager.bedwars.spawnedMobs.contains(entityCreature.getEntityId());
            } else {
                return !golems.getOrDefault(entityCreature.getEntityId(), false);
            }
        } else if (entityCreature instanceof EntityPigZombie && Utils.getBedwarsStatus() != 2) {
            return false;
        }
        return hostileMobs.contains(entityCreature);
    }

    private boolean basicCondition() {
        if (!Utils.nullCheck()) {
            return false;
        }
        return !mc.thePlayer.isDead;
    }

    private boolean settingCondition() {
        if (requireMouseDown.isToggled() && !Mouse.isButtonDown(0)) {
            return false;
        } else if (weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            return false;
        } else if (disableWhileMining.isToggled() && Utils.isMining()) {
            return false;
        } else if (disableInInventory.isToggled() && mc.currentScreen != null) {
            return false;
        } else
            return ModuleManager.bedAura == null || !ModuleManager.bedAura.isEnabled() || ModuleManager.bedAura.allowAura.isToggled() || ModuleManager.bedAura.currentBlock == null;
    }

    private long nextDelay() {
        int cps = Math.max(1, (int) targetCPS.getInput());
        int baseDelay = 1000 / cps;
        int finalDelay = baseDelay + (rand.nextInt(21) - 10);
        return Math.max(33, Math.min(180, finalDelay));
    }

    static class KillAuraTarget {
        double distance;
        float health;
        int hurttime;
        double yawDelta;
        int entityId;
        boolean isEnemy;

        public KillAuraTarget(double distance, float health, int hurttime, double yawDelta, int entityId, boolean isEnemy) {
            this.distance = distance;
            this.health = health;
            this.hurttime = hurttime;
            this.yawDelta = yawDelta;
            this.entityId = entityId;
            this.isEnemy = isEnemy;
        }
    }
}
