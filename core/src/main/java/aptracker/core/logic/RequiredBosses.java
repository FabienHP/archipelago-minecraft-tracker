package aptracker.core.logic;

import java.util.Locale;

/** The apworld's {@code required_bosses} option, in the order of its numeric values. */
public enum RequiredBosses {
    NONE(false, false),
    ENDER_DRAGON(true, false),
    WITHER(false, true),
    BOTH(true, true);

    private final boolean dragon;
    private final boolean wither;

    RequiredBosses(boolean dragon, boolean wither) {
        this.dragon = dragon;
        this.wither = wither;
    }

    public boolean dragon() {
        return dragon;
    }

    public boolean wither() {
        return wither;
    }

    public static RequiredBosses fromValue(int value) {
        RequiredBosses[] values = values();
        if (value < 0 || value >= values.length) {
            throw new IllegalArgumentException("Unknown required_bosses value " + value);
        }
        return values[value];
    }

    /** Parses the option key written in the slot data, such as "ender_dragon". */
    public static RequiredBosses fromKey(String key) {
        return valueOf(key.toUpperCase(Locale.ROOT));
    }
}
