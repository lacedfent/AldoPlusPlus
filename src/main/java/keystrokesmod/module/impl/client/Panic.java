package keystrokesmod.module.impl.client;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import net.minecraftforge.common.MinecraftForge;

public class Panic extends Module {

    public Panic() {
        super("Panic", category.client);
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen instanceof ClickGui) {
            mc.displayGuiScreen(null);
        }

        for (Module module : ModuleManager.modules) {
            if (module != this && module.isEnabled()) {
                module.disable();
            }
        }

        Raven.unloaded = true;

        MinecraftForge.EVENT_BUS.unregister(Raven.instance);
        MinecraftForge.EVENT_BUS.unregister(Raven.debugHelper);
        MinecraftForge.EVENT_BUS.unregister(Raven.mouseHelper);
        MinecraftForge.EVENT_BUS.unregister(Raven.rotationHelper);
        MinecraftForge.EVENT_BUS.unregister(Raven.eventKeyStrokeRenderer);
        MinecraftForge.EVENT_BUS.unregister(Raven.pingHelper);
        if (Raven.packetsHandler != null) {
            MinecraftForge.EVENT_BUS.unregister(Raven.packetsHandler);
        }
        MinecraftForge.EVENT_BUS.unregister(Raven.moduleUtils);
        if (Raven.lagHandler != null) {
            MinecraftForge.EVENT_BUS.unregister(Raven.lagHandler);
        }
        MinecraftForge.EVENT_BUS.unregister(Raven.blockHighlightSharedHandler);

        System.out.println("[Aldo++] Client unloaded by Panic.");

        this.disable();
    }
}