package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.impl.client.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(LayerCape.class)
public class MixinLayerCape {
    @Shadow
    private final RenderPlayer playerRenderer;

    public MixinLayerCape(RenderPlayer playerRendererIn) {
        this.playerRenderer = playerRendererIn;
    }

    @Redirect(method = "doRenderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;isWearing(Lnet/minecraft/entity/player/EnumPlayerModelParts;)Z"))
    private boolean modifyIsWearing(AbstractClientPlayer player, EnumPlayerModelParts part) {
        if (player.equals(Minecraft.getMinecraft().thePlayer) && Settings.customCapes.getInput() > 0) {
            return true;
        }
        return player.isWearing(part);
    }

    @Redirect(method = "doRenderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;getLocationCape()Lnet/minecraft/util/ResourceLocation;"))
    private ResourceLocation modifyGetLocationCape(AbstractClientPlayer player) {
        int capeIndex = (int) (Settings.customCapes.getInput() - 1);
        if (player.equals(Minecraft.getMinecraft().thePlayer) && capeIndex >= 0 && capeIndex < Settings.loadedCapes.size()) {
            if (capeIndex + 1 == Settings.animatedCapeIndex && Settings.animatedCape != null) {
                Settings.animatedCape.update();
            }
            return Settings.loadedCapes.get(capeIndex);
        }
        return player.getLocationCape();
    }
}