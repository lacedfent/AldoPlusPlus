package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ChestESP extends Module {
    private ColorSetting color;
    private ButtonSetting rainbow, outline, shade, disableIfOpened;

    public ChestESP() {
        super("ChestESP", Module.category.render, 0);
        this.registerSetting(color = new ColorSetting("Color", 0, 0, 255));
        this.registerSetting(rainbow = new ButtonSetting("Rainbow", false));
        this.registerSetting(outline = new ButtonSetting("Outline", false));
        this.registerSetting(shade = new ButtonSetting("Shade", false));
        this.registerSetting(disableIfOpened = new ButtonSetting("Disable if opened", false));
    }

    @SubscribeEvent
    public void o(RenderWorldLastEvent ev) {
        if (!Utils.nullCheck()) {
            return;
        }
        int rgb = rainbow.isToggled() ? Utils.getChroma(2L, 0L) : color.getColor();
        for (TileEntity tileEntity : mc.theWorld.loadedTileEntityList) {
            if (tileEntity instanceof TileEntityChest) {
                if (disableIfOpened.isToggled() && ((TileEntityChest) tileEntity).lidAngle > 0.0f) {
                    continue;
                }
                RenderUtils.renderChest(tileEntity.getPos(), rgb, outline.isToggled(), shade.isToggled());
            } else {
                if (!(tileEntity instanceof TileEntityEnderChest)) {
                    continue;
                }
                if (disableIfOpened.isToggled() && ((TileEntityEnderChest) tileEntity).lidAngle > 0.0f) {
                    continue;
                }
                RenderUtils.renderChest(tileEntity.getPos(), rgb, outline.isToggled(), shade.isToggled());
            }
        }
    }
}
