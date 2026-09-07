package com.kelp.campchecklist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mekanism.api.MekanismAPI;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleContainer;
import mekanism.api.gear.IModuleHelper;
import mekanism.api.gear.ModuleData;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Optional Mekanism integration. Only public mekanism.api.gear classes are referenced here. */
public final class MekanismIntegration {
    public static final ResourceLocation MODULES = ResourceLocation.fromNamespaceAndPath(CampChecklist.ID, "mekanism_modules");
    private static final int STATE_VERSION = 1;

    private MekanismIntegration() {}

    public static void register() {
        CheckerRegistry.register(MODULES, new ModuleChecker());
    }

    private static final class ModuleChecker implements CheckerRegistry.Checker {
        @Override
        public int intervalTicks() { return 20; }

        @Override
        public String unavailable(Definitions.Goal goal, MinecraftServer server) {
            List<Requirement> requirements = parse(goal);
            if (requirements.isEmpty()) return "parameters.required_modules must contain at least one requirement";
            if (!goal.parameters().has("require_same_player") || !goal.parameters().get("require_same_player").getAsBoolean()) {
                return "require_same_player must be true for the Mekanism modules checker";
            }
            if (goal.target() != requirements.size()) return "target must equal required_modules size";
            for (Requirement requirement : requirements) {
                if (!BuiltInRegistries.ITEM.containsKey(requirement.item())) return "Unknown target item: " + requirement.item();
                Holder<ModuleData<?>> holder = moduleHolder(requirement.module());
                if (holder == null) return "Unknown Mekanism module: " + requirement.module();
                if (requirement.minCount() > holder.value().getMaxStackSize()) {
                    return "min_count exceeds module max install count (" + holder.value().getMaxStackSize() + "): " + requirement.module();
                }
                if (!IModuleHelper.INSTANCE.getSupportedItems(holder).contains(BuiltInRegistries.ITEM.get(requirement.item()))) {
                    return "Module " + requirement.module() + " is not supported by " + requirement.item();
                }
                if (requirement.slot() != null && equipmentSlot(requirement.slot()) == null && !requirement.slot().equals("inventory")) {
                    return "Unknown slot: " + requirement.slot();
                }
            }
            return "";
        }

        @Override
        public CheckerRegistry.Result evaluate(Definitions.Goal goal, MinecraftServer server,
                                               net.minecraft.nbt.CompoundTag state, int version) {
            List<Requirement> requirements = parse(goal);
            int best = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                best = Math.max(best, score(player, requirements));
            }
            return new CheckerRegistry.Result(best, best >= requirements.size(), state, STATE_VERSION);
        }

        private static int score(ServerPlayer player, List<Requirement> requirements) {
            int score = 0;
            Map<TargetGroup, List<Requirement>> inventoryGroups = new HashMap<>();
            for (Requirement requirement : requirements) {
                if ("inventory".equals(requirement.slot())) {
                    inventoryGroups.computeIfAbsent(new TargetGroup(requirement.item(), requirement.slot()), ignored -> new ArrayList<>()).add(requirement);
                } else if (matches(player, requirement)) {
                    score++;
                }
            }
            for (List<Requirement> group : inventoryGroups.values()) {
                int bestTool = 0;
                for (ItemStack stack : inventoryStacks(player)) {
                    if (stack.getItem() != BuiltInRegistries.ITEM.get(group.get(0).item())) continue;
                    int toolScore = 0;
                    for (Requirement requirement : group) if (matches(stack, requirement)) toolScore++;
                    bestTool = Math.max(bestTool, toolScore);
                }
                score += bestTool;
            }
            return score;
        }

        private static boolean matches(ServerPlayer player, Requirement requirement) {
            for (ItemStack stack : candidateStacks(player, requirement)) if (matches(stack, requirement)) return true;
            return false;
        }

        private static boolean matches(ItemStack stack, Requirement requirement) {
            Holder<ModuleData<?>> module = moduleHolder(requirement.module());
            if (module == null) return false;
            if (stack.isEmpty() || stack.getItem() != BuiltInRegistries.ITEM.get(requirement.item())) return false;
            IModuleContainer container = IModuleHelper.INSTANCE.getModuleContainer(stack);
            if (container == null) return false;
            IModule<?> installed = container.get(module);
            return installed != null && installed.getInstalledCount() >= requirement.minCount()
                    && (!requirement.requireEnabled() || installed.isEnabled());
        }

        private static Iterable<ItemStack> candidateStacks(ServerPlayer player, Requirement requirement) {
            List<ItemStack> result = new ArrayList<>();
            if (requirement.slot() != null && !requirement.slot().equals("inventory")) {
                EquipmentSlot slot = equipmentSlot(requirement.slot());
                if (slot != null) result.add(player.getItemBySlot(slot));
            } else {
                result.addAll(inventoryStacks(player));
            }
            return result;
        }

        private static List<ItemStack> inventoryStacks(ServerPlayer player) {
            List<ItemStack> result = new ArrayList<>();
            result.add(player.getMainHandItem());
            result.add(player.getOffhandItem());
            result.addAll(player.getInventory().items);
            return result;
        }

        private static Holder<ModuleData<?>> moduleHolder(ResourceLocation id) {
            ResourceKey<ModuleData<?>> key = ResourceKey.create(MekanismAPI.MODULE_REGISTRY_NAME, id);
            return MekanismAPI.MODULE_REGISTRY.getHolder(key).orElse(null);
        }
    }

    private record TargetGroup(ResourceLocation item, String source) {}
    private record Requirement(ResourceLocation item, String slot, ResourceLocation module, int minCount, boolean requireEnabled) {}

    private static List<Requirement> parse(Definitions.Goal goal) {
        JsonObject parameters = goal.parameters();
        if (!parameters.has("required_modules") || !parameters.get("required_modules").isJsonArray()) return List.of();
        JsonArray array = parameters.getAsJsonArray("required_modules");
        List<Requirement> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) return List.of();
            JsonObject object = element.getAsJsonObject();
            try {
                ResourceLocation item = ResourceLocation.parse(object.get("item").getAsString());
                ResourceLocation module = ResourceLocation.parse(object.get("module").getAsString());
                int minCount = object.has("min_count") ? object.get("min_count").getAsInt() : 1;
                boolean enabled = !object.has("require_enabled") || object.get("require_enabled").getAsBoolean();
                String slot = object.has("slot") ? object.get("slot").getAsString() : null;
                if (minCount < 1) return List.of();
                result.add(new Requirement(item, slot, module, minCount, enabled));
            } catch (Exception ignored) { return List.of(); }
        }
        return List.copyOf(result);
    }

    private static EquipmentSlot equipmentSlot(String value) {
        return switch (value) {
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "body", "chest", "bodyarmor" -> EquipmentSlot.CHEST;
            case "legs", "pants" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
