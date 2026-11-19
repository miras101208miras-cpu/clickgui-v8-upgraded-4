package com.example.clickgui;

import java.util.*;

public class Module {
    public final String name;
    public final Category category;
    public final List<Setting> settings = new ArrayList<>();
    public boolean enabled, expanded;
    public int key = -1;
    public float anim;

    public Module(String name, Category category, Setting... s) {
        this.name = name;
        this.category = category;
        settings.addAll(List.of(s));
    }

    public void toggle() {
        enabled = !enabled;
        HudCombat.markArrayListDirty();
        Notifications.onModuleToggle(this);
        ConfigManager.markDirty();
    }

    public Setting find(String n) {
        for (Setting s : settings) if (s.name.equals(n)) return s;
        return null;
    }

    public double num(String n) { return find(n) instanceof Setting.Slider s ? s.value : 0; }
    public boolean bool(String n) { return find(n) instanceof Setting.Bool b && b.value; }
    public boolean has(String n, String opt) { return find(n) instanceof Setting.Multi m && m.selected.contains(opt); }
}
