package com.kelp.campchecklist;

import com.kelp.campchecklist.api.progress.ConditionStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/** Immutable client DTO. LDLib2 screens can consume this without depending on server classes. */
public record ViewModel(List<Tab> tabs,List<Goal> goals,List<String> diagnostics) {
    public static final ViewModel EMPTY=new ViewModel(List.of(),List.of(),List.of());
    public record Tab(String id,String title,String description,String icon,int order) {}
    /** Common recursive snapshot. It is separate from the public addon API and from UI rows. */
    public record Evaluation(ConditionStatus status, Optional<Progress> numeric, Optional<Component> progressText,
                             Optional<Component> unavailableReason, List<EvaluationDetail> details, boolean detailsTruncated) {
        public Evaluation { numeric=Objects.requireNonNull(numeric); progressText=progressText.map(Component::copy);
            unavailableReason=unavailableReason.map(Component::copy); details=List.copyOf(details); }
    }
    public record Progress(double current,double target) {}
    public record EvaluationDetail(String key,Display display,Evaluation evaluation) {
        public EvaluationDetail { display=new Display(display.label(),display.icon()); }
    }
    public record Display(Component label,Optional<ItemStack> icon) {
        public Display { label=label.copy(); icon=icon.filter(s->!s.isEmpty()).map(ItemStack::copy); }
        @Override public Component label() { return label.copy(); }
        @Override public Optional<ItemStack> icon() { return icon.map(ItemStack::copy); }
    }
    /** Server-computed detail for a composite goal; the client never parses goal JSON. */
    public record Detail(String id,String label,double current,double target,String unit,String displayUnit,String progressText) {
        public Detail(String id,String label,double current,double target,String unit,String displayUnit) { this(id,label,current,target,unit,displayUnit,""); }
        public Detail {
            id = id == null ? "" : id;
            label = label == null ? "" : label;
            unit = unit == null ? "count" : unit;
            displayUnit = displayUnit == null ? unit : displayUnit;
            progressText = progressText == null ? "" : progressText;
        }
        public double displayCurrent() { return displayValue(current); }
        public double displayTarget() { return displayValue(target); }
        private double displayValue(double value) {
            double meters = switch (unit) {
                case "kilometers" -> value * 1000;
                case "blocks", "meters" -> value;
                default -> value;
            };
            return switch (displayUnit) {
                case "kilometers" -> meters / 1000;
                default -> meters;
            };
        }
    }
    public record Goal(String id,String tab,String title,String description,String icon,int order,
                       boolean completed,boolean manual,double current,double target,double progress,
                       String unit,String displayUnit,boolean unavailable,String reason,List<Detail> details,Evaluation evaluation) {
        public Goal(String id,String tab,String title,String description,String icon,int order,
                    boolean completed,boolean manual,double current,double target,double progress,
                    String unit,String displayUnit,boolean unavailable,String reason) {
            this(id,tab,title,description,icon,order,completed,manual,current,target,progress,
                    unit,displayUnit,unavailable,reason,List.of(),null);
        }
        public Goal(String id,String tab,String title,String description,String icon,int order,boolean completed,boolean manual,double current,double target,double progress,String unit,String displayUnit,boolean unavailable,String reason,List<Detail> details) { this(id,tab,title,description,icon,order,completed,manual,current,target,progress,unit,displayUnit,unavailable,reason,details,null); }
        public Goal {
            details = details == null ? List.of() : List.copyOf(details);
        }
        public double displayCurrent() { return displayValue(current); }
        public double displayTarget() { return displayValue(target); }
        private double displayValue(double value) {
            double meters = switch (unit) {
                case "kilometers" -> value * 1000;
                case "blocks", "meters" -> value;
                default -> value;
            };
            return switch (displayUnit) {
                case "kilometers" -> meters / 1000;
                default -> meters;
            };
        }
    }
    public double completionFraction() {
        long available=goals.stream().filter(g -> !g.unavailable()).count();
        return available==0 ? 0 : (double)goals.stream().filter(g -> !g.unavailable() && g.completed()).count()/available;
    }
}
