package keystrokesmod.module.impl.fun;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;

public class BigHead extends Module {
    public static BigHead instance;

    public final SliderSetting size;

    public BigHead() {
        super("Big Head", category.fun, 0);
        this.registerSetting(size = new SliderSetting("Size", 1.6, 1.0, 2.5, 0.1));
    }

    @Override
    public void onEnable() {
        instance = this;
    }

    @Override
    public void onDisable() {
        instance = null;
    }
}