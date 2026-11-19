package com.example.clickgui;

import java.util.*;

public abstract class Setting {
    public final String name;
    protected Setting(String name) { this.name = name; }

    public static class Bool extends Setting {
        public boolean value; public float anim;
        public Bool(String n, boolean v) { super(n); value = v; anim = v ? 1 : 0; }
    }

    public static class Slider extends Setting {
        public double value; public final double min, max, step;
        public Slider(String n, double v, double min, double max, double step) {
            super(n); value = v; this.min = min; this.max = max; this.step = step;
        }
        public void setFromPercent(double t) {
            t = Math.max(0, Math.min(1, t));
            double raw = Math.round((min + t * (max - min)) / step) * step;
            value = Math.max(min, Math.min(max, raw));
        }
        public double percent() { return (value - min) / (max - min); }
    }

    public static class Multi extends Setting {
        public final List<String> options; public final Set<String> selected = new LinkedHashSet<>();
        public Multi(String n, List<String> options, String... on) {
            super(n); this.options = options; selected.addAll(List.of(on));
        }
        public void toggle(String o) { if (!selected.remove(o)) selected.add(o); }
    }

    /** Выбор одного варианта. */
    public static class Mode extends Setting {
        public final List<String> options; public int index;
        public Mode(String n, List<String> options, int index) { super(n); this.options = options; this.index = index; }
        public String get() { return options.get(index); }
    }
}
