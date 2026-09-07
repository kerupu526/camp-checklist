package com.kelp.campchecklist;

import java.util.List;

/** Immutable client DTO. LDLib2 screens can consume this without depending on server classes. */
public record ViewModel(List<Tab> tabs,List<Goal> goals,List<String> diagnostics) {
    public static final ViewModel EMPTY=new ViewModel(List.of(),List.of(),List.of());
    public record Tab(String id,String title,String description,String icon,int order) {}
    /** Server-computed detail for a composite goal; the client never parses goal JSON. */
    public record Detail(String id,String label,double current,double target,String unit,String displayUnit) {
        public Detail {
            id = id == null ? "" : id;
            label = label == null ? "" : label;
            unit = unit == null ? "count" : unit;
            displayUnit = displayUnit == null ? unit : displayUnit;
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
                       String unit,String displayUnit,boolean unavailable,String reason,List<Detail> details) {
        public Goal(String id,String tab,String title,String description,String icon,int order,
                    boolean completed,boolean manual,double current,double target,double progress,
                    String unit,String displayUnit,boolean unavailable,String reason) {
            this(id,tab,title,description,icon,order,completed,manual,current,target,progress,
                    unit,displayUnit,unavailable,reason,List.of());
        }
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
