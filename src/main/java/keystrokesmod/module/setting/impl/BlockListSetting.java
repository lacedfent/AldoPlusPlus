package keystrokesmod.module.setting.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import keystrokesmod.module.setting.Setting;

import java.util.ArrayList;
import java.util.List;

public class BlockListSetting extends Setting {
    private final List<String> blocks = new ArrayList<>();

    public BlockListSetting(String name) {
        super(name);
    }

    public void addBlock(String registryName) {
        if (!blocks.contains(registryName)) {
            blocks.add(registryName);
        }
    }

    public void removeBlock(String registryName) {
        blocks.remove(registryName);
    }

    public List<String> getBlocks() {
        return blocks;
    }

    public boolean contains(String storageId) {
        if (blocks.contains(storageId)) return true;
        String registryId = extractRegistryId(storageId);
        return registryId != null && blocks.contains(registryId + ":*");
    }

    private static String extractRegistryId(String storageId) {
        if (storageId == null || storageId.isEmpty()) return null;
        if (storageId.endsWith(":*")) return storageId.substring(0, storageId.length() - 2);
        String[] p = storageId.split(":");
        if (p.length >= 3) return p[0] + ":" + p[1];
        if (p.length == 2) return storageId;
        return null;
    }

    @Override
    public void loadProfile(JsonObject data) {
        if (!data.has(getProfileKey())) return;
        blocks.clear();
        JsonElement el = data.get(getProfileKey());
        if (el.isJsonArray()) {
            for (JsonElement entry : el.getAsJsonArray()) {
                blocks.add(entry.getAsString());
            }
        }
    }

    public JsonArray toJsonArray() {
        JsonArray arr = new JsonArray();
        for (String block : blocks) {
            arr.add(new JsonPrimitive(block));
        }
        return arr;
    }
}
