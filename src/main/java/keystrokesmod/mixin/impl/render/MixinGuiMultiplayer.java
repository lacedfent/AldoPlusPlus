package keystrokesmod.mixin.impl.render;

import keystrokesmod.gui.GuiAccountManager;
import keystrokesmod.mixin.impl.accessor.IAccessorGuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMultiplayer.class)
public abstract class MixinGuiMultiplayer {

    private static final int BUTTON_ACCOUNTS = 9001;

    @Shadow
    private GuiScreen parentScreen;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void addAccountsButton(CallbackInfo callbackInfo) {
        ((IAccessorGuiScreen) this).getButtonList().add(
                new GuiButton(BUTTON_ACCOUNTS, ((GuiScreen) (Object) this).width / 2 + 80, 8, 72, 20, "Accounts"));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void handleAccountsButton(GuiButton button, CallbackInfo callbackInfo) {
        if (button.id == BUTTON_ACCOUNTS) {
            GuiAccountManager gui = new GuiAccountManager();
            gui.parentScreen = this.parentScreen;
            ((GuiScreen) (Object) this).mc.displayGuiScreen(gui);
            callbackInfo.cancel();
        }
    }
}