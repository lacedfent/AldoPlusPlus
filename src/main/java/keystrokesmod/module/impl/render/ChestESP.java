package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class ChestESP extends Module {
    private ColorSetting color;
    private ButtonSetting rainbow, outline, shade, disableIfOpened;
    private SliderSetting maxDistance;

    public ChestESP() {
        super("ChestESP", Module.category.render, 0);
        this.registerSetting(color = new ColorSetting("Color", 0, 0, 255));
        this.registerSetting(rainbow = new ButtonSetting("Rainbow", false));
        this.registerSetting(outline = new ButtonSetting("Outline", false));
        this.registerSetting(shade = new ButtonSetting("Shade", false));
        this.registerSetting(disableIfOpened = new ButtonSetting("Disable if opened", false));
        this.registerSetting(maxDistance = new SliderSetting("Max distance", 128.0, 32.0, 256.0, 8.0));
    }

    @SubscribeEvent
    public void o(RenderWorldLastEvent ev) {
        if (!Utils.nullCheck()) {
            return;
        }
        int rgb = rainbow.isToggled() ? Utils.getChroma(2L, 0L) : color.getColor();
        double maxDistSq = maxDistance.getInput() * maxDistance.getInput();
        List<BlockPos> batch = new ArrayList<>();

        for (TileEntity tileEntity : mc.theWorld.loadedTileEntityList) {
            if (tileEntity instanceof TileEntityChest) {
                if (disableIfOpened.isToggled() && ((TileEntityChest) tileEntity).lidAngle > 0.0f) {
                    continue;
                }
            } else if (!(tileEntity instanceof TileEntityEnderChest)) {
                continue;
            } else if (disableIfOpened.isToggled() && ((TileEntityEnderChest) tileEntity).lidAngle > 0.0f) {
                continue;
            }

            BlockPos pos = tileEntity.getPos();
            if (!RenderUtils.isBlockPosWithinDistanceSqToView(pos, maxDistSq)) {
                continue;
            }
            AxisAlignedBB bb = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            if (!RenderUtils.isInViewFrustum(bb)) {
                continue;
            }
            batch.add(pos);
        }

        RenderUtils.renderChestBatch(batch, rgb, outline.isToggled(), shade.isToggled());
    }
}
