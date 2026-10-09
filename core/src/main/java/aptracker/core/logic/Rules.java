package aptracker.core.logic;

import java.util.LinkedHashMap;
import java.util.Map;

import static aptracker.core.logic.Rule.all;
import static aptracker.core.logic.Rule.any;
import static aptracker.core.logic.Rule.has;
import static aptracker.core.logic.Rule.location;
import static aptracker.core.logic.Rule.region;

/**
 * A line-by-line port of the apworld's {@code Rules.py}. Helper names and the order of the entries
 * follow the Python file so that the two can be compared side by side when the apworld changes.
 */
final class Rules {

    private static final String TOOLS = "Progressive Tools";
    private static final String WEAPONS = "Progressive Weapons";
    private static final String ARMOR = "Progressive Armor";
    private static final String RESOURCE_CRAFTING = "Progressive Resource Crafting";

    private final LogicWorld world;
    private final SlotOptions options;

    private final Rule hasIronIngots;
    private final Rule hasCopperIngots;
    private final Rule hasGoldIngots;
    private final Rule hasDiamondPickaxe;
    private final Rule craftCrossbow;
    private final Rule hasBottle;
    private final Rule canAdventure;
    private final Rule basicCombat;
    private final Rule hasSpyglass;
    private final Rule canEnchant;
    private final Rule canUseAnvil;
    private final Rule fortressLoot;
    private final Rule canExcavate;
    private final Rule canBrewPotions;
    private final Rule canPiglinTrade;
    private final Rule overworldVillager;
    private final Rule enterStronghold;
    private final Rule ominousVaults;
    private final Rule completeRaid;
    private final Rule canKillWither;
    private final Rule canRespawnEnderDragon;
    private final Rule canKillEnderDragon;

    Rules(LogicWorld world) {
        this.world = world;
        this.options = world.options();

        hasIronIngots = all(has(TOOLS), has(RESOURCE_CRAFTING));
        hasCopperIngots = all(has(TOOLS), has(RESOURCE_CRAFTING));
        hasGoldIngots = all(has(RESOURCE_CRAFTING), any(has(TOOLS, 2), region("The Nether")));
        hasDiamondPickaxe = all(has(TOOLS, 3), hasIronIngots);
        craftCrossbow = all(has("Archery"), hasIronIngots);
        hasBottle = all(has("Bottles"), has(RESOURCE_CRAFTING));

        canAdventure = canAdventure();
        basicCombat = basicCombat();

        hasSpyglass = all(hasCopperIngots, has("Spyglass"), canAdventure);
        canEnchant = all(has("Enchanting"), hasDiamondPickaxe);  // mine obsidian and lapis
        canUseAnvil = all(has("Enchanting"), has(RESOURCE_CRAFTING, 2), hasIronIngots);
        fortressLoot = all(region("Nether Fortress"), basicCombat);  // blaze rods, wither skulls
        canExcavate = all(hasCopperIngots, has("Brush"), canAdventure);
        canBrewPotions = all(has(LogicWorld.EVENT_BLAZE_RODS), has("Brewing"), hasBottle);
        canPiglinTrade = all(hasGoldIngots, any(region("The Nether"), region("Bastion Remnant")));
        overworldVillager = overworldVillager();
        enterStronghold = all(has(LogicWorld.EVENT_BLAZE_RODS), has("Brewing"), has("3 Ender Pearls"));
        ominousVaults = ominousVaults();
        completeRaid = completeRaid();
        canKillWither = canKillWither();
        canRespawnEnderDragon = all(region("The Nether"), region("The End"),
                has(RESOURCE_CRAFTING));  // smelt sand into glass
        canKillEnderDragon = canKillEnderDragon();
    }

    private Rule overworldVillager() {
        String villageRegion = world.parentRegion(world.entranceOf("Village"));
        if (villageRegion.equals("The Nether")) {  // 2 options: cure zombie villager or build portal in village
            return any(location("Zombie Doctor"), all(hasDiamondPickaxe, region("Village")));
        } else if (villageRegion.equals("The End")) {
            return location("Zombie Doctor");
        }
        return any(region("Village"), location("Zombie Doctor"));
    }

    // Difficulty-dependent functions

    private Rule canAdventure() {
        Rule deathLinkCheck = options.deathLink() ? has("Bed") : Rule.TRUE;
        switch (options.combatDifficulty()) {
            case EASY:
                return all(has(WEAPONS, 2), hasIronIngots, deathLinkCheck);
            case HARD:
                return Rule.TRUE;
            default:
                return all(has(WEAPONS), deathLinkCheck, any(has(RESOURCE_CRAFTING), has("Campfire")));
        }
    }

    private Rule basicCombat() {
        switch (options.combatDifficulty()) {
            case EASY:
                return all(has(WEAPONS, 2), has(ARMOR), has("Shield"), hasIronIngots);
            case HARD:
                return Rule.TRUE;
            default:
                return all(has(WEAPONS), any(has(ARMOR), has("Shield")), hasIronIngots);
        }
    }

    private Rule ominousVaults() {
        switch (options.combatDifficulty()) {
            case EASY:
                return all(region("Pillager Outpost"), has(WEAPONS, 3), has(ARMOR, 2), has("Shield"), has(TOOLS, 2),
                        hasIronIngots);
            case HARD:
                return all(region("Pillager Outpost"), has(WEAPONS, 2), hasIronIngots, any(has(ARMOR), has("Shield")));
            default:
                return all(region("Pillager Outpost"), has(WEAPONS, 2), hasIronIngots, has(ARMOR), has("Shield"));
        }
    }

    private Rule completeRaid() {
        Rule reachRegions = all(region("Village"), region("Pillager Outpost"));
        switch (options.combatDifficulty()) {
            case EASY:
                return all(reachRegions, has(WEAPONS, 3), has(ARMOR, 2), has("Shield"), has("Archery"), has(TOOLS, 2),
                        hasIronIngots);
            case HARD:  // might be too hard?
                return all(reachRegions, has(WEAPONS, 2), hasIronIngots, any(has(ARMOR), has("Shield")));
            default:
                return all(reachRegions, has(WEAPONS, 2), hasIronIngots, has(ARMOR), has("Shield"));
        }
    }

    private Rule canKillWither() {
        Rule normalKill = all(has(WEAPONS, 3), has(ARMOR, 2), canBrewPotions, canEnchant);
        switch (options.combatDifficulty()) {
            case EASY:
                return all(fortressLoot, normalKill, has("Archery"));
            case HARD:  // cheese kill using bedrock ceilings
                return all(fortressLoot, any(normalKill, region("The Nether"), region("The End")));
            default:
                return all(fortressLoot, normalKill);
        }
    }

    private Rule canKillEnderDragon() {
        switch (options.combatDifficulty()) {
            case EASY:
                return all(has(WEAPONS, 3), has(ARMOR, 2), has("Archery"), canBrewPotions, canEnchant);
            case HARD:
                return any(
                        all(has(WEAPONS, 2), has(ARMOR)),
                        all(has(WEAPONS, 1), has("Bed")));  // who needs armor when you can respawn right outside the chamber
            default:
                return all(has(WEAPONS, 2), has(ARMOR), has("Archery"));
        }
    }

    private Rule hasStructureCompass(String entranceName) {
        if (!options.structureCompasses()) {
            return Rule.TRUE;
        }
        return has("Structure Compass (" + world.connectedRegion(entranceName) + ")");
    }

    Map<String, Rule> entrances() {
        Map<String, Rule> rules = new LinkedHashMap<>();
        rules.put("Nether Portal", all(has("Flint and Steel"), any(has("Bucket"), has(TOOLS, 3)), hasIronIngots));
        rules.put("End Portal", all(enterStronghold, has("3 Ender Pearls", 4)));
        rules.put("Overworld Structure 1", all(canAdventure, hasStructureCompass("Overworld Structure 1")));
        rules.put("Overworld Structure 2", all(canAdventure, hasStructureCompass("Overworld Structure 2")));
        rules.put("Nether Structure 1", all(canAdventure, hasStructureCompass("Nether Structure 1")));
        rules.put("Nether Structure 2", all(canAdventure, hasStructureCompass("Nether Structure 2")));
        rules.put("The End Structure", all(canAdventure, hasStructureCompass("The End Structure")));
        rules.put("Ocean", all(canAdventure, hasStructureCompass("Ocean")));
        rules.put("Dark Forest", all(canAdventure, hasStructureCompass("Dark Forest")));
        rules.put("Deep Dark", all(canAdventure, hasIronIngots, has(TOOLS, 2), hasStructureCompass("Deep Dark")));
        rules.put("Ruins", all(canAdventure, hasStructureCompass("Ruins")));
        rules.put("Underground", all(canAdventure, has(TOOLS), hasStructureCompass("Underground")));
        rules.put("Sulfur Spring", all(canAdventure, has(TOOLS), hasStructureCompass("Sulfur Spring")));
        rules.put("Biome Discovery", all(canAdventure, hasStructureCompass("Biome Discovery")));
        return rules;
    }

    Map<String, Rule> locations() {
        Map<String, Rule> rules = new LinkedHashMap<>();
        rules.put(LogicWorld.EVENT_ENDER_DRAGON, all(canRespawnEnderDragon, canKillEnderDragon));
        rules.put(LogicWorld.EVENT_WITHER, canKillWither);
        rules.put(LogicWorld.EVENT_BLAZE_RODS, fortressLoot);
        rules.put("Who is Cutting Onions?", canPiglinTrade);
        rules.put("Oh Shiny", canPiglinTrade);
        rules.put("Suit Up", all(has(ARMOR), hasIronIngots));
        rules.put("Very Very Frightening", all(has("Channeling Book"), canUseAnvil, canEnchant, overworldVillager));
        rules.put("Hot Stuff", all(has("Bucket"), hasIronIngots));
        rules.put("Free the End", all(canRespawnEnderDragon, canKillEnderDragon));
        rules.put("A Furious Cocktail", all(
                canBrewPotions,
                has("Fishing Rod"),  // Water Breathing
                region("The Nether"),  // Regeneration, Fire Resistance, gold nuggets
                region("Village"),  // Night Vision, Invisibility
                location("Bring Home the Beacon"),  // Resistance
                canAdventure,
                region("Trial Chambers")));  // Wind Charged
        rules.put("Bring Home the Beacon", all(canKillWither, hasDiamondPickaxe, has(RESOURCE_CRAFTING, 2)));
        rules.put("Not Today, Thank You", all(has("Shield"), hasIronIngots));
        rules.put("Isn't It Iron Pick", all(has(TOOLS, 2), hasIronIngots));
        rules.put("Local Brewery", canBrewPotions);
        rules.put("The Next Generation", all(canRespawnEnderDragon, canKillEnderDragon));
        rules.put("Fishy Business", has("Fishing Rod"));
        rules.put("Hot Tourist Destinations", hasStructureCompass("Biome Discovery"));
        rules.put("This Boat Has Legs", all(hasIronIngots, has("Saddle"), has("Fishing Rod")));
        rules.put("Sniper Duel", has("Archery"));
        rules.put("Great View From Up Here", basicCombat);
        rules.put("How Did We Get Here?", all(
                canBrewPotions,
                hasGoldIngots,  // Absorption
                region("End City"),  // Levitation
                region("The Nether"),  // potion ingredients
                region("Ocean Monument"),  // Heart of the Sea, Dolphin's Grace, Mining Fatigue
                region("Ancient City"),  // Darkness
                region("Trial Chambers"),  // Wind Charged
                has("Fishing Rod"),  // Pufferfish, Nautilus Shells
                has("Archery"),  // Spectral Arrows
                location("Bring Home the Beacon"),  // Haste
                location("Hero of the Village")));  // Bad Omen, Hero of the Village
        rules.put("Bullseye", all(has("Archery"), has(TOOLS, 2), hasIronIngots));
        rules.put("Spooky Scary Skeleton", basicCombat);
        rules.put("Two by Two", all(
                canExcavate,
                region("The Nether"),  // Hoglins
                region("Ocean Monument"),  // Sniffers
                has("Bucket"),  // Axolotls
                region("Village"),  // Cats
                has("Brush"),
                has("Fishing Rod")));  // Pufferfish for Nautiluses
        rules.put("Two Birds, One Arrow", all(craftCrossbow, canEnchant));
        rules.put("Who's the Pillager Now?", craftCrossbow);
        rules.put("Getting an Upgrade", has(TOOLS));
        rules.put("Tactical Fishing", all(has("Bucket"), hasIronIngots));
        rules.put("Zombie Doctor", all(canBrewPotions, hasGoldIngots));
        rules.put("Ice Bucket Challenge", hasDiamondPickaxe);
        rules.put("Into Fire", basicCombat);
        rules.put("War Pigs", basicCombat);
        rules.put("Take Aim", has("Archery"));
        rules.put("Total Beelocation", all(has("Silk Touch Book"), canUseAnvil, canEnchant));
        rules.put("Arbalistic", all(craftCrossbow, has("Piercing IV Book"), canUseAnvil, canEnchant));
        rules.put("The End... Again...", all(canRespawnEnderDragon, canKillEnderDragon));
        rules.put("Acquire Hardware", hasIronIngots);
        rules.put("Not Quite \"Nine\" Lives", all(canPiglinTrade, has(RESOURCE_CRAFTING, 2)));
        rules.put("Cover Me with Diamonds", all(has(ARMOR, 2), has(TOOLS, 2), hasIronIngots));
        rules.put("Sky's the Limit", basicCombat);
        rules.put("Hired Help", all(has(RESOURCE_CRAFTING, 2), hasIronIngots));
        rules.put("Sweet Dreams", any(has("Bed"), region("Village")));
        rules.put("You Need a Mint", all(canRespawnEnderDragon, hasBottle));
        rules.put("Monsters Hunted", all(
                canRespawnEnderDragon,  // Ghast, Hoglin, Magma Cube, Piglin
                canKillEnderDragon,  // Ender Dragon, Enderman, Endermite, Silverfish
                canKillWither,  // Blaze, Wither, Wither Skeleton, Zombified Piglin
                completeRaid,  // Ravagers; Pillager Outposts
                region("Bastion Remnant"),  // Piglin Brute
                region("End City"),  // Shulker
                region("Trial Chambers"),  // Breeze
                has("Lead"),  // Zoglins
                region("Ocean Monument"),  // Drowned
                any(
                        all(canBrewPotions, has("Fishing Rod")),  // Water Breathing Potions for Elder Guardian, Guardian
                        all(canEnchant, has("Bucket")))));  // Aqua Affinity/Respiration and Milk/Axolotls for Elder Guardian, Guardian
        rules.put("Enchanter", canEnchant);
        rules.put("Voluntary Exile", basicCombat);
        rules.put("Eye Spy", enterStronghold);
        rules.put("Serious Dedication", all(location("Hidden in the Depths"), has("8 Netherite Scrap"), hasGoldIngots));
        rules.put("Postmortal", completeRaid);
        rules.put("Adventuring Time", all(canAdventure, hasIronIngots, has(TOOLS, 2)));
        rules.put("Hero of the Village", completeRaid);
        rules.put("Hidden in the Depths", all(canBrewPotions, has("Bed"), hasDiamondPickaxe));
        rules.put("Beaconator", all(canKillWither, hasDiamondPickaxe, has(RESOURCE_CRAFTING, 2)));
        rules.put("Withering Heights", canKillWither);
        rules.put("A Balanced Diet", all(
                hasBottle,  // honey bottle
                has("Campfire"),  // honey bottle
                has("Fishing Rod"),
                location("Overpowered"),  // gapple, notch apple
                region("The End")));  // chorus fruit
        rules.put("Subspace Bubble", hasDiamondPickaxe);
        rules.put("Country Lode, Take Me Home", all(has(TOOLS, 2), hasIronIngots));
        rules.put("Bee Our Guest", all(has("Campfire"), hasBottle));
        rules.put("Uneasy Alliance", all(hasDiamondPickaxe, has("Fishing Rod")));
        rules.put("Diamonds!", all(has(TOOLS, 2), hasIronIngots));
        rules.put("A Throwaway Joke", basicCombat);
        rules.put("Sticky Situation", all(has("Campfire"), hasBottle));
        rules.put("Ol' Betsy", craftCrossbow);
        rules.put("Cover Me in Debris", all(has(ARMOR, 2), has("8 Netherite Scrap", 2), location("Hidden in the Depths")));
        rules.put("Hot Topic", has(RESOURCE_CRAFTING));
        rules.put("The Lie", all(hasIronIngots, has("Bucket")));
        rules.put("On a Rail", all(hasIronIngots, has(TOOLS, 2)));
        rules.put("When Pigs Fly", all(hasIronIngots, has("Saddle"), has("Fishing Rod"), canAdventure));
        // The Python rule also requires that the player did not exclude "Over-Overkill" in their YAML.
        // Neither the .apmc file nor the slot data carries that list, so it is assumed not excluded.
        rules.put("Overkill", any(
                all(canBrewPotions, any(has(WEAPONS), region("The Nether"))),
                all(location("Over-Overkill"), Rule.of(options.includeHardAdvancements()))));
        rules.put("Librarian", has("Enchanting"));
        rules.put("Overpowered", all(hasIronIngots, has(TOOLS, 2), basicCombat));
        rules.put("Wax On", all(has("Campfire"), hasCopperIngots));
        rules.put("Wax Off", any(all(hasCopperIngots, has("Campfire")), region("Trial Chambers")));
        rules.put("The Cutest Predator", all(canAdventure, hasIronIngots, has("Bucket")));
        rules.put("The Healing Power of Friendship", all(canAdventure, hasIronIngots, has("Bucket")));
        rules.put("Is It a Bird?", hasSpyglass);
        rules.put("Is It a Balloon?", hasSpyglass);
        rules.put("Is It a Plane?", all(hasSpyglass, canRespawnEnderDragon));
        rules.put("Surge Protector", all(has("Channeling Book"), canUseAnvil, canEnchant, overworldVillager));
        rules.put("Light as a Rabbit", all(canAdventure, hasIronIngots, has("Bucket")));
        rules.put("Glow and Behold!", canAdventure);
        rules.put("Whatever Floats Your Goat!", canAdventure);
        rules.put("Caves & Cliffs", all(hasIronIngots, has("Bucket"), has(TOOLS, 2)));
        rules.put("Feels Like Home", all(hasIronIngots, has("Bucket"), has("Fishing Rod"), has("Saddle")));
        rules.put("Sound of Music", all(has(TOOLS, 2), hasIronIngots, canAdventure,
                any(basicCombat, region("The Nether"), region("Ancient City"))));
        rules.put("Star Trader", all(
                hasIronIngots,
                has("Bucket"),
                any(
                        region("The Nether"),  // soul sand in nether
                        region("Nether Fortress"),  // soul sand in fortress if not in nether for water elevator
                        canPiglinTrade),  // piglins give soul sand
                overworldVillager));
        rules.put("Birthday Song", all(location("The Lie"), has(TOOLS, 2), hasIronIngots,
                any(region("Pillager Outpost"), all(basicCombat, region("Woodland Mansion")))));
        rules.put("Bukkit Bukkit", all(has("Bucket"), hasIronIngots, canAdventure));
        rules.put("It Spreads", all(canAdventure, hasIronIngots, has(TOOLS, 2)));
        rules.put("Sneak 100", all(canAdventure, hasIronIngots, has(TOOLS, 2)));
        rules.put("When the Squad Hops into Town", all(canAdventure, has("Lead"), has("Bucket"), hasIronIngots));
        rules.put("With Our Powers Combined!", all(canAdventure, region("The Nether"), has("Lead"), has("Bucket"),
                hasIronIngots));
        rules.put("You've Got a Friend in Me",
                any(region("Pillager Outpost"), all(basicCombat, region("Woodland Mansion"))));
        rules.put("Smells Interesting", canExcavate);
        rules.put("Little Sniffs", canExcavate);
        rules.put("Planting the Past", canExcavate);
        rules.put("Crafting a New Look", all(hasIronIngots, any(  // Maybe streamline this one
                fortressLoot,
                all(region("Pillager Outpost"), basicCombat),
                all(region("Bastion Remnant"), basicCombat),
                all(region("End City"), basicCombat),
                all(region("Ocean Monument"), basicCombat, has("Bucket"), canEnchant),
                all(region("Woodland Mansion"), basicCombat),
                region("Ancient City"),
                all(region("Trail Ruins"), has("Brush")))));
        rules.put("Smithing with Style", all(
                canExcavate,  // Wayfinder Armor Trim
                fortressLoot,  // Rib Armor Trim
                region("Bastion Remnant"),  // Snout Armor Trim
                region("End City"),  // Spire Armor Trim
                any(
                        all(has("Fishing Rod"), canBrewPotions),  // Water Breathing Potions
                        all(has("Bucket"), canEnchant)),  // Milk/Axolotls, Respiration
                region("Woodland Mansion"),  // Vex Armor Trim
                region("Ancient City"),  // Ward and Silence Armor Trims
                region("Trail Ruins"),
                region("Ocean Monument")));  // Tide Armor Trim
        rules.put("Respecting the Remnants", all(canExcavate, any(region("Ocean Monument"), region("Trail Ruins"))));
        rules.put("Careful Restoration", all(region("Trial Chambers"), basicCombat));
        rules.put("The Power of Books", has(TOOLS, 2));
        rules.put("Isn't It Scute?", all(canAdventure, hasCopperIngots, has("Brush")));
        rules.put("Shear Brilliance", all(canAdventure, hasCopperIngots, has("Brush")));
        rules.put("Good as New", all(canAdventure, hasCopperIngots, has("Brush")));
        rules.put("The Whole Pack", canAdventure);
        rules.put("Under Lock and Key", basicCombat);
        rules.put("Blowback", basicCombat);
        rules.put("Who Needs Rockets?", basicCombat);
        rules.put("Crafters Crafting Crafters", all(hasIronIngots, has(TOOLS, 2)));
        rules.put("Lighten Up", any(
                all(fortressLoot, has(TOOLS, 2), has(RESOURCE_CRAFTING, 2)),
                region("Trial Chambers")));
        rules.put("Over-Overkill", ominousVaults);
        rules.put("Revaulting", ominousVaults);
        rules.put("Stay Hydrated!", all(region("The Nether"), canPiglinTrade));
        rules.put("Heart Transplanter", all(canAdventure, any(
                all(basicCombat, has(RESOURCE_CRAFTING, 2)),
                all(has("Silk Touch Book"), canUseAnvil, canEnchant))));
        rules.put("Mob Kabob", has(RESOURCE_CRAFTING));
        rules.put("Uh Oh", all(canAdventure, basicCombat, has(TOOLS)));
        return rules;
    }
}
