package com.kelp.campchecklist;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Optional Create integration for per-player train distance tracking.
 *
 * The Create 6.0.10 target intentionally uses public classes from Create's
 * {@code content.*} packages because no stable train-distance API is exposed.
 * Keep this class isolated so the dependency remains optional and versioned.
 */
public final class CreateIntegration {
    private static final ResourceLocation CHECKER_ID = ResourceLocation.fromNamespaceAndPath(
            CampChecklist.ID, "create_train_distance");
    private static final int STATE_VERSION = 1;
    private static final Set<MinecraftServer> INITIALIZED_SERVERS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private CreateIntegration() {}

    public static void register() {
        CheckerRegistry.register(CHECKER_ID, new TrainDistanceChecker());
        CampChecklist.LOGGER.info("Registered Create checklist integration");
    }

    /** Called once per server tick after Create has updated its trains for the tick. */
    public static void tick(MinecraftServer server) {
        ChecklistRuntime runtime = CampChecklist.runtime(server);
        if (INITIALIZED_SERVERS.add(server)) {
            runtime.mutateCheckerStates(CHECKER_ID.toString(), STATE_VERSION,
                    (goal, counter) -> resetActiveStates(counter.state));
        }
        runtime.mutateCheckerStates(CHECKER_ID.toString(), STATE_VERSION,
                (goal, counter) -> updateDistanceState(server, counter.state));
    }

    private static void resetActiveStates(CompoundTag state) {
        if (!state.contains("players", Tag.TAG_COMPOUND)) return;
        CompoundTag players = state.getCompound("players");
        for (String key : players.getAllKeys()) {
            players.getCompound(key).putBoolean("active", false);
        }
    }

    private static void updateDistanceState(MinecraftServer server, CompoundTag state) {
        CompoundTag players = state.contains("players", Tag.TAG_COMPOUND)
                ? state.getCompound("players") : new CompoundTag();
        Set<String> onlinePlayers = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onlinePlayers.add(player.getUUID().toString());
        }
        for (String key : players.getAllKeys()) {
            if (!onlinePlayers.contains(key)) {
                players.getCompound(key).putBoolean("active", false);
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CompoundTag playerState = players.contains(player.getUUID().toString(), Tag.TAG_COMPOUND)
                    ? players.getCompound(player.getUUID().toString()) : new CompoundTag();
            CarriageContraptionEntity vehicle = findTrainVehicle(player);
            if (vehicle == null || !vehicle.isAlive()) {
                playerState.putBoolean("active", false);
                players.put(player.getUUID().toString(), playerState);
                continue;
            }

            Train train = Create.RAILWAYS.sided(player.level()).trains.get(vehicle.trainId);
            if (train == null || train.invalid || train.derailed || train.graph == null) {
                playerState.putBoolean("active", false);
                players.put(player.getUUID().toString(), playerState);
                continue;
            }

            String trainId = train.id.toString();
            String vehicleId = vehicle.getUUID().toString();
            String dimension = player.level().dimension().location().toString();
            boolean continuous = playerState.getBoolean("initialized")
                    && playerState.getBoolean("active")
                    && trainId.equals(playerState.getString("last_train_id"))
                    && vehicleId.equals(playerState.getString("last_vehicle_id"))
                    && dimension.equals(playerState.getString("last_dimension"));

            playerState.putBoolean("initialized", true);
            playerState.putBoolean("active", true);
            playerState.putString("last_train_id", trainId);
            playerState.putString("last_vehicle_id", vehicleId);
            playerState.putString("last_dimension", dimension);

            if (continuous && train.carriageWaitingForChunks == -1 && Double.isFinite(train.speed)) {
                double distance = Math.abs(train.speed);
                double current = playerState.getDouble("distance");
                if (!Double.isFinite(current) || current < 0) current = 0;
                playerState.putDouble("distance", current + distance);
            }
            players.put(player.getUUID().toString(), playerState);
        }
        state.put("players", players);
    }

    private static CarriageContraptionEntity findTrainVehicle(ServerPlayer player) {
        Entity current = player.getVehicle();
        Set<UUID> seen = new HashSet<>();
        for (int depth = 0; current != null && depth < 8 && seen.add(current.getUUID()); depth++) {
            if (current instanceof CarriageContraptionEntity carriage) return carriage;
            current = current.getVehicle();
        }
        return null;
    }

    private static final class TrainDistanceChecker implements CheckerRegistry.Checker {
        @Override
        public int intervalTicks() {
            return 20;
        }

        @Override
        public String unavailable(Definitions.Goal goal, MinecraftServer server) {
            return "";
        }

        @Override
        public CheckerRegistry.Result evaluate(Definitions.Goal goal, MinecraftServer server,
                                               CompoundTag state, int version) {
            double current = maxPlayerDistance(state);
            return new CheckerRegistry.Result(current, current >= goal.target(), state, version);
        }
    }

    static double maxPlayerDistance(CompoundTag state) {
        if (state == null || !state.contains("players", Tag.TAG_COMPOUND)) return 0;
        CompoundTag players = state.getCompound("players");
        double max = 0;
        for (String key : players.getAllKeys()) {
            CompoundTag player = players.getCompound(key);
            double distance = player.getDouble("distance");
            if (Double.isFinite(distance) && distance >= 0) max = Math.max(max, distance);
        }
        return max;
    }
}
