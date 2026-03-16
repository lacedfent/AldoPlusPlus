package keystrokesmod.module.impl.combat;

import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.lwjgl.input.Mouse;

import java.util.Random;

public class AutoClicker extends Module {
    public SliderSetting targetCPS;
    public ButtonSetting simulateExhaust;
    public ButtonSetting notUsingItem;
    public ButtonSetting breakBlocks;
    public ButtonSetting weaponOnly;
    public ButtonSetting disableCreative;

    private long nextClickTime;
    private boolean isHoldingBlockBreak;

    private Random rand;

    public AutoClicker() {
        super("AutoClicker", category.combat, 0);
        this.registerSetting(new DescriptionSetting("Best with delay remover."));
        this.registerSetting(targetCPS = new SliderSetting("Target CPS", 10.0, 1.0, 20.0, 0.5));
        this.registerSetting(simulateExhaust = new ButtonSetting("Simulate exhaust", true));
        this.registerSetting(notUsingItem = new ButtonSetting("Not using item", false));
        this.registerSetting(breakBlocks = new ButtonSetting("Break blocks", false));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(disableCreative = new ButtonSetting("Disable in creative", false));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        this.rand = new Random();
        this.nextClickTime = 0L;
        this.isHoldingBlockBreak = false;
    }

    @Override
    public void onDisable() {
        this.nextClickTime = 0L;
        this.isHoldingBlockBreak = false;
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!Utils.nullCheck()) return;
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) return;

        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        if (Mouse.isButtonDown(0)) {
            long now = System.currentTimeMillis();
            if (nextClickTime == 0) {
                nextClickTime = now + nextDelay();
            }

            int clicks = 0;
            while (nextClickTime <= now) {
                clicks++;
                nextClickTime += nextDelay();
            }

            if (notUsingItem.isToggled() && mc.thePlayer.isUsingItem()) return;
            if (disableCreative.isToggled() && mc.thePlayer.capabilities.isCreativeMode) return;
            if (mc.currentScreen != null || !mc.inGameHasFocus) return;
            if (weaponOnly.isToggled() && !Utils.holdingWeapon()) return;

            if (breakBlocks.isToggled() && mc.objectMouseOver != null) {
                BlockPos pos = mc.objectMouseOver.getBlockPos();
                if (pos != null) {
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (block != Blocks.air && !(block instanceof BlockLiquid)) {
                        if (!this.isHoldingBlockBreak) {
                            KeyBinding.setKeyBindState(key, true);
                            ReflectionUtils.setButton(0, true);
                            this.isHoldingBlockBreak = true;
                        }
                        return;
                    }
                    if (this.isHoldingBlockBreak) {
                        KeyBinding.setKeyBindState(key, false);
                        ReflectionUtils.setButton(0, false);
                        this.isHoldingBlockBreak = false;
                        return;
                    }
                } else {
                    this.isHoldingBlockBreak = false;
                }
            }

            for (int i = 0; i < clicks; i++) {
                KeyBinding.setKeyBindState(key, true);
                KeyBinding.onTick(key);
                ReflectionUtils.setButton(0, true);
            }
        } else {
            this.nextClickTime = 0L;
            this.isHoldingBlockBreak = false;
            KeyBinding.setKeyBindState(key, false);
            ReflectionUtils.setButton(0, false);
        }
    }

    private long nextDelay() {
        int target = Math.max(1, (int) targetCPS.getInput());
        int baseDelay = 1000 / target;

        int finalDelay;

        if (simulateExhaust.isToggled()) {
            int variation = rand.nextInt(baseDelay + 1) - (baseDelay / 2);
            finalDelay = baseDelay + variation;

            if (rand.nextInt(100) < 15) {
                if (rand.nextBoolean()) {
                    finalDelay = 25 + rand.nextInt(16);
                } else {
                    finalDelay = baseDelay + 50 + rand.nextInt(41);
                }
            }

            if (rand.nextInt(100) < 8) {
                int spikeMult = 50 + rand.nextInt(151);
                finalDelay = (finalDelay * spikeMult) / 100;
            }

            if (rand.nextInt(100) < 10) {
                finalDelay += 10 + rand.nextInt(26);
            }
        } else {
            finalDelay = baseDelay + (rand.nextInt(21) - 10);
        }

        return Math.max(33, Math.min(180, finalDelay));
    }
}
