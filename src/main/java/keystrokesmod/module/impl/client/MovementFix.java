package keystrokesmod.module.impl.client;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;

public class MovementFix extends Module {

    public MovementFix() {
        super("MovementFix", category.client);
        this.registerSetting(new DescriptionSetting("Aligns input with rotations"));
    }
}
