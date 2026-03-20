package keystrokesmod.mixin.impl.client;

import keystrokesmod.event.*;
import net.minecraft.util.MovingObjectPosition;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.Freelook;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.impl.player.FastMine;
import org.objectweb.asm.Opcodes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V", shift = At.Shift.BEFORE))
    public void onBeforeGetMouseOver(CallbackInfo ci) {
        RotationHelper.get().updateServerRotations();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V", shift = At.Shift.AFTER))
    public void onRunTickMouseOver(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PostMouseSelectionEvent());
    }

    @Inject(method = "runTick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/settings/GameSettings;chatVisibility:Lnet/minecraft/entity/player/EntityPlayer$EnumChatVisibility;"))
    private void injectBeforeChatVisibility(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PrePlayerInteractEvent());
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", ordinal = 2))
    private void onRunTick(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PreInputEvent());
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    public void onRunGameLoop(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new RunGameLoopEvent());
    }

    @Inject(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Timer;updateTimer()V", shift = At.Shift.AFTER))
    private void raven$pumpInputWhenTimerFrozen(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (((IAccessorMinecraft) (Object) this).getTimer().timerSpeed == 0.0F) {
            keystrokesmod.utility.FrozenEntitySync.get().pumpFrame();
            if (mc.currentScreen == null) {
                raven$frozenNoGui(mc);
            } else {
                raven$frozenGui(mc);
            }
        }
    }

    @Inject(method = "clickMouse", at = @At("HEAD"), cancellable = true)
    public void injectClickMouse(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        MovingObjectPosition mop = mc.objectMouseOver;
        PreAttackEvent preAttack = new PreAttackEvent(mop);
        MinecraftForge.EVENT_BUS.post(preAttack);
        if (preAttack.isCanceled()) {
            ci.cancel();
            return;
        }
        MinecraftForge.EVENT_BUS.post(new ClickMouseEvent());
    }

    @Inject(method = "rightClickMouse", at = @At("HEAD"))
    public void injectRightClickMouse(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new RightClickMouseEvent());
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    public void onRunTickStart(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new GameTickEvent());
    }

    @Inject(
        method = "runTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;sendClickBlockToController(Z)V",
            shift = At.Shift.AFTER
        )
    )
    private void raven$fastMinePassiveBlockHitDelay(CallbackInfo ci) {
        FastMine fm = ModuleManager.fastMine;
        if (fm != null) {
            fm.tickPassiveBlockHitDecay((Minecraft) (Object) this);
        }
    }

    @Inject(method = "displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V", at = @At("HEAD"))
    public void onDisplayGuiScreen(GuiScreen guiScreen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        GuiScreen previousGui = mc.currentScreen;
        GuiScreen setGui = guiScreen;
        boolean opened = setGui != null;
        if (!opened) {
            setGui = previousGui;
        }

        GuiUpdateEvent event = new GuiUpdateEvent(setGui, opened);
        MinecraftForge.EVENT_BUS.post(event);
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;changeCurrentItem(I)V"))
    public void changeCurrentItem(InventoryPlayer inventoryPlayer, int slot) {
        PreSlotScrollEvent event = new PreSlotScrollEvent(slot, inventoryPlayer.currentItem);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return;
        }
        inventoryPlayer.changeCurrentItem(slot);
    }

    @Redirect(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I", opcode = Opcodes.PUTFIELD))
    private void onSetThirdPersonView(GameSettings gameSettings, int value) {
        if (ModuleManager.freelook != null && Freelook.perspectiveToggled) {
            ModuleManager.freelook.resetPerspective();
        } else {
            gameSettings.thirdPersonView = value;
        }
    }

    @Redirect(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.PUTFIELD))
    private void onSetCurrentItem(InventoryPlayer inventoryPlayer, int slot) {
        SlotUpdateEvent e = new SlotUpdateEvent(slot);
        MinecraftForge.EVENT_BUS.post(e);
        if (e.isCanceled()) {
            return;
        }
        inventoryPlayer.currentItem = slot;
    }

    /**
     * Frozen path when no GUI is open: pump raw input, keep KeyBinding state in sync,
     * call module keybinds, then explicitly mirror the vanilla chat-open checks that
     * would normally run inside runTick().
     */
    @Unique
    private void raven$frozenNoGui(Minecraft mc) {
        // MOUSE: keep KeyBinding state in sync; no screen to forward events to
        while (org.lwjgl.input.Mouse.next()) {
            int button = org.lwjgl.input.Mouse.getEventButton();
            boolean down = org.lwjgl.input.Mouse.getEventButtonState();
            if (button != -1) {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(button - 100, down);
                if (down) net.minecraft.client.settings.KeyBinding.onTick(button - 100);
            }
        }

        // KEYBOARD: use vanilla key mapping (char+256 when key==0 for special chars)
        while (org.lwjgl.input.Keyboard.next()) {
            int key = org.lwjgl.input.Keyboard.getEventKey() == 0
                    ? org.lwjgl.input.Keyboard.getEventCharacter() + 256
                    : org.lwjgl.input.Keyboard.getEventKey();
            boolean down = org.lwjgl.input.Keyboard.getEventKeyState();

            net.minecraft.client.settings.KeyBinding.setKeyBindState(key, down);
            if (down) {
                net.minecraft.client.settings.KeyBinding.onTick(key);
                ((IAccessorMinecraft) (Object) this).invokeDispatchKeypresses();
            }
        }

        // Mirror the vanilla chat-open checks from runTick() that we are skipping.
        boolean chatVisible = mc.gameSettings.chatVisibility
                != net.minecraft.entity.player.EntityPlayer.EnumChatVisibility.HIDDEN;
        while (mc.gameSettings.keyBindChat.isPressed() && chatVisible) {
            mc.displayGuiScreen(new net.minecraft.client.gui.GuiChat());
        }
        if (mc.currentScreen == null && mc.gameSettings.keyBindCommand.isPressed() && chatVisible) {
            mc.displayGuiScreen(new net.minecraft.client.gui.GuiChat("/"));
        }

        // Module keybinds — only when no GUI was opened by the checks above
        if (mc.currentScreen == null) {
            keystrokesmod.Raven.handleFrozenKeybinds();
        }
    }

    /**
     * Frozen path when a GUI is already open: forward input to the screen and tick it.
     * Matches vanilla: when allowUserInput is false (e.g. GuiChat), vanilla does NOT
     * update KeyBinding state for gameplay binds, so we must not either. Otherwise
     * typed letters get pressTime queued and trigger inventory/drop etc. on timer resume.
     */
    @Unique
    private void raven$frozenGui(Minecraft mc) {
        GuiScreen screen = mc.currentScreen;
        if (screen == null) return;

        boolean allowUserInput = screen.allowUserInput;

        if (!allowUserInput) {
            while (org.lwjgl.input.Mouse.next()) {
                try {
                    screen.handleMouseInput();
                } catch (java.io.IOException ignored) {}
            }
            while (org.lwjgl.input.Keyboard.next()) {
                try {
                    screen.handleKeyboardInput();
                } catch (java.io.IOException ignored) {}
            }
        } else {
            // allowUserInput == true: keep KeyBinding state in sync and forward to screen.
            while (org.lwjgl.input.Mouse.next()) {
                int button = org.lwjgl.input.Mouse.getEventButton();
                boolean down = org.lwjgl.input.Mouse.getEventButtonState();
                if (button != -1) {
                    net.minecraft.client.settings.KeyBinding.setKeyBindState(button - 100, down);
                    if (down) net.minecraft.client.settings.KeyBinding.onTick(button - 100);
                }
                try {
                    screen.handleMouseInput();
                } catch (java.io.IOException ignored) {}
            }
            while (org.lwjgl.input.Keyboard.next()) {
                int key = org.lwjgl.input.Keyboard.getEventKey() == 0
                        ? org.lwjgl.input.Keyboard.getEventCharacter() + 256
                        : org.lwjgl.input.Keyboard.getEventKey();
                boolean down = org.lwjgl.input.Keyboard.getEventKeyState();

                net.minecraft.client.settings.KeyBinding.setKeyBindState(key, down);
                if (down) net.minecraft.client.settings.KeyBinding.onTick(key);

                try {
                    screen.handleKeyboardInput();
                } catch (java.io.IOException ignored) {}
            }
        }

        screen.updateScreen();
    }
}