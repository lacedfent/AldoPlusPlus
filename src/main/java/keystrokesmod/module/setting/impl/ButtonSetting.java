package keystrokesmod.module.setting.impl;

import com.google.gson.JsonObject;
import keystrokesmod.module.setting.Setting;

public class ButtonSetting extends Setting {
    private String name;
    private boolean isEnabled;
    public boolean isMethodButton;
    private Runnable method;
    public GroupSetting group;

    public ButtonSetting(String name, boolean isEnabled) {
        super(name);
        this.name = name;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
    }

    public ButtonSetting(GroupSetting group, String name, boolean isEnabled) {
        super(name);
        this.group = group;
        this.name = name;
        this.isEnabled = isEnabled;
        this.isMethodButton = false;
    }

    public ButtonSetting(String name, Runnable method) {
        super(name);
        this.name = name;
        this.isEnabled = false;
        this.isMethodButton = true;
        this.method = method;
    }

    public void runMethod() {
        if (method != null) {
            method.run();
        }
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String getProfileKey() {
        return group == null ? getName() : group.getName() + "." + getName();
    }

    public boolean isToggled() {
        return this.isEnabled;
    }

    public void toggle() {
        this.isEnabled = !this.isEnabled;
    }

    public void enable() {
        this.isEnabled = true;
    }

    public void disable() {
        this.isEnabled = false;
    }

    public void setEnabled(boolean b) {
        this.isEnabled = b;
    }

    @Override
    public void loadProfile(JsonObject data) {
        String profileKey = getProfileKey();
        String legacyKey = getName();
        String key = data.has(profileKey) ? profileKey : legacyKey;
        if (data.has(key) && data.get(key).isJsonPrimitive() && !this.isMethodButton) {
            boolean booleanValue = isEnabled;
            try {
                booleanValue = data.getAsJsonPrimitive(key).getAsBoolean();
            }
            catch (Exception e) {}
            setEnabled(booleanValue);
        }
    }
}
