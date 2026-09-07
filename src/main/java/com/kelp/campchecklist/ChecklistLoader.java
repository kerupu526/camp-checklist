package com.kelp.campchecklist;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;

public final class ChecklistLoader extends SimplePreparableReloadListener<ChecklistLoader.Catalog> {
    public record Catalog(Map<ResourceLocation,Definitions.Tab> tabs, Map<ResourceLocation,Definitions.Goal> goals, Map<ResourceLocation,String> errors) {}
    public volatile Catalog catalog=new Catalog(Map.of(),Map.of(),Map.of());
    public volatile long revision;
    @Override protected Catalog prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation,Definitions.Tab> tabs=new HashMap<>(); Map<ResourceLocation,Definitions.Goal> goals=new HashMap<>(); Map<ResourceLocation,String> errors=new HashMap<>();
        read(manager,"checklist/tabs",(id,j) -> tabs.put(id,Definitions.tab(id,j)),errors);
        read(manager,"checklist/goals",(id,j) -> goals.put(id,Definitions.goal(id,j)),errors);
        return new Catalog(Map.copyOf(tabs),Map.copyOf(goals),Map.copyOf(errors));
    }
    private interface Parser { void parse(ResourceLocation id, JsonObject j); }
    private void read(ResourceManager manager,String folder,Parser parser,Map<ResourceLocation,String> errors) {
        manager.listResources(folder,p -> p.getPath().endsWith(".json")).forEach((path,resource) -> {
            ResourceLocation id=ResourceLocation.fromNamespaceAndPath(path.getNamespace(),path.getPath().substring(folder.length()+1,path.getPath().length()-5));
            try (Reader reader=resource.openAsReader()) { parser.parse(id,JsonParser.parseReader(reader).getAsJsonObject()); }
            catch (Exception e) { errors.put(path,e.getMessage()==null ? e.toString() : e.getMessage()); CampChecklist.LOGGER.warn("Invalid checklist resource {}: {}",path,e.toString()); }
        });
    }
    @Override protected void apply(Catalog value,ResourceManager manager,ProfilerFiller profiler) {
        catalog=value;
        revision++;
        CampChecklist.LOGGER.info("Checklist definitions loaded during resource reload: tabs={}, goals={}, errors={}, revision={}",
                value.tabs().size(), value.goals().size(), value.errors().size(), revision);
    }
}
