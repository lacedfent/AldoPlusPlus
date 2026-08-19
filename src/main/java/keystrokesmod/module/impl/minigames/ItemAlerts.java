package keystrokesmod.module.impl.minigames;

import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.util.StringUtils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ItemAlerts extends Module {

    private final ButtonSetting chatAlerts;
    private final ButtonSetting hudAlerts;
    private final SliderSetting hudAlertsDuration;
    private final ButtonSetting showSharpness;
    private final ButtonSetting showProtection;
    private final ButtonSetting showTeammates;
    private final ButtonSetting showDistance;
    private final SliderSetting delay;
    private final ButtonSetting debug;

    private final Map<String, ButtonSetting> itemButtons = new HashMap<>();
    private final Map<String, String> itemDisplayColors = new HashMap<>();
    private final Set<String> armorPieceNames = new HashSet<>();
    private final Set<String> heldItemNames = new HashSet<>();
    private final Set<String> heldItemDisplayNames = new HashSet<>();
    private final Set<String> teamUpgrades = new HashSet<>();
    private final Map<String, Map<String, Object>> playerItems = new HashMap<>();
    private final List<HudAlert> alertList = new ArrayList<>();

    private static final String[] COLOR_KEYS = {"c", "9", "a", "e", "b", "f", "d", "8"};
    private static final String CHAT_PREFIX = "&7[&dIA&7]&r&7 ";

    private String myName = "";
    private String myTeam = "";
    private int status = -1;
    private long delayInterval = 15000;
    private long lastWorldTick;

    public ItemAlerts() {
        super("Item Alerts", category.minigames);

        this.registerSetting(chatAlerts = new ButtonSetting("Chat Alerts", true));
        this.registerSetting(hudAlerts = new ButtonSetting("HUD Alerts", true));
        this.registerSetting(hudAlertsDuration = new SliderSetting("Hud Alerts Duration", "s", 5.0, 0.0, 10.0, 0.1));

        this.registerSetting(showSharpness = new ButtonSetting(color("&bSharpness"), true));
        this.registerSetting(showProtection = new ButtonSetting(color("&bProtection"), true));

        registerItemButton("Iron Sword");
        registerItemButton("Diamond Sword");
        registerItemButton("Diamond Pickaxe");
        registerItemButton("Ender Pearl");
        registerItemButton("Obsidian");
        registerItemButton("Bridge Egg");
        registerItemButton("Fireball");
        registerItemButton("TNT");
        registerItemButton("Block Zapper");
        registerItemButton("Bow");
        registerItemButton("Ice Bridge");
        registerItemButton("Sleeping Dust");
        registerItemButton("Machine Gun Bow");
        registerItemButton("Charlie the Unicorn");
        registerItemButton("Unstable Teleportation Device");
        registerItemButton("Miracle of the Stars");
        registerItemButton("Mystic Mirror");
        registerItemButton("Devastator Bow");
        registerItemButton("Speed Potion");
        registerItemButton("Jump Potion");
        registerItemButton("Invisibility Potion");
        registerItemButton("Dream Defender");
        registerItemButton("Iron Leggings");
        registerItemButton("Chainmail Leggings");
        registerItemButton("Diamond Leggings");

        this.registerSetting(showTeammates = new ButtonSetting("Show Teammates", false));
        this.registerSetting(showDistance = new ButtonSetting("Show Distance", true));
        this.registerSetting(delay = new SliderSetting("Delay", "s", 15.0, 0.0, 60.0, 1.0));
        this.registerSetting(debug = new ButtonSetting("Debug", false));

        setupItems();
        this.closetModule = true;
    }

    private void registerItemButton(String key) {
        ButtonSetting setting = new ButtonSetting(color(key), true);
        itemButtons.put(key, setting);
        this.registerSetting(setting);
    }

    @Override
    public void onEnable() {
        playerItems.clear();
        teamUpgrades.clear();
        alertList.clear();
        status = -1;
        lastWorldTick = -1;
    }

    @Override
    public void onUpdate() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        long worldTick = mc.theWorld.getTotalWorldTime();
        if (lastWorldTick == -1 || Math.abs(worldTick - lastWorldTick) > 100) {
            lastWorldTick = worldTick;
            playerItems.clear();
            teamUpgrades.clear();
            alertList.clear();
        }
        if (mc.thePlayer.ticksExisted % 100 == 0) {
            status = getBedwarsStatus();
            myName = mc.thePlayer.getName();
            refreshTeams();
            setupItems();
            delayInterval = (long) (delay.getInput() * 1000);
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.getPacket() instanceof S04PacketEntityEquipment) {
            S04PacketEntityEquipment s04 = (S04PacketEntityEquipment) e.getPacket();
            doAlerts(s04.getEntityID(), s04.getItemStack(), s04.getEquipmentSlot());
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!debug.isToggled()) {
            return;
        }
        if (e.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity c02 = (C02PacketUseEntity) e.getPacket();
            Entity en = c02.getEntityFromWorld(mc.theWorld);
            if (en == null) {
                return;
            }
            if (c02.getAction() != C02PacketUseEntity.Action.ATTACK || !(en instanceof EntityOtherPlayerMP)) {
                return;
            }
            String msg = CHAT_PREFIX + en.getDisplayName().getFormattedText().substring(0, 2) + en.getName() + "&7: ";
            ItemStack item = ((EntityPlayer) en).getHeldItem();
            if (item == null) {
                msg += "&r'null' / 'null'";
            } else {
                msg += "&r'" + item.getItem().getRegistryName() + "&r' &7/ &r'" + item.getDisplayName() + "&r' " + item.stackSize;
            }
            Utils.sendMessage(msg);
        }
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        long now = System.currentTimeMillis();
        alertList.removeIf(alert -> alert.expiry <= now);
        if (alertList.isEmpty()) {
            return;
        }
        ScaledResolution res = new ScaledResolution(mc);
        int x = res.getScaledWidth() - 160;
        int y = res.getScaledHeight() - 24 - alertList.size() * 24;
        for (HudAlert alert : alertList) {
            int alpha = 255;
            long remaining = alert.expiry - now;
            if (remaining < 1000) {
                alpha = (int) (255 * remaining / 1000.0);
            }
            Gui.drawRect(x - 2, y, res.getScaledWidth(), y + 21, 0x88000000);
            this.mc.fontRendererObj.drawStringWithShadow(alert.title, x, y, 0xAA55FF & 0x00FFFFFF | alpha << 24);
            this.mc.fontRendererObj.drawStringWithShadow(alert.message, x, y + 10, 0xFFFFFF & 0x00FFFFFF | alpha << 24);
            y += 24;
        }
    }

    private void doAlerts(int entityId, ItemStack item, int slot) {
        if (status != 3) {
            return;
        }
        Entity entity = mc.theWorld.getEntityByID(entityId);
        if (!(entity instanceof EntityOtherPlayerMP)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) entity;
        long now = System.currentTimeMillis();
        if (player == mc.thePlayer || player.isDead) {
            return;
        }
        String playerDisplay = player.getDisplayName().getFormattedText();
        if (!showTeammates.isToggled() && !myTeam.isEmpty() && playerDisplay.startsWith(color(myTeam))
                || playerDisplay.startsWith(color("7"))) {
            return;
        }
        String uuid = player.getUniqueID().toString();
        String teamColor = playerDisplay.length() > 1 ? playerDisplay.substring(1, 2) : "";
        String team = getColoredTeam(teamColor);
        if (team == null) {
            return;
        }
        String itemName = item == null ? "" : item.getItem().getRegistryName().substring(10);
        String itemDisplayName = item != null ? item.getDisplayName() : "";
        Map<String, Object> existingData = playerItems.get(uuid);
        if (existingData == null) {
            existingData = new HashMap<>();
            playerItems.put(uuid, existingData);
        }

        if (item != null) {
            boolean heldRaw = heldItemNames.contains(itemName);
            boolean heldDisplay = heldItemDisplayNames.contains(itemDisplayName);
            boolean isWeapon = itemName.endsWith("sword");
            boolean hasEnchantments = !EnchantmentHelper.getEnchantments(item).isEmpty();

            if (slot == 0 && (heldRaw || heldDisplay)) {
                String lastItem = String.valueOf(existingData.get("lastitem"));
                String trackedItemName = heldRaw ? itemName : itemDisplayName;
                long lastTime = parseLong(existingData.get(trackedItemName));
                if (now > lastTime && !lastItem.equals(itemName)) {
                    String coloredName = color("&" + teamColor) + player.getName();
                    String displayColor = itemDisplayColors.get(trackedItemName);
                    if (displayColor != null) {
                        String msg = CHAT_PREFIX + "&eAlert: " + coloredName + " &7is holding&r " + displayColor + "&r&7";
                        if (showDistance.isToggled()) {
                            msg += " &7(&d" + (int) mc.thePlayer.getDistanceToEntity(player) + "m&7)";
                        }
                        String alertmsg = color(coloredName + " &7has " + displayColor + "&7.");
                        if (hudAlerts.isToggled()) {
                            addAlert(color("&lItem Alerts"), alertmsg, (int) (hudAlertsDuration.getInput() * 1000));
                        }
                        if (chatAlerts.isToggled()) {
                            Utils.sendMessage(msg);
                        }
                        existingData.put(trackedItemName, now + delayInterval);
                    }
                }
            }

            if (hasEnchantments && (slot == 2 || (slot == 0 && isWeapon))) {
                String upgradeKey = (slot == 2 ? "protection" : "sharpness") + teamColor;
                boolean enabled = slot == 2 ? showProtection.isToggled() : showSharpness.isToggled();
                if (enabled && !teamUpgrades.contains(upgradeKey)) {
                    teamUpgrades.add(upgradeKey);
                    String upgradeName = slot == 2 ? "Reinforced Armor" : "Sharpened Swords";
                    String msg = CHAT_PREFIX + "&eAlert: " + team + " &7purchased &b" + upgradeName;
                    String alertmsg = color(team + " &7has &b" + upgradeName + "&7.");
                    if (hudAlerts.isToggled()) {
                        addAlert(color("&lItem Alerts"), alertmsg, (int) (hudAlertsDuration.getInput() * 1000));
                    }
                    if (chatAlerts.isToggled()) {
                        Utils.sendMessage(msg);
                    }
                }
            }

            if (slot == 2 && armorPieceNames.contains(itemName)) {
                String existingArmor = String.valueOf(existingData.get("armorpiece"));
                if (!existingArmor.equals(itemName)) {
                    String coloredName = color("&" + teamColor) + player.getName();
                    String armorDisplayColor = itemDisplayColors.get(itemName);
                    if (armorDisplayColor != null) {
                        String msg = CHAT_PREFIX + "&eAlert: " + coloredName + " &7purchased&r " + armorDisplayColor + "&r&7";
                        if (showDistance.isToggled()) {
                            msg += " &7(&d" + (int) mc.thePlayer.getDistanceToEntity(player) + "m&7)";
                        }
                        String alertmsg = color(coloredName + " &7has " + armorDisplayColor + "&7.");
                        if (hudAlerts.isToggled()) {
                            addAlert(color("&lItem Alerts"), alertmsg, (int) (hudAlertsDuration.getInput() * 1000));
                        }
                        if (chatAlerts.isToggled()) {
                            Utils.sendMessage(msg);
                        }
                    }
                }
                existingData.put("armorpiece", itemName);
            }
        }

        if (slot == 0) {
            existingData.put("lastitem", itemName);
        }
    }

    private void addAlert(String title, String message, int duration) {
        alertList.add(new HudAlert(title, message, System.currentTimeMillis() + duration));
    }

    private void setupItems() {
        heldItemNames.clear();
        heldItemDisplayNames.clear();
        armorPieceNames.clear();

        if (getItemButton("Iron Sword").isToggled()) heldItemNames.add("iron_sword");
        if (getItemButton("Diamond Sword").isToggled()) heldItemNames.add("diamond_sword");
        if (getItemButton("Diamond Pickaxe").isToggled()) heldItemNames.add("diamond_pickaxe");
        if (getItemButton("Ender Pearl").isToggled()) heldItemNames.add("ender_pearl");
        if (getItemButton("Obsidian").isToggled()) heldItemNames.add("obsidian");
        if (getItemButton("Bridge Egg").isToggled()) heldItemNames.add("egg");
        if (getItemButton("Fireball").isToggled()) heldItemNames.add("fire_charge");
        if (getItemButton("TNT").isToggled()) heldItemNames.add("tnt");
        if (getItemButton("Block Zapper").isToggled()) heldItemNames.add("prismarine_shard");

        if (getItemButton("Bow").isToggled()) heldItemDisplayNames.add("Bow");
        if (getItemButton("Speed Potion").isToggled()) heldItemDisplayNames.add("Speed II Potion (45 seconds)");
        if (getItemButton("Jump Potion").isToggled()) heldItemDisplayNames.add("Jump V Potion (45 seconds)");
        if (getItemButton("Invisibility Potion").isToggled()) heldItemDisplayNames.add("Invisibility Potion (30 seconds)");
        if (getItemButton("Dream Defender").isToggled()) heldItemDisplayNames.add(color("&cDream Defender"));
        if (getItemButton("Machine Gun Bow").isToggled()) heldItemDisplayNames.add("Machine Gun Bow");
        if (getItemButton("Charlie the Unicorn").isToggled()) heldItemDisplayNames.add("Charlie the Unicorn");
        if (getItemButton("Ice Bridge").isToggled()) heldItemDisplayNames.add("Ice Bridge");
        if (getItemButton("Sleeping Dust").isToggled()) heldItemDisplayNames.add("Sleeping Dust");
        if (getItemButton("Unstable Teleportation Device").isToggled()) heldItemDisplayNames.add("Unstable Teleportation Device");
        if (getItemButton("Devastator Bow").isToggled()) heldItemDisplayNames.add("Devastator Bow");
        if (getItemButton("Miracle of the Stars").isToggled()) heldItemDisplayNames.add("Miracle of the Stars");
        if (getItemButton("Mystic Mirror").isToggled()) heldItemDisplayNames.add("Mystic Mirror");

        if (getItemButton("Chainmail Leggings").isToggled()) armorPieceNames.add("chainmail_leggings");
        if (getItemButton("Iron Leggings").isToggled()) armorPieceNames.add("iron_leggings");
        if (getItemButton("Diamond Leggings").isToggled()) armorPieceNames.add("diamond_leggings");
    }

    private ButtonSetting getItemButton(String key) {
        return itemButtons.get(key);
    }

    private String getColoredTeam(String colorCode) {
        switch (colorCode) {
            case "c": return color("&cRed Team&r");
            case "9": return color("&9Blue Team&r");
            case "a": return color("&aGreen Team&r");
            case "e": return color("&eYellow Team&r");
            case "b": return color("&bAqua Team&r");
            case "f": return color("&fWhite Team&r");
            case "d": return color("&dPink Team&r");
            case "8": return color("&8Gray Team&r");
            default: return null;
        }
    }

    private int getBedwarsStatus() {
        List<String> sidebar = Utils.getScoreBoardOld();
        if (sidebar == null) {
            return mc.theWorld.provider.getDimensionId() == 1 ? 0 : -1;
        }
        if (sidebar.size() < 7) {
            return -1;
        }
        if (!StringUtils.stripControlCodes(sidebar.get(0)).startsWith("BED WARS")) {
            return -1;
        }
        if (StringUtils.stripControlCodes(sidebar.get(5)).startsWith("R Red:")
                && StringUtils.stripControlCodes(sidebar.get(6)).startsWith("B Blue:")) {
            return 3;
        }
        String six = StringUtils.stripControlCodes(sidebar.get(6));
        if (six.equals("Waiting...") || six.startsWith("Starting in")) {
            return 2;
        }
        return -1;
    }

    private void refreshTeams() {
        if (status != 3 || mc.thePlayer.capabilities.allowFlying) {
            return;
        }
        for (String key : COLOR_KEYS) {
            if (mc.thePlayer.getDisplayName().getFormattedText().startsWith(color(key))) {
                myTeam = key;
                return;
            }
        }
    }

    private static long parseLong(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String color(String s) {
        return s.replace('&', '\u00a7');
    }

    private static class HudAlert {
        final String title;
        final String message;
        final long expiry;

        HudAlert(String title, String message, long expiry) {
            this.title = title;
            this.message = message;
            this.expiry = expiry;
        }
    }
}