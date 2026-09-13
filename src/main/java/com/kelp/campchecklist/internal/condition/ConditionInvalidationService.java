package com.kelp.campchecklist.internal.condition;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

/**
 * Server-thread-only dependency index and end-of-tick invalidation queue for native goals.
 * Evaluation is deliberately outside this class: callers drain a stable batch and invoke
 * the single native evaluation seam. Reentrant invalidations therefore remain for the next batch.
 */
public final class ConditionInvalidationService {
    private final Thread owner = Thread.currentThread();
    private final Map<ResourceLocation, Set<ConditionDependencyKey>> goalToDependencies = new HashMap<>();
    private final Map<ConditionDependencyKey, Set<ResourceLocation>> dependencyToGoals = new HashMap<>();
    private final Set<ResourceLocation> nativeGoals = new HashSet<>();
    private final Set<ResourceLocation> dirtyGoals = new HashSet<>();

    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Condition invalidation must run on the server thread");
    }

    public void rebuild(Map<ResourceLocation, ? extends Collection<ConditionDependencyKey>> definitions) {
        checkThread();
        goalToDependencies.clear();
        dependencyToGoals.clear();
        nativeGoals.clear();
        dirtyGoals.clear();
        definitions.forEach((goal, dependencies) -> replaceGoalDependenciesInternal(goal, dependencies));
    }

    public void replaceGoalDependencies(ResourceLocation goal, Collection<ConditionDependencyKey> dependencies) {
        checkThread();
        Objects.requireNonNull(goal, "goal");
        replaceGoalDependenciesInternal(goal, dependencies);
    }

    private void replaceGoalDependenciesInternal(ResourceLocation goal, Collection<ConditionDependencyKey> dependencies) {
        Set<ConditionDependencyKey> old = goalToDependencies.remove(goal);
        if (old != null) for (ConditionDependencyKey key : old) {
            Set<ResourceLocation> goals = dependencyToGoals.get(key);
            if (goals != null) { goals.remove(goal); if (goals.isEmpty()) dependencyToGoals.remove(key); }
        }
        TreeSet<ConditionDependencyKey> copy = new TreeSet<>();
        if (dependencies != null) for (ConditionDependencyKey key : dependencies) if (key != null) copy.add(key);
        goalToDependencies.put(goal, Collections.unmodifiableSet(copy));
        nativeGoals.add(goal);
        for (ConditionDependencyKey key : copy) dependencyToGoals.computeIfAbsent(key, ignored -> new HashSet<>()).add(goal);
        dirtyGoals.remove(goal);
    }

    public void removeGoal(ResourceLocation goal) {
        checkThread();
        Set<ConditionDependencyKey> old = goalToDependencies.remove(goal);
        if (old != null) for (ConditionDependencyKey key : old) {
            Set<ResourceLocation> goals = dependencyToGoals.get(key);
            if (goals != null) { goals.remove(goal); if (goals.isEmpty()) dependencyToGoals.remove(key); }
        }
        nativeGoals.remove(goal);
        dirtyGoals.remove(goal);
    }

    public void invalidateGoal(ResourceLocation goal) {
        checkThread();
        if (goal != null && nativeGoals.contains(goal)) dirtyGoals.add(goal);
    }

    public void invalidateDependency(ConditionDependencyKey dependency) {
        checkThread();
        if (dependency == null) return;
        Set<ResourceLocation> goals = dependencyToGoals.get(dependency);
        if (goals != null) dirtyGoals.addAll(goals);
    }

    public void invalidateAllNative(InvalidationReason reason) {
        checkThread();
        Objects.requireNonNull(reason, "reason");
        dirtyGoals.addAll(nativeGoals);
    }

    /** Drains only the current batch; additions during evaluation remain queued for the next call. */
    public List<ResourceLocation> flush() {
        checkThread();
        List<ResourceLocation> batch = new ArrayList<>(dirtyGoals);
        dirtyGoals.clear();
        batch.sort(Comparator.comparing(ResourceLocation::toString));
        return List.copyOf(batch);
    }

    public int dirtyCount() { checkThread(); return dirtyGoals.size(); }
    public int indexedGoalCount() { checkThread(); return nativeGoals.size(); }
    public Set<ConditionDependencyKey> dependencies(ResourceLocation goal) {
        checkThread();
        return goalToDependencies.getOrDefault(goal, Set.of());
    }
}
