package com.kelp.campchecklist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Generic event-driven checkers that are independent of optional content mods. */
public final class CoreCheckers {
    public static final ResourceLocation ACQUIRE_ITEM_SET = ResourceLocation.fromNamespaceAndPath(CampChecklist.ID, "acquire_item_set");
    public static final ResourceLocation ITEM_STOCKPILE_SET = ResourceLocation.fromNamespaceAndPath(CampChecklist.ID, "item_stockpile_set");
    private static final String ACQUIRED_ITEMS = "acquired_items";
    private static final String STOCKPILE_PEAKS = "stockpile_peaks";
    private static final int ACQUIRE_STATE_VERSION = 1;
    private static final int STOCKPILE_STATE_VERSION = 1;

    private CoreCheckers() {}

    public static void register() {
        CheckerRegistry.register(ACQUIRE_ITEM_SET, new CheckerRegistry.Checker() {
            @Override
            public String unavailable(Definitions.Goal goal, MinecraftServer server) {
                List<ResourceLocation> items = items(goal);
                if (items.isEmpty()) return "parameters.items must contain at least one item id";
                for (ResourceLocation id : items) {
                    if (!BuiltInRegistries.ITEM.containsKey(id)) return "Unknown item in parameters.items: " + id;
                }
                if (items.size() != new HashSet<>(items).size()) return "parameters.items must not contain duplicates";
                if (goal.target() != items.size()) return "target must equal parameters.items size";
                return "";
            }

            @Override
            public void onAcquire(Definitions.Goal goal, MinecraftServer server, ServerPlayer player, ItemStack stack,
                                  CompoundTag state, int version) {
                if (version != ACQUIRE_STATE_VERSION || !state.contains(ACQUIRED_ITEMS, Tag.TAG_LIST)) {
                    state.remove(ACQUIRED_ITEMS);
                }
                ListTag acquired = state.getList(ACQUIRED_ITEMS, Tag.TAG_STRING);
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (items(goal).stream().anyMatch(id -> id.toString().equals(itemId)) && !acquired.contains(net.minecraft.nbt.StringTag.valueOf(itemId))) {
                    acquired.add(net.minecraft.nbt.StringTag.valueOf(itemId));
                    state.put(ACQUIRED_ITEMS, acquired);
                }
            }

            @Override
            public CheckerRegistry.Result evaluate(Definitions.Goal goal, MinecraftServer server, CompoundTag state, int version) {
                List<ResourceLocation> required = items(goal);
                ListTag acquired = state.getList(ACQUIRED_ITEMS, Tag.TAG_STRING);
                int current = 0;
                for (ResourceLocation id : required) {
                    if (acquired.contains(net.minecraft.nbt.StringTag.valueOf(id.toString()))) current++;
                }
                return new CheckerRegistry.Result(current, current >= required.size(), state, ACQUIRE_STATE_VERSION);
            }
        });
        CheckerRegistry.register(ITEM_STOCKPILE_SET, new CheckerRegistry.Checker() {
            @Override
            public int intervalTicks() { return 20; }

            @Override
            public String unavailable(Definitions.Goal goal, MinecraftServer server) {
                List<StockpileRequirement> required = stockpileRequirements(goal);
                if (required.isEmpty()) return "parameters.requirements must contain at least one item target";
                HashSet<ResourceLocation> ids = new HashSet<>();
                long target = 0;
                for (StockpileRequirement requirement : required) {
                    if (!BuiltInRegistries.ITEM.containsKey(requirement.item())) {
                        return "Unknown item in parameters.requirements: " + requirement.item();
                    }
                    if (!ids.add(requirement.item())) return "parameters.requirements must not contain duplicates";
                    target += requirement.target();
                }
                if (goal.target() != target) return "target must equal parameters.requirements target sum";
                return "";
            }

            @Override
            public CheckerRegistry.Result evaluate(Definitions.Goal goal, MinecraftServer server,
                                                   CompoundTag state, int version) {
                List<StockpileRequirement> required = stockpileRequirements(goal);
                if (version != STOCKPILE_STATE_VERSION || !state.contains(STOCKPILE_PEAKS, Tag.TAG_COMPOUND)) {
                    state = new CompoundTag();
                }
                CompoundTag peaks = state.getCompound(STOCKPILE_PEAKS);
                java.util.Map<ResourceLocation,Integer> observed = new java.util.HashMap<>();
                if (server != null) {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        for (ItemStack stack : inventoryStacks(player)) {
                            if (stack.isEmpty()) continue;
                            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                            observed.merge(id, stack.getCount(), Integer::sum);
                        }
                    }
                }

                double current = 0;
                boolean completed = true;
                for (StockpileRequirement requirement : required) {
                    String key = requirement.item().toString();
                    int peak = Math.max(0, peaks.getInt(key));
                    peak = Math.max(peak, observed.getOrDefault(requirement.item(), 0));
                    peaks.putInt(key, peak);
                    int capped = Math.min(peak, requirement.target());
                    current += capped;
                    completed &= peak >= requirement.target();
                }
                state.put(STOCKPILE_PEAKS, peaks);
                return new CheckerRegistry.Result(current, completed, state, STOCKPILE_STATE_VERSION);
            }

            @Override
            public List<ViewModel.Detail> details(Definitions.Goal goal, CompoundTag state) {
                CompoundTag peaks = state != null && state.contains(STOCKPILE_PEAKS, Tag.TAG_COMPOUND)
                        ? state.getCompound(STOCKPILE_PEAKS) : new CompoundTag();
                List<ViewModel.Detail> details = new ArrayList<>();
                for (StockpileRequirement requirement : stockpileRequirements(goal)) {
                    int peak = Math.max(0, peaks.getInt(requirement.item().toString()));
                    ItemStack display = new ItemStack(BuiltInRegistries.ITEM.get(requirement.item()));
                    details.add(new ViewModel.Detail(requirement.item().toString(),
                            display.getItem().getDescriptionId(),
                            Math.min(peak, requirement.target()), requirement.target(), "count", "count"));
                }
                return List.copyOf(details);
            }
        });
    }

    private record StockpileRequirement(ResourceLocation item, int target) {}

    private static List<StockpileRequirement> stockpileRequirements(Definitions.Goal goal) {
        JsonObject parameters = goal.parameters();
        if (!parameters.has("requirements") || !parameters.get("requirements").isJsonArray()) return List.of();
        List<StockpileRequirement> result = new ArrayList<>();
        for (JsonElement element : parameters.getAsJsonArray("requirements")) {
            if (!element.isJsonObject()) return List.of();
            JsonObject object = element.getAsJsonObject();
            try {
                if (!object.has("item") || !object.has("target")) return List.of();
                ResourceLocation item = ResourceLocation.parse(object.get("item").getAsString());
                double number = object.get("target").getAsDouble();
                if (!Double.isFinite(number) || number < 1 || number != Math.rint(number) || number > Integer.MAX_VALUE) return List.of();
                result.add(new StockpileRequirement(item, (int) number));
            } catch (Exception ignored) { return List.of(); }
        }
        return List.copyOf(result);
    }

    private static List<ItemStack> inventoryStacks(ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>(player.getInventory().items);
        result.addAll(player.getInventory().offhand);
        return result;
    }

    private static List<ResourceLocation> items(Definitions.Goal goal) {
        JsonObject parameters = goal.parameters();
        if (!parameters.has("items") || !parameters.get("items").isJsonArray()) return List.of();
        JsonArray array = parameters.getAsJsonArray("items");
        List<ResourceLocation> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return List.of();
            try { result.add(ResourceLocation.parse(element.getAsString())); }
            catch (Exception ignored) { return List.of(); }
        }
        return List.copyOf(result);
    }
}
