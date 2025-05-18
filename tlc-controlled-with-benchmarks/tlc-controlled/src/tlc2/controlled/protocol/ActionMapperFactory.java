package tlc2.controlled.protocol;

import java.util.List;
import java.util.Map;

import tlc2.tool.Action;

public class ActionMapperFactory {
    public static ActionMapper getMapper(Map<String, String> params, List<Action> enabledActions, String model) {
        String name = "";
        if(params.containsKey("name")) {
            name = params.get("name");
        }
        if (model.contains("TPC") || name.equalsIgnoreCase("tpc")) {
            boolean isAbstract = false;
            if(params.containsKey("abstract")) {
                isAbstract = true;
            }
            return new TPCActionMapper(enabledActions, isAbstract);
        } else if (model.contains("RAFT_CRASHES") || name.equalsIgnoreCase("raft_crashes")) {
            return new RaftCrashesActionMapper(enabledActions);
        } else if (model.contains("RAFT") || name.equalsIgnoreCase("raft")) {
            boolean isAbstract = false;
            if(params.containsKey("abstract")) {
                isAbstract = true;
            }
            return new RaftActionMapper(enabledActions, isAbstract);
        }
        return new DefaultActionMapper(enabledActions);
    }
}
