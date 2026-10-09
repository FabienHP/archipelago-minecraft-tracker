package aptracker.core.logic;

/** The apworld's {@code combat_difficulty} option, in the order of its numeric values. */
public enum CombatDifficulty {
    EASY,
    NORMAL,
    HARD;

    public static CombatDifficulty fromValue(int value) {
        CombatDifficulty[] values = values();
        if (value < 0 || value >= values.length) {
            throw new IllegalArgumentException("Unknown combat_difficulty value " + value);
        }
        return values[value];
    }
}
