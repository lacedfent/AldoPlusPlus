package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;

public class Fullbright extends Module {

    private float oldGamma;

    public Fullbright() {
        super("Fullbright", category.render, 0);
    }

    @Override
    public void onEnable() {
        oldGamma = mc.gameSettings.gammaSetting;
        mc.gameSettings.gammaSetting = 100.0F;
    }

    @Override
    public void onDisable() {
        mc.gameSettings.gammaSetting = oldGamma;
    }
}