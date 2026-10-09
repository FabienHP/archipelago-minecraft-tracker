package aptracker.core.logic;

import aptracker.core.data.GameData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The options of one Minecraft slot that the logic depends on. The apworld writes them both in the
 * {@code .apmc} file and in the slot data sent by the Archipelago server (see {@code _get_mc_data}).
 *
 * @param structures exit name to the structure attached to it, for the five shuffled exits
 */
public record SlotOptions(
        CombatDifficulty combatDifficulty,
        boolean structureCompasses,
        boolean deathLink,
        boolean includeHardAdvancements,
        boolean includeUnreasonableAdvancements,
        boolean includePostgameAdvancements,
        RequiredBosses requiredBosses,
        int advancementGoal,
        int eggShardsRequired,
        int eggShardsAvailable,
        Map<String, String> structures) {

    public SlotOptions {
        structures = Map.copyOf(structures);
    }

    /** The apworld's default options, with structures left where vanilla has them. */
    public static Builder builder(GameData data) {
        return new Builder(data);
    }

    /**
     * Reads the options from the slot data (or from the JSON inside the {@code .apmc} file, which has
     * the same shape). A field that is absent keeps the apworld's default.
     */
    public static SlotOptions fromSlotData(JsonObject json, GameData data) {
        Builder builder = builder(data);
        if (json.has("combat_difficulty")) {
            builder.combatDifficulty(CombatDifficulty.fromValue(json.get("combat_difficulty").getAsInt()));
        }
        if (json.has("structure_compasses")) {
            builder.structureCompasses(truthy(json.get("structure_compasses")));
        }
        if (json.has("death_link")) {
            builder.deathLink(truthy(json.get("death_link")));
        }
        if (json.has("include_hard_advancements")) {
            builder.includeHardAdvancements(truthy(json.get("include_hard_advancements")));
        }
        if (json.has("include_unreasonable_advancements")) {
            builder.includeUnreasonableAdvancements(truthy(json.get("include_unreasonable_advancements")));
        }
        if (json.has("include_postgame_advancements")) {
            builder.includePostgameAdvancements(truthy(json.get("include_postgame_advancements")));
        }
        if (json.has("bosses_to_defeat")) {
            builder.requiredBosses(RequiredBosses.fromValue(json.get("bosses_to_defeat").getAsInt()));
        } else if (json.has("required_bosses")) {
            builder.requiredBosses(RequiredBosses.fromKey(json.get("required_bosses").getAsString()));
        }
        if (json.has("advancement_goal")) {
            builder.advancementGoal(json.get("advancement_goal").getAsInt());
        }
        if (json.has("egg_shards_required")) {
            builder.eggShardsRequired(json.get("egg_shards_required").getAsInt());
        }
        if (json.has("egg_shards_available")) {
            builder.eggShardsAvailable(json.get("egg_shards_available").getAsInt());
        }
        if (json.has("structures")) {
            Map<String, String> structures = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("structures").entrySet()) {
                structures.put(entry.getKey(), entry.getValue().getAsString());
            }
            builder.structures(structures);
        }
        return builder.build();
    }

    // The apworld writes toggles as 0/1 for some fields and as true/false for others.
    private static boolean truthy(JsonElement element) {
        if (element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        return element.getAsInt() != 0;
    }

    public static final class Builder {
        private CombatDifficulty combatDifficulty = CombatDifficulty.NORMAL;
        private boolean structureCompasses = true;
        private boolean deathLink = false;
        private boolean includeHardAdvancements = false;
        private boolean includeUnreasonableAdvancements = false;
        private boolean includePostgameAdvancements = false;
        private RequiredBosses requiredBosses = RequiredBosses.ENDER_DRAGON;
        private int advancementGoal = 40;
        private int eggShardsRequired = 0;
        private int eggShardsAvailable = 0;
        private Map<String, String> structures;

        private Builder(GameData data) {
            structures = data.defaultConnections();
        }

        public Builder combatDifficulty(CombatDifficulty value) {
            combatDifficulty = value;
            return this;
        }

        public Builder structureCompasses(boolean value) {
            structureCompasses = value;
            return this;
        }

        public Builder deathLink(boolean value) {
            deathLink = value;
            return this;
        }

        public Builder includeHardAdvancements(boolean value) {
            includeHardAdvancements = value;
            return this;
        }

        public Builder includeUnreasonableAdvancements(boolean value) {
            includeUnreasonableAdvancements = value;
            return this;
        }

        public Builder includePostgameAdvancements(boolean value) {
            includePostgameAdvancements = value;
            return this;
        }

        public Builder requiredBosses(RequiredBosses value) {
            requiredBosses = value;
            return this;
        }

        public Builder advancementGoal(int value) {
            advancementGoal = value;
            return this;
        }

        public Builder eggShardsRequired(int value) {
            eggShardsRequired = value;
            return this;
        }

        public Builder eggShardsAvailable(int value) {
            eggShardsAvailable = value;
            return this;
        }

        public Builder structures(Map<String, String> value) {
            structures = value;
            return this;
        }

        public SlotOptions build() {
            return new SlotOptions(combatDifficulty, structureCompasses, deathLink, includeHardAdvancements,
                    includeUnreasonableAdvancements, includePostgameAdvancements, requiredBosses, advancementGoal,
                    eggShardsRequired, eggShardsAvailable, structures);
        }
    }
}
