package keystrokesmod.module.impl.movement;

import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;

public class Timer extends Module {
    private SliderSetting speed;

    public Timer() {
        super("Timer", category.movement);
        this.registerSetting(speed = new SliderSetting("Speed", 1.0D, 0.0D, 2.0D, 0.1D));
    }

    @Override
    public String getInfo() {
        return Utils.asWholeNum(speed.getInput());
    }

    @Override
    public void onDisable() {
        Utils.resetTimer();
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) return;

        if (!(mc.currentScreen instanceof ClickGui)) {
            ((IAccessorMinecraft) mc).getTimer().timerSpeed = (float) speed.getInput();
        }
        else {
            Utils.resetTimer();
        }
    }
}
