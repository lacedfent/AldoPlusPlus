package keystrokesmod.mixin.impl.accessor;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@SideOnly(Side.CLIENT)
@Mixin(GuiScreen.class)
public interface IAccessorGuiScreen {
    @Accessor("buttonList")
    List<GuiButton> getButtonList();

    @Invoker("mouseClicked")
    void callMouseClicked(int mouseX, int mouseY, int mouseButton);
}
