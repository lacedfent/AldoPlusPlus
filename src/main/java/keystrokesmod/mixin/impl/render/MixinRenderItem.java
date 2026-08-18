package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.impl.render.RainbowGlint;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderItem.class)
public class MixinRenderItem {
    @ModifyArg(method = "renderEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderModel(Lnet/minecraft/client/resources/model/IBakedModel;I)V", ordinal = 0), index = 1)
    private int rainbowGlintPass1(int color) {
        return RainbowGlint.instance != null ? RainbowGlint.getColor() : color;
    }

    @ModifyArg(method = "renderEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderModel(Lnet/minecraft/client/resources/model/IBakedModel;I)V", ordinal = 1), index = 1)
    private int rainbowGlintPass2(int color) {
        return RainbowGlint.instance != null ? RainbowGlint.getColor() : color;
    }
}