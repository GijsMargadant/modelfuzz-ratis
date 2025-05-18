package tlc2.controlled.protocol;

import java.util.*;

public class StateAbstractorFactory {
    public static StateAbstractor getStateAbstractor(Map<String, String> params) {
         String name = "";
         if(params.containsKey("name")) {
             name = params.get("name");
         } else if (params.containsKey("abs")){
             name = params.get("abs");
         }
         if(name.equalsIgnoreCase("raft")){
             return new RaftStateAbstractor(params);
         }
        return new DefaultStateAbstractor();
    }
}
