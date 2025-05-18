package tlc2.controlled.protocol;

import java.util.*;
import tlc2.tool.TLCState;

public class DefaultStateAbstractor implements StateAbstractor {
    public List<TLCState> doAbstraction(List<TLCState> states) {
        List<TLCState> uniqueStates = new ArrayList<>();
        int i = 0, j = 1;
        for (; j < states.size(); j++) {
            long iFingerprint = states.get(i).fingerPrint();
            long jFingerprint = states.get(j).fingerPrint();
            if (iFingerprint != jFingerprint) {
                uniqueStates.add(states.get(i));
                i = j;
            }
        }
        if (i == states.size() -1) {
            uniqueStates.add(states.get(i));
        }
        return uniqueStates;
    }
}
