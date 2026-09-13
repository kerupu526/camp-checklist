package com.kelp.campchecklist;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Typed, registry-aware codec for the internal C5 snapshot. */
final class ViewModelStreamCodec {
    private static final int MAX_TEXT=16_384, MAX_TABS=256, MAX_GOALS=2_048, MAX_DIAGNOSTICS=2_048;
    private ViewModelStreamCodec() {}
    static void write(RegistryFriendlyByteBuf b,ViewModel model) {
        writeCount(b,model.tabs().size(),MAX_TABS); for(var tab:model.tabs()) writeTab(b,tab);
        writeCount(b,model.goals().size(),MAX_GOALS); for(var goal:model.goals()) writeGoal(b,goal);
        writeCount(b,model.diagnostics().size(),MAX_DIAGNOSTICS); for(var diagnostic:model.diagnostics()) writeText(b,diagnostic);
    }
    static ViewModel read(RegistryFriendlyByteBuf b) {
        List<ViewModel.Tab> tabs=new ArrayList<>(); for(int i=readCount(b,MAX_TABS);i-->0;) tabs.add(readTab(b));
        List<ViewModel.Goal> goals=new ArrayList<>(); int[] budget={ConditionPresentation.MAX_DETAIL_NODES_PER_SNAPSHOT}; for(int i=readCount(b,MAX_GOALS);i-->0;) goals.add(readGoal(b,budget));
        List<String> diagnostics=new ArrayList<>(); for(int i=readCount(b,MAX_DIAGNOSTICS);i-->0;) diagnostics.add(readText(b));
        return new ViewModel(tabs,goals,diagnostics);
    }
    private static void writeTab(RegistryFriendlyByteBuf b,ViewModel.Tab t) { writeText(b,t.id());writeText(b,t.title());writeText(b,t.description());writeText(b,t.icon());b.writeVarInt(t.order()); }
    private static ViewModel.Tab readTab(RegistryFriendlyByteBuf b) { return new ViewModel.Tab(readText(b),readText(b),readText(b),readText(b),b.readVarInt()); }
    private static void writeGoal(RegistryFriendlyByteBuf b,ViewModel.Goal g) {
        writeText(b,g.id());writeText(b,g.tab());writeText(b,g.title());writeText(b,g.description());writeText(b,g.icon());b.writeVarInt(g.order());b.writeBoolean(g.completed());b.writeBoolean(g.manual());b.writeDouble(g.current());b.writeDouble(g.target());b.writeDouble(g.progress());writeText(b,g.unit());writeText(b,g.displayUnit());b.writeBoolean(g.unavailable());writeText(b,g.reason());
        b.writeBoolean(g.evaluation()!=null); if(g.evaluation()!=null) writeEvaluation(b,g.evaluation());
        writeCount(b,g.details().size(),ConditionPresentation.MAX_DETAIL_NODES_PER_GOAL); for(var d:g.details()) { writeText(b,d.id());writeText(b,d.label());b.writeDouble(d.current());b.writeDouble(d.target());writeText(b,d.unit());writeText(b,d.displayUnit());writeText(b,d.progressText()); }
    }
    private static ViewModel.Goal readGoal(RegistryFriendlyByteBuf b,int[] budget) {
        String id=readText(b),tab=readText(b),title=readText(b),description=readText(b),icon=readText(b); int order=b.readVarInt(); boolean completed=b.readBoolean(),manual=b.readBoolean(); double current=b.readDouble(),target=b.readDouble(),progress=b.readDouble(); String unit=readText(b),displayUnit=readText(b); boolean unavailable=b.readBoolean(); String reason=readText(b);
        ViewModel.Evaluation evaluation=b.readBoolean()?readEvaluation(b,0,budget):null;
        List<ViewModel.Detail> details=new ArrayList<>(); for(int i=readCount(b,ConditionPresentation.MAX_DETAIL_NODES_PER_GOAL);i-->0;) details.add(new ViewModel.Detail(readText(b),readText(b),b.readDouble(),b.readDouble(),readText(b),readText(b),readText(b)));
        return new ViewModel.Goal(id,tab,title,description,icon,order,completed,manual,current,target,progress,unit,displayUnit,unavailable,reason,details,evaluation);
    }
    private static void writeEvaluation(RegistryFriendlyByteBuf b,ViewModel.Evaluation e) { b.writeVarInt(e.status().ordinal());b.writeBoolean(e.numeric().isPresent());e.numeric().ifPresent(n->{b.writeDouble(n.current());b.writeDouble(n.target());});writeOptionalComponent(b,e.progressText());writeOptionalComponent(b,e.unavailableReason());b.writeBoolean(e.detailsTruncated());writeCount(b,e.details().size(),ConditionPresentation.MAX_DETAIL_NODES_PER_GOAL);for(var d:e.details())writeDetail(b,d); }
    private static ViewModel.Evaluation readEvaluation(RegistryFriendlyByteBuf b,int depth,int[] budget) {
        if(depth>ConditionPresentation.MAX_DETAIL_DEPTH) throw new IllegalArgumentException("Condition detail snapshot exceeds depth bound");
        int status=b.readVarInt(); if(status<0||status>=com.kelp.campchecklist.api.progress.ConditionStatus.values().length)throw new IllegalArgumentException("Invalid condition status");
        Optional<ViewModel.Progress> numeric=b.readBoolean()?Optional.of(new ViewModel.Progress(b.readDouble(),b.readDouble())):Optional.empty(); Optional<Component> progress=readOptionalComponent(b),reason=readOptionalComponent(b); boolean truncated=b.readBoolean()||depth>=ConditionPresentation.MAX_DETAIL_DEPTH; List<ViewModel.EvaluationDetail> details=new ArrayList<>(); int count=readCount(b,ConditionPresentation.MAX_DETAIL_NODES_PER_GOAL); for(int i=0;i<count;i++){if(budget[0]--<=0)throw new IllegalArgumentException("Condition detail snapshot exceeds budget");details.add(readDetail(b,depth+1,budget));} return new ViewModel.Evaluation(com.kelp.campchecklist.api.progress.ConditionStatus.values()[status],numeric,progress,reason,details,truncated);
    }
    private static void writeDetail(RegistryFriendlyByteBuf b,ViewModel.EvaluationDetail d) { writeText(b,d.key()); ComponentSerialization.STREAM_CODEC.encode(b,d.display().label());b.writeBoolean(d.display().icon().isPresent());d.display().icon().ifPresent(icon->ItemStack.OPTIONAL_STREAM_CODEC.encode(b,icon));writeEvaluation(b,d.evaluation()); }
    private static ViewModel.EvaluationDetail readDetail(RegistryFriendlyByteBuf b,int depth,int[] budget) { String key=readText(b);Component label=ComponentSerialization.STREAM_CODEC.decode(b);Optional<ItemStack> icon=b.readBoolean()?Optional.of(ItemStack.OPTIONAL_STREAM_CODEC.decode(b)):Optional.empty();return new ViewModel.EvaluationDetail(key,new ViewModel.Display(label,icon),readEvaluation(b,depth,budget)); }
    private static void writeOptionalComponent(RegistryFriendlyByteBuf b,Optional<Component> c){b.writeBoolean(c.isPresent());c.ifPresent(v->ComponentSerialization.STREAM_CODEC.encode(b,v));}
    private static Optional<Component> readOptionalComponent(RegistryFriendlyByteBuf b){return b.readBoolean()?Optional.of(ComponentSerialization.STREAM_CODEC.decode(b)):Optional.empty();}
    private static void writeText(RegistryFriendlyByteBuf b,String value){b.writeUtf(value==null?"":value,MAX_TEXT);}
    private static String readText(RegistryFriendlyByteBuf b){return b.readUtf(MAX_TEXT);}
    private static void writeCount(RegistryFriendlyByteBuf b,int count,int maximum){if(count<0||count>maximum)throw new IllegalArgumentException("Snapshot count exceeds bound");b.writeVarInt(count);}
    private static int readCount(RegistryFriendlyByteBuf b,int maximum){int count=b.readVarInt();if(count<0||count>maximum)throw new IllegalArgumentException("Snapshot count exceeds bound");return count;}
}
