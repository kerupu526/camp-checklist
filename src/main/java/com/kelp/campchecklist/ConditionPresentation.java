package com.kelp.campchecklist;

import com.kelp.campchecklist.api.progress.*;
import java.util.*;

/** Bounded server-to-client presentation copy and the client-only compact row projection. */
final class ConditionPresentation {
    static final int MAX_DETAIL_DEPTH=16, MAX_DETAIL_NODES_PER_GOAL=256, MAX_DETAIL_NODES_PER_SNAPSHOT=2048;
    private ConditionPresentation() {}
    static ViewModel.Evaluation copy(ConditionResult result,int[] remaining) { return copy(result,0,remaining); }
    private static ViewModel.Evaluation copy(ConditionResult result,int depth,int[] remaining) {
        boolean truncated=depth>=MAX_DETAIL_DEPTH; List<ViewModel.EvaluationDetail> details=new ArrayList<>();
        if (!truncated) for (ConditionDetail detail:result.details()) {
            if (remaining[0]--<=0) { truncated=true; break; }
            var display=detail.display(); var child=copy(detail.result(),depth+1,remaining);
            details.add(new ViewModel.EvaluationDetail(detail.key(),new ViewModel.Display(display.label(),display.itemIcon()),child));
            truncated|=child.detailsTruncated();
        }
        return new ViewModel.Evaluation(result.status(),result.progress().map(p->new ViewModel.Progress(p.current(),p.target())),result.progressText(),result.unavailableReason(),details,truncated);
    }
    static List<ViewModel.Detail> rows(ViewModel.Evaluation root) { if(root==null)return List.of(); List<ViewModel.Detail> rows=new ArrayList<>(); rows(root,"",0,rows); return List.copyOf(rows); }
    private static void rows(ViewModel.Evaluation evaluation,String parent,int depth,List<ViewModel.Detail> rows) {
        for (ViewModel.EvaluationDetail detail:evaluation.details()) {
            String identity=parent.isEmpty()?detail.key():parent+"/"+detail.key(); var child=detail.evaluation();
            String label="  ".repeat(depth)+detail.display().label().getString();
            double current=child.numeric().map(ViewModel.Progress::current).orElse(0d), target=child.numeric().map(ViewModel.Progress::target).orElse(0d);
            String id=detail.display().icon().map(s->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).orElse(identity);
            String progress=child.progressText().map(c->c.getString()).orElse("");
            rows.add(new ViewModel.Detail(id,label,current,target,"count","count",progress)); rows(child,identity,depth+1,rows);
        }
    }
}
