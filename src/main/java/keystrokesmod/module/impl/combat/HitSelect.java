package keystrokesmod.module.impl.combat;

import keystrokesmod.event.PreAttackEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.CombatTargeting;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HitSelect extends Module {
    private static final double HIT_RANGE = 3.0D;
    private static final double HIT_RANGE_SQ = HIT_RANGE * HIT_RANGE;
    private static final long SERVER_CONFIRM_COOLDOWN_MS = 500L;
    private static final long SERVER_CONFIRM_TIMEOUT_MS = 1500L;

    private static final int BLOCK_WAIT_FIRST = 1;
    private static final int BLOCK_SERVER_COOLDOWN = 1 << 3;
    private static final int BLOCK_PREDICTED_BURST = 1 << 4;
    private static final int BLOCK_CRITICALS = 1 << 5;

    private final SliderSetting pauseDuration;
    private final SliderSetting mode;
    private final SliderSetting waitForFirstHit;
    private final ButtonSetting disableDuringKnockback;
    private final ButtonSetting onlyWhileDamaged;
    private final ButtonSetting useServerAttackTime;
    private final ButtonSetting fakeSwing;
    private final SliderSetting inCombatCancelRate;
    private final SliderSetting missedSwingsCancelRate;

    private final String[] modes = new String[] { "Burst", "Criticals" };

    private EntityPlayer currentTarget;
    private final Map<Integer, TargetState> targetStates = new HashMap<>();
    private int lastSelfHurtTime;
    private boolean takingKnockback;
    private boolean waitFirstTracking;
    private long waitFirstStartMs;
    private boolean waitFirstUnlocked;

    public HitSelect() {
        super("Hit Select", category.combat);

        this.registerSetting(new DescriptionSetting("Filters unnecessary clicks."));
        this.registerSetting(pauseDuration = new SliderSetting("Pause duration", "ms", 500.0D, 0.0D, 1000.0D, 10.0D));
        this.registerSetting(mode = new SliderSetting("Mode", 0, modes));
        this.registerSetting(waitForFirstHit = new SliderSetting("Wait for first hit", "ms", 0.0D, 0.0D, 500.0D, 10.0D));
        this.registerSetting(disableDuringKnockback = new ButtonSetting("Disable during knockback", false));
        this.registerSetting(onlyWhileDamaged = new ButtonSetting("Only while damaged", false));
        this.registerSetting(useServerAttackTime = new ButtonSetting("Use server attack time", false));
        this.registerSetting(fakeSwing = new ButtonSetting("Fake swing", false));
        this.registerSetting(new DescriptionSetting("Cancel rate"));
        this.registerSetting(inCombatCancelRate = new SliderSetting("In combat", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(missedSwingsCancelRate = new SliderSetting("Missed swings", "%", 0.0D, 0.0D, 100.0D, 1.0D));
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        return modes[(int) mode.getInput()];
    }

    @Override
    public void onEnable() {
        resetAllState();
    }

    @Override
    public void onDisable() {
        resetAllState();
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        if (!Utils.nullCheck() || mc.thePlayer.isDead || mc.theWorld == null) {
            resetAllState();
            return;
        }

        long now = System.currentTimeMillis();
        pruneTargetStates();

        EntityPlayer nextTarget = CombatTargeting.findTarget(HIT_RANGE_SQ);
        updateCurrentTarget(nextTarget, now);
        updateSelfDamage(now);
        updateTargetDamage(now);
    }

    @SubscribeEvent
    public void onPreAttack(PreAttackEvent event) {
        if (!canProcessClicks()) {
            return;
        }

        long now = System.currentTimeMillis();
        ClickType clickType = classifyClick(event.objectMouseOver);

        if (clickType == ClickType.BLOCK_INTERACTION) {
            return;
        }

        if (clickType == ClickType.MISSED_SWING) {
            if (shouldCancel(missedSwingsCancelRate.getInput())) {
                cancelClick(event);
            }
            return;
        }

        EntityPlayer clickedTarget = CombatTargeting.asValidPlayer(event.objectMouseOver == null ? null : event.objectMouseOver.entityHit, HIT_RANGE_SQ);
        if (clickedTarget == null) {
            return;
        }

        updateCurrentTarget(clickedTarget, now);

        int blockMask = getValidHitBlockMask(now);
        boolean shouldBlock = (blockMask & BLOCK_WAIT_FIRST) != 0
                || applyPauseDuration(blockMask, now);
        if (shouldBlock && shouldCancel(inCombatCancelRate.getInput())) {
            cancelClick(event);
            return;
        }

        recordPassedValidHit(clickedTarget, now);
    }

    private boolean canProcessClicks() {
        return Utils.nullCheck() && mc.theWorld != null && mc.thePlayer != null && !mc.thePlayer.isDead;
    }

    private ClickType classifyClick(MovingObjectPosition objectMouseOver) {
        if (objectMouseOver == null) {
            return ClickType.MISSED_SWING;
        }

        if (objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return ClickType.BLOCK_INTERACTION;
        }

        if (objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity entityHit = objectMouseOver.entityHit;
            return CombatTargeting.asValidPlayer(entityHit, HIT_RANGE_SQ) != null ? ClickType.VALID_HIT : ClickType.MISSED_SWING;
        }

        return ClickType.MISSED_SWING;
    }

    private void cancelClick(PreAttackEvent event) {
        if (fakeSwing.isToggled() && Utils.nullCheck()) {
            Utils.setSwinging();
        }

        event.setCanceled(true);
    }

    private void updateCurrentTarget(EntityPlayer nextTarget, long now) {
        if (sameTarget(nextTarget)) {
            if (nextTarget != null) {
                currentTarget = nextTarget;
                getTargetState(nextTarget, now);
            }
            return;
        }

        currentTarget = nextTarget;

        if (nextTarget == null) {
            resetWaitFirstState();
        } else if (!waitFirstTracking) {
            waitFirstTracking = true;
            waitFirstStartMs = now;
            waitFirstUnlocked = false;
        }

        if (nextTarget != null) {
            getTargetState(nextTarget, now);
        }
    }

    private void updateSelfDamage(long now) {
        int hurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = hurtTime > lastSelfHurtTime;

        if (hurtAgain) {
            if (waitFirstTracking && !waitFirstUnlocked) {
                waitFirstUnlocked = true;
            }

            if (!takingKnockback) {
                takingKnockback = true;
            }

            if (currentTarget != null) {
                TargetState state = getTargetState(currentTarget, now);
                state.firstSelfHitSeen = true;
            }
        }

        if (takingKnockback && mc.thePlayer.onGround && !hurtAgain) {
            takingKnockback = false;
        }

        lastSelfHurtTime = hurtTime;
    }

    private void updateTargetDamage(long now) {
        if (currentTarget == null) {
            return;
        }

        TargetState state = getTargetState(currentTarget, now);
        if (state.pendingServerConfirmationMs > 0L && now - state.pendingServerConfirmationMs > SERVER_CONFIRM_TIMEOUT_MS) {
            state.pendingServerConfirmationMs = 0L;
        }

        if (useServerAttackTime.isToggled() && state.pendingServerConfirmationMs > 0L && currentTarget.hurtTime > state.lastObservedTargetHurtTime) {
            state.pendingServerConfirmationMs = 0L;
            state.lastConfirmedTargetDamageMs = now;
            state.rawBlockMask = BLOCK_SERVER_COOLDOWN;
            state.rawBlockStartMs = now;
        }

        state.lastObservedTargetHurtTime = currentTarget.hurtTime;
    }

    private int getValidHitBlockMask(long now) {
        if (currentTarget == null) {
            return 0;
        }

        TargetState state = getTargetState(currentTarget, now);
        if (disableDuringKnockback.isToggled() && isTakingKnockback(now)) {
            return 0;
        }

        int blockMask = 0;

        if (isWaitingForFirstHit(now)) {
            blockMask |= BLOCK_WAIT_FIRST;
        }

        blockMask |= getBurstBlockMask(state, now);

        if (isCriticalsBlocked(state, now)) {
            blockMask |= BLOCK_CRITICALS;
        }

        return blockMask;
    }

    private int getBurstBlockMask(TargetState state, long now) {
        if (useServerAttackTime.isToggled()) {
            if (state.lastConfirmedTargetDamageMs > 0L && now - state.lastConfirmedTargetDamageMs < SERVER_CONFIRM_COOLDOWN_MS) {
                return BLOCK_SERVER_COOLDOWN;
            }

            return 0;
        }

        if (state.pendingLocalBurstUntilMs > now) {
            return BLOCK_PREDICTED_BURST;
        }

        return currentTarget.hurtTime > 0 ? BLOCK_PREDICTED_BURST : 0;
    }

    private boolean isCriticalsBlocked(TargetState state, long now) {
        if ((int) mode.getInput() != 1) {
            return false;
        }

        if (mc.thePlayer.onGround) {
            return false;
        }

        if (onlyWhileDamaged.isToggled() && !state.firstSelfHitSeen) {
            return false;
        }

        if (disableDuringKnockback.isToggled() && isTakingKnockback(now)) {
            return false;
        }

        return !canCriticalHit();
    }

    private boolean isWaitingForFirstHit(long now) {
        if (waitForFirstHit.getInput() <= 0.0D
                || currentTarget == null
                || !waitFirstTracking
                || waitFirstUnlocked
                || waitFirstStartMs <= 0L) {
            return false;
        }

        return now - waitFirstStartMs < (long) waitForFirstHit.getInput();
    }

    private boolean canCriticalHit() {
        return mc.thePlayer.fallDistance > 0.0F
                && !mc.thePlayer.onGround
                && !mc.thePlayer.isOnLadder()
                && !mc.thePlayer.isInWater()
                && !mc.thePlayer.isPotionActive(Potion.blindness)
                && mc.thePlayer.ridingEntity == null;
    }

    private boolean isTakingKnockback(long now) {
        return takingKnockback || mc.thePlayer.hurtTime > 0;
    }

    private boolean applyPauseDuration(int blockMask, long now) {
        if (currentTarget == null) {
            return false;
        }

        return applyPauseDuration(getTargetState(currentTarget, now), blockMask, now);
    }

    private boolean applyPauseDuration(TargetState state, int blockMask, long now) {
        if (state.pendingLocalBurstUntilMs > 0L && now >= state.pendingLocalBurstUntilMs) {
            state.pendingLocalBurstUntilMs = 0L;
        }

        if (blockMask == 0) {
            state.rawBlockMask = 0;
            state.rawBlockStartMs = 0L;
            return false;
        }

        if (pauseDuration.getInput() <= 0.0D) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartMs = now;
            return false;
        }

        if (blockMask != state.rawBlockMask) {
            state.rawBlockMask = blockMask;
            state.rawBlockStartMs = now;
        } else if (state.rawBlockStartMs <= 0L) {
            state.rawBlockStartMs = now;
        }

        return now - state.rawBlockStartMs < (long) pauseDuration.getInput();
    }

    private void recordPassedValidHit(EntityPlayer target, long now) {
        if (target == null) {
            return;
        }

        updateCurrentTarget(target, now);
        TargetState state = getTargetState(target, now);

        if (useServerAttackTime.isToggled()) {
            state.pendingServerConfirmationMs = now;
            state.lastConfirmedTargetDamageMs = 0L;
            return;
        }

        if (pauseDuration.getInput() > 0.0D) {
            state.pendingLocalBurstUntilMs = now + (long) pauseDuration.getInput();
            state.rawBlockMask = BLOCK_PREDICTED_BURST;
            state.rawBlockStartMs = now;
        }
    }

    private boolean shouldCancel(double chance) {
        if (chance <= 0.0D) {
            return false;
        }

        if (chance >= 100.0D) {
            return true;
        }

        return Math.random() * 100.0D < chance;
    }

    private boolean sameTarget(EntityPlayer nextTarget) {
        if (currentTarget == null || nextTarget == null) {
            return currentTarget == nextTarget;
        }

        return currentTarget.getEntityId() == nextTarget.getEntityId();
    }

    private void resetWaitFirstState() {
        waitFirstTracking = false;
        waitFirstStartMs = 0L;
        waitFirstUnlocked = false;
    }

    private TargetState getTargetState(EntityPlayer target, long now) {
        TargetState state = targetStates.get(target.getEntityId());
        if (state == null) {
            state = new TargetState();
            state.lastObservedTargetHurtTime = target.hurtTime;
            targetStates.put(target.getEntityId(), state);
        }
        return state;
    }

    private void pruneTargetStates() {
        if (mc.theWorld == null) {
            targetStates.clear();
            return;
        }

        Iterator<Map.Entry<Integer, TargetState>> iterator = targetStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TargetState> entry = iterator.next();
            Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            if (!(entity instanceof EntityPlayer) || entity.isDead || ((EntityPlayer) entity).deathTime != 0) {
                iterator.remove();
            }
        }
    }

    private void resetAllState() {
        currentTarget = null;
        targetStates.clear();
        lastSelfHurtTime = 0;
        takingKnockback = false;
        resetWaitFirstState();
    }

    private enum ClickType {
        VALID_HIT,
        BLOCK_INTERACTION,
        MISSED_SWING
    }

    private static class TargetState {
        boolean firstSelfHitSeen;
        long lastConfirmedTargetDamageMs;
        long pendingServerConfirmationMs;
        long pendingLocalBurstUntilMs;
        int lastObservedTargetHurtTime;
        long rawBlockStartMs;
        int rawBlockMask;
    }
}
