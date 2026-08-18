package keystrokesmod.module.impl.client;

import keystrokesmod.Raven;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class Settings extends Module {
    public static SliderSetting customCapes;
    public static ButtonSetting addBracketsToDistance;
    public static ButtonSetting hideFirstPersonESP;
    public static ButtonSetting setChatAsInventory;
    public static ButtonSetting showHealthAsHearts;
    public static ButtonSetting showHeartSymbol;

    public static ButtonSetting weaponAxe;
    public static ButtonSetting weaponHoe;
    public static ButtonSetting weaponRod;
    public static ButtonSetting weaponShovel;
    public static ButtonSetting weaponStick;

    public static ButtonSetting rotateBody;
    public static ButtonSetting fullBody;
    public static SliderSetting randomYawFactor;

    public static ButtonSetting loadGuiPositions;
    public static ButtonSetting sendMessage;

    public static SliderSetting offset;
    public static SliderSetting timeMultiplier;

    private String[] capes = new String[] { "None", "Aldo++", "Rainbow" };

    public static List<ResourceLocation> loadedCapes = new ArrayList<>();
    public static AnimatedCape animatedCape;
    public static int animatedCapeIndex = -1;

    public Settings() {
        super("Settings", category.client, 0);
        this.registerSetting(new DescriptionSetting("General"));
        this.registerSetting(customCapes = new SliderSetting("Custom cape", 0, capes));
        this.registerSetting(addBracketsToDistance = new ButtonSetting("Add brackets to distance", false));
        this.registerSetting(hideFirstPersonESP = new ButtonSetting("Hide first person self ESP", true));
        this.registerSetting(setChatAsInventory = new ButtonSetting("Set chat as inventory", false));
        this.registerSetting(showHealthAsHearts = new ButtonSetting("Show health as hearts", false));
        this.registerSetting(showHeartSymbol = new ButtonSetting("Show heart symbol", false));
        this.registerSetting(new DescriptionSetting("Extra weapons"));
        this.registerSetting(weaponAxe = new ButtonSetting("Axe", false));
        this.registerSetting(weaponHoe = new ButtonSetting("Hoe", false));
        this.registerSetting(weaponRod = new ButtonSetting("Rod", false));
        this.registerSetting(weaponShovel = new ButtonSetting("Shovel", false));
        this.registerSetting(weaponStick = new ButtonSetting("Stick", true));
        this.registerSetting(new DescriptionSetting("Rotations"));
        this.registerSetting(rotateBody = new ButtonSetting("Rotate body", true));
        this.registerSetting(fullBody = new ButtonSetting("Full body", false));
        this.registerSetting(randomYawFactor = new SliderSetting("Random yaw factor", 0, 0.0, 10.0, 1.0));
        this.registerSetting(new DescriptionSetting("Profiles"));
        this.registerSetting(loadGuiPositions = new ButtonSetting("Load gui state", false));
        this.registerSetting(sendMessage = new ButtonSetting("Send message on enable", true));
        this.registerSetting(new DescriptionSetting("Theme colors"));
        this.registerSetting(offset = new SliderSetting("Offset", 0.5, -3.0, 3.0, 0.1));
        this.registerSetting(timeMultiplier = new SliderSetting("Time multiplier", 0.5, 0.1, 4.0, 0.1));
        this.canBeEnabled = false;
        loadCapes();
    }

    public void loadCapes() {
        try {
            for (int i = 1; i < capes.length; i++) {
                if (capes[i].equals("Rainbow")) {
                    animatedCape = new AnimatedCape();
                    ResourceLocation location = mc.renderEngine.getDynamicTextureLocation("rvn_rainbow", animatedCape.getTexture());
                    loadedCapes.add(location);
                    animatedCapeIndex = i;
                    animatedCape.update();
                    continue;
                }
                String name = capes[i].toLowerCase();
                InputStream stream = Raven.class.getResourceAsStream("/assets/keystrokesmod/textures/capes/" + name + ".png");
                if (stream == null) {
                    continue;
                }
                BufferedImage bufferedImage = ImageIO.read(stream);
                loadedCapes.add(mc.renderEngine.getDynamicTextureLocation(name, new DynamicTexture(bufferedImage)));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class AnimatedCape {
        private static final int WIDTH = 256;
        private static final int HEIGHT = 128;
        private final DynamicTexture texture;
        private final int[] data;

        public AnimatedCape() {
            this.texture = new DynamicTexture(WIDTH, HEIGHT);
            this.data = this.texture.getTextureData();
        }

        public DynamicTexture getTexture() {
            return this.texture;
        }

        public void update() {
            long time = System.currentTimeMillis();
            float phase = (time % 3500L) / 3500.0F;
            float wave = (time % 7000L) / 7000.0F;
            for (int y = 0; y < HEIGHT; y++) {
                float fy = y / (float) HEIGHT;
                float vGradient = 1.0F - 0.35F * fy;
                for (int x = 0; x < WIDTH; x++) {
                    float fx = x / (float) WIDTH;
                    float hue = fx + phase;
                    if (hue >= 1.0F) {
                        hue -= 1.0F;
                    }
                    float ripple = 0.18F * (float) Math.sin(2.0 * Math.PI * (fx * 2.0 + fy * 1.2 + wave));
                    float brightness = Math.max(0.25F, Math.min(1.0F, vGradient + ripple));
                    this.data[y * WIDTH + x] = Color.HSBtoRGB(hue, 1.0F, brightness);
                }
            }
            this.texture.updateDynamicTexture();
        }
    }

    public static boolean inInventory() {
        if (mc.currentScreen instanceof GuiInventory) {
            return true;
        }
        if (mc.currentScreen instanceof GuiChat && setChatAsInventory.isToggled()) {
            return true;
        }
        return false;
    }
}
