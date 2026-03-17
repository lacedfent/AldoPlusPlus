package keystrokesmod.module.impl.movement;

import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.FrozenEntitySync;
import keystrokesmod.utility.Utils;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Timer extends Module {
    private SliderSetting speed;
    private ButtonSetting updateEntities;
    private boolean wasFrozen;

    public Timer() {
        super("Timer", category.movement);
        this.registerSetting(speed = new SliderSetting("Speed", 1.0D, 0.0D, 2.0D, 0.1D));
        this.registerSetting(updateEntities = new ButtonSetting("Update entities", false));
    }

    @Override
    public String getInfo() {
        return Utils.asWholeNum(speed.getInput());
    }

    @Override
    public void onEnable() {
        wasFrozen = false;
    }

    @Override
    public void onDisable() {
        Utils.resetTimer();
        if (wasFrozen) {
            FrozenEntitySync.get().stop();
            wasFrozen = false;
        }
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) return;

        if (!(mc.currentScreen instanceof ClickGui)) {
            float spd = (float) speed.getInput();
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = spd;

            if (spd == 0.0F && updateEntities.isToggled()) {
                if (!wasFrozen) {
                    FrozenEntitySync.get().start();
                    wasFrozen = true;
                }
            } else {
                if (wasFrozen) {
                    FrozenEntitySync.get().stop();
                    wasFrozen = false;
                }
            }
        } else {
            Utils.resetTimer();
            if (wasFrozen) {
                FrozenEntitySync.get().stop();
                wasFrozen = false;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!isEnabled()) return;
        if (!FrozenEntitySync.get().isActive()) return;
        if (e.isCanceled()) return;

        if (FrozenEntitySync.get().intercept(e.getPacket())) {
            e.setCanceled(true);
        }
    }

    public boolean isFrozen() {
        return isEnabled() && wasFrozen;
    }
}
