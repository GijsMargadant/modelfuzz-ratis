package tlc2.controlled.protocol;

import java.util.List;
import java.util.Map;

import tlc2.tool.Action;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.Value;

public class RaftCrashesActionMapper extends BaseActionMapper{

    public RaftCrashesActionMapper(List<Action> enabledActions) {
        super(enabledActions);
    }

    protected Action mapAdd(int i) {
        if (!this.enabledActionMap.containsKey("Add")) {
            return null;
        }
        for (Action a : this.enabledActionMap.get("Add")) {
            Map<String, Value> params = a.getParams();
            if (params.containsKey("i")) {
                IntValue i_val = (IntValue) params.get("i");
                if (i_val.val == i) {
                    return a;
                }
            }
        }
        return null;
    }

    protected Action mapUpdateState(int i, String state, int lastIndex, int commitIndex) {
        if (!this.enabledActionMap.containsKey("UpdateState")) {
            return null;
        }
        for (Action a : this.enabledActionMap.get("UpdateState")) {
            Map<String, Value> params = a.getParams();
            IntValue i_val = (IntValue) params.get("i");
            StringValue s_val = (StringValue) params.get("s");
            IntValue li_val = (IntValue) params.get("li");
            IntValue ci_val = (IntValue) params.get("ci");
            if (i_val.val == i && s_val.val.equals(state) && li_val.val == lastIndex && ci_val.val == commitIndex) {
                return a;
            }
        }
        return null;
    }

    protected Action mapUpdateLeaderTerm(int i, int term) {
        if (!this.enabledActionMap.containsKey("UpdateLeaderTerm")) {
            return null;
        }
        for (Action a : this.enabledActionMap.get("UpdateLeaderTerm")) {
            Map<String, Value> params = a.getParams();
            if (params.containsKey("i") && params.containsKey("t")) {
                IntValue i_val = (IntValue) params.get("i");
                IntValue t_val = (IntValue) params.get("t");
                if (i_val.val == i && t_val.val ==term) {
                    return a;
                }
            }
        }
        return null;
    }

    protected Action mapUpdateSnapshot(int i, int sIndex) {
        if (!this.enabledActionMap.containsKey("UpdateSnapshot")) {
            return null;
        }
        for (Action a : this.enabledActionMap.get("UpdateSnapshot")) {
            Map<String, Value> params = a.getParams();
            if (params.containsKey("i") && params.containsKey("si")) {
                IntValue i_val = (IntValue) params.get("i");
                IntValue si_val = (IntValue) params.get("si");
                if (i_val.val == i && si_val.val ==sIndex) {
                    return a;
                }
            }
        }
        return null;
    }

    protected Action mapRemove(int i) {
        if (!this.enabledActionMap.containsKey("Remove")) {
            return null;
        }
        for (Action a : this.enabledActionMap.get("Remove")) {
            Map<String, Value> params = a.getParams();
            if (params.containsKey("i")) {
                IntValue i_val = (IntValue) params.get("i");
                if (i_val.val == i) {
                    return a;
                }
            }
        }
        return null;
    }

    public Action mapAction(AbstractAction abstractAction) {
        try {
            String name = abstractAction.name;
            switch(name) {
                case "MembershipChange":
                    String action = (String) abstractAction.params.get("action");
                    if (action == null ) {
                        return null;
                    }
                    Double node = (Double) abstractAction.params.get("node");
                    if (action.equals("Add")) {
                        return this.mapAdd(node.intValue());
                    } else if (action.equals("Remove")) {
                        return this.mapRemove(node.intValue());
                    }
                case "Add":
                    Double i = (Double) abstractAction.params.get("i");
                    return this.mapAdd(i.intValue());
                case "UpdateState":
                    i = (Double) abstractAction.params.get("i");
                    String state = (String) abstractAction.params.get("state");
                    Double li = (Double) abstractAction.params.get("last_index");
                    Double ci = (Double) abstractAction.params.get("commit_index");
                    return this.mapUpdateState(i.intValue(), state, li.intValue(), ci.intValue());
                case "BecomeLeader":
                    node = (Double) abstractAction.params.get("node");
                    Double term = (Double) abstractAction.params.get("term");
                    return this.mapUpdateLeaderTerm(node.intValue(), term.intValue());
                case "UpdateSnapshot":
                    node = (Double) abstractAction.params.get("node");
                    Double si = (Double) abstractAction.params.get("snapshot_index");
                    return this.mapUpdateSnapshot(node.intValue(), si.intValue());
                case "Remove":
                    i = (Double) abstractAction.params.get("i");
                    return this.mapRemove(i.intValue());
            }
        } catch (Exception e) {

        }
        return null;
    }
}
