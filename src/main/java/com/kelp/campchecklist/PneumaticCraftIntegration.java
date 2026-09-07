package com.kelp.campchecklist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.desht.pneumaticcraft.api.PneumaticRegistry;
import me.desht.pneumaticcraft.api.pneumatic_armor.ICommonArmorHandler;
import me.desht.pneumaticcraft.api.upgrade.PNCUpgrade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Optional PneumaticCraft integration. This class is only loaded when PneumaticCraft is installed. */
public final class PneumaticCraftIntegration {
    private static final ResourceLocation CHECKER_ID = ResourceLocation.fromNamespaceAndPath(
            CampChecklist.ID, "pneumaticcraft_armor");
    private static final Map<String, EquipmentSlot> SLOTS = Map.of(
            "head", EquipmentSlot.HEAD,
            "chest", EquipmentSlot.CHEST,
            "legs", EquipmentSlot.LEGS,
            "feet", EquipmentSlot.FEET
    );

    private PneumaticCraftIntegration() {}

    public static void register() {
        CheckerRegistry.register(CHECKER_ID, new ArmorChecker());
        CampChecklist.LOGGER.info("Registered PneumaticCraft checklist integration");
    }

    private record ArmorRequirement(ResourceLocation item, EquipmentSlot slot) {}
    private record UpgradeRequirement(ResourceLocation id, EquipmentSlot slot, int minTier, PNCUpgrade upgrade) {}
    private record Requirements(List<ArmorRequirement> armor, List<UpgradeRequirement> upgrades) {}

    private static final class ArmorChecker implements CheckerRegistry.Checker {
        @Override
        public int intervalTicks() {
            return 20;
        }

        @Override
        public String unavailable(Definitions.Goal goal, MinecraftServer server) {
            try {
                parseRequirements(goal.parameters());
                return "";
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
        }

        @Override
        public CheckerRegistry.Result evaluate(Definitions.Goal goal, MinecraftServer server,
                                               net.minecraft.nbt.CompoundTag state, int version) {
            Requirements requirements = parseRequirements(goal.parameters());
            List<Integer> playerScores = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ICommonArmorHandler armorHandler = PneumaticRegistry.getInstance()
                        .getCommonArmorRegistry().getCommonArmorHandler(player);
                if (armorHandler == null || !armorHandler.isValid()) continue;

                List<Boolean> armorSatisfied = requirements.armor().stream()
                        .map(requirement -> matchesEquippedItem(player, requirement)).toList();
                List<Integer> upgradeCounts = new ArrayList<>();
                List<Integer> minimumTiers = new ArrayList<>();
                for (UpgradeRequirement requirement : requirements.upgrades()) {
                    boolean armorMatches = matchesEquippedItem(player, new ArmorRequirement(
                            itemForSlot(requirements.armor(), requirement.slot()), requirement.slot()));
                    upgradeCounts.add(armorMatches
                            ? armorHandler.getUpgradeCount(requirement.slot(), requirement.upgrade()) : 0);
                    minimumTiers.add(requirement.minTier());
                }
                int score = score(armorSatisfied, upgradeCounts, minimumTiers);
                playerScores.add(score);
            }
            int best = maxSinglePlayerScore(playerScores);
            return new CheckerRegistry.Result(best, best >= goal.target(), state, version);
        }

        private static ResourceLocation itemForSlot(List<ArmorRequirement> armor, EquipmentSlot slot) {
            return armor.stream().filter(requirement -> requirement.slot() == slot)
                    .map(ArmorRequirement::item).findFirst().orElseThrow(
                            () -> new IllegalArgumentException("No required armor item for upgrade slot " + slotName(slot)));
        }
    }

    private static Requirements parseRequirements(JsonObject parameters) {
        if (parameters == null) throw new IllegalArgumentException("Missing parameters");
        JsonElement samePlayer = parameters.get("require_same_player");
        if (samePlayer == null || !samePlayer.isJsonPrimitive()
                || !samePlayer.getAsJsonPrimitive().isBoolean() || !samePlayer.getAsBoolean()) {
            throw new IllegalArgumentException("require_same_player must be true");
        }
        List<ArmorRequirement> armor = parseArmor(parameters);
        List<UpgradeRequirement> upgrades = parseUpgrades(parameters, armor);
        return new Requirements(List.copyOf(armor), List.copyOf(upgrades));
    }

    private static List<ArmorRequirement> parseArmor(JsonObject parameters) {
        JsonArray array = requiredArray(parameters, "required_armor");
        if (array.isEmpty()) throw new IllegalArgumentException("required_armor must not be empty");
        List<ArmorRequirement> result = new ArrayList<>();
        Set<EquipmentSlot> slots = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("required_armor entries must be objects");
            JsonObject object = element.getAsJsonObject();
            ResourceLocation item = resource(object, "item", "required_armor item");
            if (!BuiltInRegistries.ITEM.containsKey(item)) {
                throw new IllegalArgumentException("Unknown armor item: " + item);
            }
            EquipmentSlot slot = slot(object, "required_armor");
            if (!slots.add(slot)) throw new IllegalArgumentException("Duplicate required_armor slot: " + slotName(slot));
            result.add(new ArmorRequirement(item, slot));
        }
        return result;
    }

    private static List<UpgradeRequirement> parseUpgrades(JsonObject parameters, List<ArmorRequirement> armor) {
        JsonArray array = requiredArray(parameters, "required_upgrades");
        if (array.isEmpty()) throw new IllegalArgumentException("required_upgrades must not be empty");
        Set<EquipmentSlot> armorSlots = new HashSet<>();
        armor.forEach(requirement -> armorSlots.add(requirement.slot()));
        List<UpgradeRequirement> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("required_upgrades entries must be objects");
            JsonObject object = element.getAsJsonObject();
            ResourceLocation id = resource(object, "id", "required_upgrades id");
            EquipmentSlot slot = slot(object, "required_upgrades");
            if (!armorSlots.contains(slot)) {
                throw new IllegalArgumentException("Upgrade slot has no corresponding required armor: " + slotName(slot));
            }
            int minTier = positiveInt(object, "min_tier");
            PNCUpgrade upgrade = PneumaticRegistry.getInstance().getUpgradeRegistry().getUpgradeById(id);
            if (upgrade == null) throw new IllegalArgumentException("Unknown PneumaticCraft upgrade: " + id);
            if (minTier > upgrade.getMaxTier()) {
                throw new IllegalArgumentException("Upgrade min_tier exceeds max tier for " + id + ": " + minTier);
            }
            result.add(new UpgradeRequirement(id, slot, minTier, upgrade));
        }
        return result;
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        return value.getAsJsonArray();
    }

    private static ResourceLocation resource(JsonObject object, String key, String label) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing or invalid " + label);
        }
        try {
            return ResourceLocation.parse(value.getAsString());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value.getAsString());
        }
    }

    private static EquipmentSlot slot(JsonObject object, String label) {
        JsonElement value = object.get("slot");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing or invalid " + label + " slot");
        }
        EquipmentSlot slot = SLOTS.get(value.getAsString());
        if (slot == null) throw new IllegalArgumentException("Invalid " + label + " slot: " + value.getAsString());
        return slot;
    }

    private static int positiveInt(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing or invalid " + key);
        }
        double number;
        try {
            number = value.getAsDouble();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        if (!Double.isFinite(number) || number < 1 || number != Math.rint(number) || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid " + key + ": " + value);
        }
        return (int) number;
    }

    private static boolean matchesEquippedItem(ServerPlayer player, ArmorRequirement requirement) {
        ItemStack equipped = player.getItemBySlot(requirement.slot());
        return !equipped.isEmpty() && BuiltInRegistries.ITEM.getKey(equipped.getItem()).equals(requirement.item());
    }

    private static String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEGS -> "legs";
            case FEET -> "feet";
            default -> slot.getName();
        };
    }

    static int score(List<Boolean> armorSatisfied, List<Integer> installedUpgradeCounts,
                     List<Integer> minimumUpgradeTiers) {
        if (installedUpgradeCounts.size() != minimumUpgradeTiers.size()) {
            throw new IllegalArgumentException("Upgrade count and tier lists must have the same size");
        }
        int score = (int) armorSatisfied.stream().filter(Boolean::booleanValue).count();
        for (int i = 0; i < installedUpgradeCounts.size(); i++) {
            if (installedUpgradeCounts.get(i) >= minimumUpgradeTiers.get(i)) score++;
        }
        return score;
    }

    static int maxSinglePlayerScore(List<Integer> playerScores) {
        return playerScores.stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
