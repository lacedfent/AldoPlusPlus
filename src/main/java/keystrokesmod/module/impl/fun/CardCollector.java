package keystrokesmod.module.impl.fun;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CardCollector extends Module {
    private enum Rarity {
        COMMON("Common", 0x9E9E9E),
        UNCOMMON("Uncommon", 0x4CAF50),
        RARE("Rare", 0x2196F3),
        EPIC("Epic", 0x9C27B0),
        LEGENDARY("Legendary", 0xFFB300);

        final String label;
        final int color;

        Rarity(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private static class CardDef {
        final String id;
        final String name;
        final String icon;
        final Rarity rarity;

        CardDef(String id, String name, Rarity rarity, String icon) {
            this.id = id;
            this.name = name;
            this.rarity = rarity;
            this.icon = icon;
        }
    }

    private static final int[] NORMAL_WEIGHTS = new int[]{55, 25, 13, 5, 2};
    private static final int[] BOOSTED_WEIGHTS = new int[]{0, 35, 40, 20, 5};
    private static final List<CardDef> CARDS = new ArrayList<>();
    private static final Map<String, Integer> CARD_TEXTURES = new HashMap<>();

    static {
        CARDS.add(new CardDef("grass", "Grass Block", Rarity.COMMON, "grass"));
        CARDS.add(new CardDef("dirt", "Dirt", Rarity.COMMON, "dirt"));
        CARDS.add(new CardDef("stone", "Stone", Rarity.COMMON, "stone"));
        CARDS.add(new CardDef("sand", "Sand", Rarity.COMMON, "sand"));
        CARDS.add(new CardDef("log", "Oak Log", Rarity.COMMON, "log"));
        CARDS.add(new CardDef("cobble", "Cobblestone", Rarity.COMMON, "cobble"));
        CARDS.add(new CardDef("bread", "Bread", Rarity.COMMON, "bread"));
        CARDS.add(new CardDef("book", "Book", Rarity.COMMON, "book"));
        CARDS.add(new CardDef("creeper", "Creeper", Rarity.UNCOMMON, "creeper"));
        CARDS.add(new CardDef("zombie", "Zombie", Rarity.UNCOMMON, "zombie"));
        CARDS.add(new CardDef("skeleton", "Skeleton", Rarity.UNCOMMON, "skeleton"));
        CARDS.add(new CardDef("iron", "Iron Ingot", Rarity.UNCOMMON, "iron"));
        CARDS.add(new CardDef("furnace", "Furnace", Rarity.UNCOMMON, "furnace"));
        CARDS.add(new CardDef("water", "Water Bucket", Rarity.UNCOMMON, "water"));
        CARDS.add(new CardDef("diamond", "Diamond", Rarity.RARE, "diamond"));
        CARDS.add(new CardDef("gold", "Gold Ingot", Rarity.RARE, "gold"));
        CARDS.add(new CardDef("pearl", "Ender Pearl", Rarity.RARE, "pearl"));
        CARDS.add(new CardDef("enderman", "Enderman", Rarity.RARE, "enderman"));
        CARDS.add(new CardDef("tnt", "TNT", Rarity.RARE, "tnt"));
        CARDS.add(new CardDef("blaze", "Blaze", Rarity.EPIC, "blaze"));
        CARDS.add(new CardDef("ghast", "Ghast", Rarity.EPIC, "ghast"));
        CARDS.add(new CardDef("guardian", "Guardian", Rarity.EPIC, "guardian"));
        CARDS.add(new CardDef("witherskel", "Wither Skeleton", Rarity.EPIC, "witherskel"));
        CARDS.add(new CardDef("dragon", "Ender Dragon", Rarity.LEGENDARY, "dragon"));
        CARDS.add(new CardDef("wither", "Wither", Rarity.LEGENDARY, "wither"));
        CARDS.add(new CardDef("apple", "Notch Apple", Rarity.LEGENDARY, "apple"));
        CARDS.add(new CardDef("star", "Nether Star", Rarity.LEGENDARY, "star"));
    }

    private final SliderSetting pointsPerKill;
    private final SliderSetting packCost;
    private final ButtonSetting showHud;
    private final SliderSetting hudX;
    private final SliderSetting hudY;
    private final KeySetting packKey;

    private int points;
    private final Map<String, Integer> owned = new HashMap<>();
    private final List<CardDef> revealCards = new ArrayList<>();
    private long revealStart;
    private boolean overlayOpen;
    private boolean prevMouseDown;
    private boolean prevPackKeyDown;
    private boolean prevDevKeyDown;

    public CardCollector() {
        super("Card Collector", category.fun);
        this.registerSetting(pointsPerKill = new SliderSetting("Points per kill", 10, 1, 50, 1));
        this.registerSetting(packCost = new SliderSetting("Pack cost", 25, 5, 200, 5));
        this.registerSetting(packKey = new KeySetting("Open pack key", Keyboard.KEY_P));
        this.registerSetting(showHud = new ButtonSetting("Show HUD", true));
        this.registerSetting(hudX = new SliderSetting("HUD X", 10, 0, 1000, 5));
        this.registerSetting(hudY = new SliderSetting("HUD Y", 260, 0, 600, 5));
    }

    @Override
    public void onDisable() {
        overlayOpen = false;
        revealCards.clear();
    }

    @Override
    public String getInfo() {
        return points + " pts";
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!Utils.nullCheck()) return;
        if (e.entity == mc.thePlayer || !(e.entity instanceof EntityPlayer)) return;
        EntityPlayer victim = (EntityPlayer) e.entity;
        if (AntiBot.isBot(victim)) return;
        if (e.source == null || e.source.getEntity() == null) return;
        net.minecraft.entity.Entity attacker = e.source.getEntity();
        if (attacker instanceof EntityArrow) {
            attacker = ((EntityArrow) attacker).shootingEntity;
        }
        if (attacker == mc.thePlayer) {
            int gain = (int) pointsPerKill.getInput();
            points += gain;
            mc.thePlayer.playSound("random.orb", 1.0f, 1.5f);
            Utils.sendMessage("&7[&dCards&7] &aKill! &f+" + gain + " &7card points (&f" + points + "&7)");
        }
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) return;
        if (mc.currentScreen == null) {
            boolean packKeyDown = packKey.isPressed();
            if (packKeyDown && !prevPackKeyDown) {
                openPack();
            }
            prevPackKeyDown = packKeyDown;
            boolean devKeyDown = Keyboard.isKeyDown(Keyboard.KEY_NUMPAD5);
            if (devKeyDown && !prevDevKeyDown) {
                points += (int) packCost.getInput();
                mc.thePlayer.playSound("random.orb", 1.0f, 2.0f);
            }
            prevDevKeyDown = devKeyDown;
            if (showHud.isToggled()) {
                boolean down = Mouse.isButtonDown(0);
                if (down && !prevMouseDown) {
                    ScaledResolution sr = new ScaledResolution(mc);
                    int mx = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
                    int my = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
                    handleClick(mx, my, sr);
                }
                prevMouseDown = down;
            }
        }
    }

    private void handleClick(int mx, int my, ScaledResolution sr) {
        if (overlayOpen) {
            int cardW = 100;
            int cardH = 150;
            int gap = 10;
            int totalW = 5 * cardW + 4 * gap;
            int px = (sr.getScaledWidth() - totalW) / 2;
            int py = (sr.getScaledHeight() - cardH) / 2 - 14;
            int bx = sr.getScaledWidth() / 2 - 35;
            int by = py + cardH + 16;
            if (mx >= bx && mx <= bx + 70 && my >= by && my <= by + 14) {
                overlayOpen = false;
                mc.thePlayer.playSound("random.click", 1.0f, 1.0f);
                return;
            }
            long elapsed = System.currentTimeMillis() - revealStart;
            for (int i = 0; i < revealCards.size(); i++) {
                double t = elapsed / 1000.0 - i * 0.12;
                if (t < 0.3) continue;
                int cx = px + i * (cardW + gap);
                if (mx >= cx && mx <= cx + cardW && my >= py && my <= py + cardH) {
                    CardDef card = revealCards.get(i);
                    Utils.sendMessage("&7[&dCards&7] &f" + card.name + " &7(" + card.rarity.label + ") &fx" + owned.getOrDefault(card.id, 0));
                    mc.thePlayer.playSound("random.click", 1.0f, 1.2f);
                    return;
                }
            }
            return;
        }
        int hx = (int) hudX.getInput();
        int hy = (int) hudY.getInput();
        int pw = 122;
        int by = hy + 36;
        if (mx >= hx + 4 && mx <= hx + pw - 4 && my >= by && my <= by + 17) {
            openPack();
        }
    }

    private void openPack() {
        int cost = (int) packCost.getInput();
        if (points < cost) {
            mc.thePlayer.playSound("note.bass", 1.0f, 0.5f);
            return;
        }
        points -= cost;
        Random rand = new Random();
        revealCards.clear();
        for (int i = 0; i < 5; i++) {
            CardDef card = i == 4 ? roll(rand, BOOSTED_WEIGHTS) : roll(rand, NORMAL_WEIGHTS);
            revealCards.add(card);
            owned.put(card.id, owned.getOrDefault(card.id, 0) + 1);
            if (card.rarity == Rarity.EPIC || card.rarity == Rarity.LEGENDARY) {
                Utils.sendMessage("&7[&dCards&7] &b" + card.name + " &7(" + card.rarity.label + ")! nice pull");
            }
        }
        revealStart = System.currentTimeMillis();
        overlayOpen = true;
        mc.thePlayer.playSound("random.chestopen", 1.0f, 1.0f);
    }

    private static CardDef roll(Random rand, int[] weights) {
        int total = 0;
        for (int w : weights) total += w;
        int roll = rand.nextInt(total);
        Rarity[] rarities = Rarity.values();
        for (int i = 0; i < weights.length; i++) {
            if (roll < weights[i]) {
                List<CardDef> pool = new ArrayList<>();
                for (CardDef c : CARDS) {
                    if (c.rarity == rarities[i]) pool.add(c);
                }
                return pool.get(rand.nextInt(pool.size()));
            }
            roll -= weights[i];
        }
        return CARDS.get(0);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Utils.nullCheck()) return;
        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) return;
        if (!showHud.isToggled() && !overlayOpen) return;
        ScaledResolution sr = new ScaledResolution(mc);
        if (showHud.isToggled()) {
            drawHud(sr);
        }
        if (overlayOpen) {
            drawOverlay(sr);
        }
    }

    private void drawHud(ScaledResolution sr) {
        int hx = (int) hudX.getInput();
        int hy = (int) hudY.getInput();
        int pw = 122;
        Gui.drawRect(hx, hy, hx + pw, hy + 57, 0xA0101018);
        Gui.drawRect(hx, hy, hx + pw, hy + 1, 0xFF2E2E42);
        Gui.drawRect(hx, hy + 56, hx + pw, hy + 57, 0xFF2E2E42);
        Gui.drawRect(hx, hy, hx + 1, hy + 57, 0xFF2E2E42);
        Gui.drawRect(hx + pw - 1, hy, hx + pw, hy + 57, 0xFF2E2E42);
        mc.fontRendererObj.drawStringWithShadow("Cards", hx + 5, hy + 3, 0xFFD54F);
        mc.fontRendererObj.drawStringWithShadow("Points: " + points, hx + 5, hy + 14, 0xFFFFFF);
        mc.fontRendererObj.drawStringWithShadow(owned.size() + "/" + CARDS.size() + " cards", hx + 5, hy + 25, 0x9E9E9E);
        int cost = (int) packCost.getInput();
        boolean afford = points >= cost;
        int by = hy + 36;
        Gui.drawRect(hx + 4, by, hx + pw - 4, by + 17, afford ? 0xFF2E7D32 : 0xFF4E342E);
        Gui.drawRect(hx + 4, by, hx + pw - 4, by + 1, 0xFF388E3C);
        String keyName = packKey.getKey() == 0 ? "" : " [" + Keyboard.getKeyName(packKey.getKey()) + "]";
        String btn = "Open Pack (" + cost + " pts)" + keyName;
        while (mc.fontRendererObj.getStringWidth(btn) > pw - 10) {
            btn = btn.substring(0, btn.length() - 1);
        }
        mc.fontRendererObj.drawStringWithShadow(btn, hx + (pw - mc.fontRendererObj.getStringWidth(btn)) / 2, by + 5, afford ? 0xFFFFFF : 0x9E9E9E);
    }

    private void drawOverlay(ScaledResolution sr) {
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        int cardW = 100;
        int cardH = 150;
        int gap = 10;
        int totalW = 5 * cardW + 4 * gap;
        int px = (sw - totalW) / 2;
        int py = (sh - cardH) / 2 - 14;
        Gui.drawRect(0, 0, sw, sh, 0x66000000);
        String title = "NEW CARDS!";
        mc.fontRendererObj.drawStringWithShadow(title, (sw - mc.fontRendererObj.getStringWidth(title)) / 2, py - 26, 0xFFD54F);
        long elapsed = System.currentTimeMillis() - revealStart;
        for (int i = 0; i < revealCards.size(); i++) {
            CardDef card = revealCards.get(i);
            double t = elapsed / 1000.0 - i * 0.12;
            if (t < 0) continue;
            double p = Math.min(t / 0.3, 1.0);
            double sx = Math.sin(p * Math.PI / 2.0);
            int cx = px + i * (cardW + gap) + cardW / 2;
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, py + cardH / 2.0f, 0.0f);
            GlStateManager.scale(sx, 1.0, 1.0);
            drawCardTexture(card, -cardW / 2, -cardH / 2, cardW, cardH);
            GlStateManager.popMatrix();
            if (t >= 0.3) {
                String cnt = "x" + owned.getOrDefault(card.id, 0);
                mc.fontRendererObj.drawStringWithShadow(cnt, cx - cardW / 2 + 4, py + cardH - 12, 0xFFFFFF);
            }
        }
        int bx = sw / 2 - 35;
        int by = py + cardH + 16;
        Gui.drawRect(bx, by, bx + 70, by + 14, 0xFF37474F);
        String close = "Close";
        mc.fontRendererObj.drawStringWithShadow(close, bx + (70 - mc.fontRendererObj.getStringWidth(close)) / 2, by + 4, 0xFFFFFF);
    }

    private void drawCardTexture(CardDef card, int x, int y, int w, int h) {
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.bindTexture(cardTexture(card));
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, w, h, 128, 192);
        if (!texture) GlStateManager.disableTexture2D();
        if (!blend) GlStateManager.disableBlend();
    }

    private static int cardTexture(CardDef card) {
        Integer cached = CARD_TEXTURES.get(card.id);
        if (cached != null) return cached;
        BufferedImage img = new BufferedImage(128, 192, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int rc = card.rarity.color;
        g.setColor(new Color(rc));
        g.fillRoundRect(0, 0, 127, 191, 14, 14);
        g.setColor(new Color(0x14141C));
        g.fillRoundRect(6, 6, 115, 179, 10, 10);
        g.setColor(new Color(0x23232F));
        g.fillRoundRect(10, 10, 108, 120, 8, 8);
        paintIcon(g, card.icon);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 15));
        FontMetrics fm = g.getFontMetrics();
        String name = card.name;
        while (fm.stringWidth(name) > 104 && name.length() > 3) {
            name = name.substring(0, name.length() - 2) + "..";
        }
        g.drawString(name, (128 - fm.stringWidth(name)) / 2, 163);
        g.setColor(new Color(rc));
        g.setFont(new Font("Arial", Font.BOLD, 11));
        fm = g.getFontMetrics();
        String rarity = card.rarity.label.toUpperCase();
        g.drawString(rarity, (128 - fm.stringWidth(rarity)) / 2, 181);
        g.dispose();
        DynamicTexture dt = new DynamicTexture(img);
        dt.updateDynamicTexture();
        int id = dt.getGlTextureId();
        CARD_TEXTURES.put(card.id, id);
        return id;
    }

    private static void paintIcon(Graphics2D g, String icon) {
        g.setStroke(new BasicStroke(6.0f));
        switch (icon) {
            case "grass":
                isoCube(g, new Color(0x7AC74F), new Color(0x6B5638), new Color(0x7A6548));
                g.setColor(new Color(0x6AA84F));
                g.drawLine(22, 45, 62, 68);
                g.drawLine(66, 68, 106, 45);
                break;
            case "dirt":
                isoCube(g, new Color(0xA0794C), new Color(0x805F38), new Color(0x6B4E2D));
                break;
            case "stone":
                isoCube(g, new Color(0x9A9A9A), new Color(0x7E7E7E), new Color(0x6E6E6E));
                break;
            case "sand":
                isoCube(g, new Color(0xE8D9A0), new Color(0xD4C27E), new Color(0xC0AF68));
                break;
            case "log":
                isoCube(g, new Color(0xA8844F), new Color(0x8F6F3F), new Color(0x7A5E34));
                g.setColor(new Color(0x65491F));
                g.fillOval(74, 72, 18, 22);
                g.setColor(new Color(0x8F6F3F));
                g.fillOval(79, 78, 8, 10);
                break;
            case "cobble":
                isoCube(g, new Color(0x8E8E8E), new Color(0x777777), new Color(0x676767));
                g.setColor(new Color(0x5A5A5A));
                g.fillRoundRect(70, 76, 14, 12, 4, 4);
                g.fillRoundRect(88, 82, 12, 10, 4, 4);
                g.fillRoundRect(76, 92, 16, 8, 4, 4);
                break;
            case "tnt":
                isoCube(g, new Color(0xD32F2F), new Color(0xE33B3B), new Color(0xB71C1C));
                g.setColor(new Color(0xE8E8E8));
                g.fillRect(64, 78, 42, 10);
                g.setColor(Color.BLACK);
                g.setFont(new Font("Arial", Font.BOLD, 20));
                g.drawString("TNT", 78, 87);
                break;
            case "furnace":
                isoCube(g, new Color(0x9A9A9A), new Color(0x858585), new Color(0x6F6F6F));
                g.setColor(new Color(0x2E2E2E));
                g.fillRect(76, 76, 18, 18);
                g.setColor(new Color(0x565656));
                g.fillRect(80, 80, 10, 10);
                break;
            case "bread":
                g.setColor(new Color(0xE3B96F));
                g.fillRoundRect(36, 34, 56, 60, 16, 16);
                g.setColor(new Color(0xB8860B));
                g.drawRoundRect(36, 34, 56, 60, 16, 16);
                g.drawLine(42, 46, 42, 88);
                break;
            case "book":
                g.setColor(new Color(0x8B4513));
                g.fillRoundRect(34, 36, 60, 56, 10, 10);
                g.setColor(new Color(0xA0522D));
                g.fillRect(34, 36, 14, 56);
                g.setColor(new Color(0xF5F0E6));
                g.fillRect(48, 42, 42, 44);
                g.setColor(new Color(0x8B4513));
                g.drawLine(56, 42, 56, 86);
                break;
            case "iron":
                ingot(g, new Color(0xD7DDE5), new Color(0x8E99A8));
                break;
            case "gold":
                ingot(g, new Color(0xFFD54F), new Color(0xB8860B));
                break;
            case "diamond":
                g.setColor(new Color(0x4DD0E1));
                fillPoly(g, new int[]{64, 102, 64, 26}, new int[]{16, 64, 112, 64});
                g.setColor(new Color(0xA7EEF7));
                fillPoly(g, new int[]{64, 64, 26}, new int[]{16, 64, 64});
                g.setColor(new Color(0x26A6BD));
                fillPoly(g, new int[]{64, 102, 64}, new int[]{64, 64, 112});
                g.setColor(new Color(0x74DBE9));
                fillPoly(g, new int[]{64, 102, 64}, new int[]{16, 64, 64});
                break;
            case "pearl":
                g.setColor(new Color(0x7B1FA2));
                g.fillOval(26, 26, 76, 76);
                g.setColor(new Color(0xA94FC4));
                g.fillOval(42, 42, 20, 12);
                g.setColor(new Color(0x3A0B52));
                g.fillOval(58, 56, 14, 14);
                break;
            case "water":
                g.setColor(new Color(0xB0BEC5));
                fillPoly(g, new int[]{32, 96, 88, 40}, new int[]{34, 34, 96, 96});
                g.setColor(new Color(0x4FC3F7));
                g.fillOval(28, 24, 72, 20);
                g.setColor(new Color(0x81D4FA));
                g.fillOval(36, 30, 56, 14);
                break;
            case "apple":
                g.setColor(new Color(0xFFB300));
                g.fillOval(26, 28, 76, 76);
                g.setColor(new Color(0xE53935));
                g.fillOval(46, 40, 10, 10);
                g.fillOval(78, 50, 14, 14);
                g.setColor(new Color(0x6D4C41));
                g.setStroke(new BasicStroke(7.0f));
                g.drawLine(64, 30, 64, 16);
                g.setStroke(new BasicStroke(6.0f));
                g.setColor(new Color(0x4CAF50));
                g.drawLine(64, 16, 78, 10);
                break;
            case "star":
                g.setColor(new Color(0x55FFFFFF, true));
                g.fillOval(18, 18, 92, 92);
                g.setColor(Color.WHITE);
                fillPoly(g, new int[]{64, 76, 112, 76, 64, 52, 16, 52}, new int[]{16, 52, 64, 76, 112, 76, 64, 52});
                break;
            case "creeper":
                g.setColor(new Color(0x5FAF54));
                g.fillRoundRect(24, 24, 80, 80, 16, 16);
                g.setColor(Color.BLACK);
                g.fillRect(36, 40, 12, 12);
                g.fillRect(80, 40, 12, 12);
                g.fillRect(40, 64, 48, 10);
                g.fillRect(52, 64, 10, 26);
                g.fillRect(66, 64, 10, 26);
                break;
            case "zombie":
                g.setColor(new Color(0x70A04D));
                g.fillOval(24, 24, 80, 80);
                g.setColor(new Color(0x2B3A1E));
                g.fillRect(38, 42, 12, 10);
                g.fillRect(78, 42, 12, 10);
                g.fillRect(46, 68, 36, 10);
                break;
            case "skeleton":
                g.setColor(new Color(0xE4E4E4));
                g.fillOval(24, 24, 80, 80);
                g.setColor(Color.BLACK);
                g.fillOval(42, 44, 8, 8);
                g.fillOval(78, 44, 8, 8);
                g.fillRect(48, 66, 32, 8);
                break;
            case "enderman":
                g.setColor(new Color(0x181820));
                g.fillRoundRect(38, 18, 52, 92, 12, 12);
                g.setColor(new Color(0xCE93D8));
                g.fillRect(44, 42, 8, 8);
                g.fillRect(76, 42, 8, 8);
                break;
            case "blaze":
                g.setColor(new Color(0xFFB300));
                g.fillOval(26, 26, 76, 76);
                g.setColor(Color.BLACK);
                g.fillRect(42, 44, 10, 10);
                g.fillRect(76, 44, 10, 10);
                g.fillRect(48, 66, 32, 8);
                g.setColor(new Color(0xFF8F00));
                g.setStroke(new BasicStroke(7.0f));
                g.drawLine(10, 52, 24, 58);
                g.drawLine(10, 72, 24, 74);
                g.drawLine(104, 58, 118, 52);
                g.drawLine(104, 74, 118, 72);
                break;
            case "ghast":
                g.setColor(new Color(0xF0F0F0));
                g.fillOval(24, 24, 80, 80);
                g.setColor(new Color(0x9E9E9E));
                g.fillOval(42, 48, 9, 9);
                g.fillOval(77, 48, 9, 9);
                g.drawLine(54, 72, 74, 72);
                break;
            case "guardian":
                g.setColor(new Color(0x6E8B92));
                fillPoly(g, new int[]{28, 46, 28}, new int[]{26, 26, 44});
                fillPoly(g, new int[]{82, 100, 100}, new int[]{26, 26, 44});
                fillPoly(g, new int[]{28, 28, 46}, new int[]{62, 102, 84});
                fillPoly(g, new int[]{100, 100, 82}, new int[]{62, 102, 84});
                g.setColor(new Color(0x86A7AE));
                g.fillRoundRect(26, 26, 76, 76, 14, 14);
                g.setColor(new Color(0x5CE1E6));
                g.fillOval(52, 52, 24, 24);
                g.setColor(new Color(0x1C4E52));
                g.fillOval(60, 60, 8, 8);
                break;
            case "witherskel":
                g.setColor(new Color(0x202024));
                g.fillRoundRect(26, 26, 76, 76, 12, 12);
                g.setColor(Color.WHITE);
                g.fillRect(40, 44, 10, 10);
                g.fillRect(78, 44, 10, 10);
                g.setColor(new Color(0x9E9E9E));
                g.fillRect(42, 70, 44, 8);
                break;
            case "dragon":
                g.setColor(new Color(0x4A148C));
                fillPoly(g, new int[]{28, 48, 30}, new int[]{20, 20, 44});
                fillPoly(g, new int[]{80, 100, 98}, new int[]{20, 20, 44});
                g.setColor(new Color(0x6A1B9A));
                g.fillRoundRect(28, 20, 72, 88, 18, 18);
                g.setColor(new Color(0xE1BEE7));
                g.fillRect(44, 44, 8, 8);
                g.fillRect(76, 44, 8, 8);
                break;
            case "wither":
                g.setColor(new Color(0x101014));
                g.fillRoundRect(30, 24, 68, 80, 16, 16);
                g.setColor(new Color(0x2E2E36));
                g.setStroke(new BasicStroke(4.0f));
                g.drawLine(44, 84, 60, 88);
                g.drawLine(60, 88, 72, 84);
                g.setStroke(new BasicStroke(6.0f));
                g.setColor(Color.WHITE);
                g.fillRect(44, 44, 10, 10);
                g.fillRect(74, 44, 10, 10);
                g.setColor(Color.BLACK);
                g.fillRect(60, 58, 8, 6);
                break;
            default:
                break;
        }
    }

    private static void isoCube(Graphics2D g, Color top, Color left, Color right) {
        g.setColor(top);
        fillPoly(g, new int[]{22, 64, 106, 64}, new int[]{41, 16, 41, 66});
        g.setColor(left);
        fillPoly(g, new int[]{22, 64, 64, 22}, new int[]{41, 66, 102, 77});
        g.setColor(right);
        fillPoly(g, new int[]{64, 106, 106, 64}, new int[]{66, 41, 77, 102});
    }

    private static void fillPoly(Graphics2D g, int[] xs, int[] ys) {
        g.fill(new Polygon(xs, ys, xs.length));
    }

    private static void drawPoly(Graphics2D g, int[] xs, int[] ys) {
        g.draw(new Polygon(xs, ys, xs.length));
    }

    private static void ingot(Graphics2D g, Color fill, Color outline) {
        g.setColor(fill);
        fillPoly(g, new int[]{40, 88, 98, 88, 40, 30}, new int[]{34, 34, 60, 90, 90, 60});
        g.setColor(outline);
        g.setStroke(new BasicStroke(5.0f));
        drawPoly(g, new int[]{40, 88, 98, 88, 40, 30}, new int[]{34, 34, 60, 90, 90, 60});
        g.drawLine(44, 44, 84, 44);
    }
}
