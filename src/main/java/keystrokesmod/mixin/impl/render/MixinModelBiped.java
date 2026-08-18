package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.impl.fun.BigHead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBiped.class)
public class MixinModelBiped {
    private Entity renderEntity;

    @Inject(method = "render", at = @At("HEAD"))
    private void captureEntity(Entity entity, float p_78088_2_, float p_78088_3_, float p_78088_4_, float p_78088_5_, float p_78088_6_, float scale, CallbackInfo ci) {
        this.renderEntity = entity;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V", ordinal = 0))
    private void modifyHeadRenderChild(ModelRenderer head, float scale) {
        this.modifyPartRender(head, scale);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V", ordinal = 1))
    private void modifyHeadRenderAdult(ModelRenderer head, float scale) {
        this.modifyPartRender(head, scale);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V", ordinal = 7))
    private void modifyHeadwearRender(ModelRenderer headwear, float scale) {
        this.modifyPartRender(headwear, scale);
    }

    private void modifyPartRender(ModelRenderer part, float scale) {
        if (BigHead.instance != null && this.renderEntity == Minecraft.getMinecraft().thePlayer) {
            float s = (float) BigHead.instance.size.getInput();
            GlStateManager.pushMatrix();
            GlStateManager.scale(s, s, s);
            part.render(scale);
            GlStateManager.popMatrix();
        }
        else {
            part.render(scale);
        }
    }
}