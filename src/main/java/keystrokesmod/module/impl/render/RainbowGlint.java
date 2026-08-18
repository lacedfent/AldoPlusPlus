package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;

import java.awt.Color;

public class RainbowGlint extends Module {
    public static RainbowGlint instance;

    public final SliderSetting speed;

    public RainbowGlint() {
        super("Rainbow Glint", category.render, 0);
        this.registerSetting(speed = new SliderSetting("Speed", 1.0, 0.1, 5.0, 0.1));
    }

    @Override
    public void onEnable() {
        instance = this;
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    public static int getColor() {
        float hue = (float) ((System.currentTimeMillis() * 0.001 * instance.speed.getInput()) % 1.0);
        return 0xFF000000 | Color.HSBtoRGB(hue, 1.0F, 1.0F);
    }
}