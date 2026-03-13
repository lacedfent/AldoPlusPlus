package keystrokesmod.module.impl.render;

import keystrokesmod.Raven;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Tracers extends Module {
    public ButtonSetting showInvis;
    public ColorSetting color;
    public ButtonSetting rainbow;
    public SliderSetting lineWidth;

    private boolean viewBobbingEnabled;

    public Tracers() {
        super("Tracers", category.render);
        this.registerSetting(showInvis = new ButtonSetting("Show invis", true));
        this.registerSetting(lineWidth = new SliderSetting("Line Width", 1.0D, 1.0D, 5.0D, 1.0D));
        this.registerSetting(color = new ColorSetting("Color", 0, 255, 0));
        this.registerSetting(rainbow = new ButtonSetting("Rainbow", false));
    }

    @Override
    public void onEnable() {
        this.viewBobbingEnabled = mc.gameSettings.viewBobbing;
        if (this.viewBobbingEnabled) {
            mc.gameSettings.viewBobbing = false;
        }
    }

    @Override
    public void onDisable() {
        mc.gameSettings.viewBobbing = this.viewBobbingEnabled;
    }

    @Override
    public void onUpdate() {
        if (mc.gameSettings.viewBobbing) {
            mc.gameSettings.viewBobbing = false;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        int rgb = rainbow.isToggled() ? Utils.getChroma(2L, 0L) : color.getColor();
        if (Raven.DEBUG) {
            for (Entity en : mc.theWorld.loadedEntityList) {
                if (en instanceof EntityLivingBase && en != mc.thePlayer) {
                    RenderUtils.drawTracerLine(en, rgb, (float) lineWidth.getInput(), e.partialTicks);
                }
            }
        }
        else {
            for (EntityPlayer en : mc.theWorld.playerEntities) {
                if (en == mc.thePlayer) {
                    continue;
                }
                if (en.deathTime != 0) {
                    continue;
                }
                if (!showInvis.isToggled() && en.isInvisible()) {
                    continue;
                }

                if (!AntiBot.isBot(en)) {
                    RenderUtils.drawTracerLine(en, rgb, (float) lineWidth.getInput(), e.partialTicks);
                }
            }
        }
    }
}
