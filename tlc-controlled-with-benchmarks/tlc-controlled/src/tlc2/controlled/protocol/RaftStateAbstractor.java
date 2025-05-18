package tlc2.controlled.protocol;

import java.util.*;
import tlc2.tool.TLCState;
import tlc2.value.IValue;
import tlc2.value.impl.FcnRcdValue;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.Value;
import util.UniqueString;

public class RaftStateAbstractor extends DefaultStateAbstractor implements StateAbstractor{
    private boolean isAbstract;

    private static Set<String> keysToIgnore = Set.of("votesGranted", "votesResponded", "votedFor");

    public RaftStateAbstractor(Map<String, String> params) {
        super();
        this.isAbstract = params.containsKey("abstract");
    }

    List<UniqueString> diff(TLCState one, TLCState two) {
        List<UniqueString> result = new ArrayList<>();
        Map<UniqueString, IValue> oneValues = one.getVals();
        Map<UniqueString, IValue> twoValues = two.getVals();
        for(Map.Entry<UniqueString,IValue>  val: oneValues.entrySet()) {
            if (!twoValues.containsKey(val.getKey())) {
                result.add(val.getKey());
            } else {
                IValue valOne = val.getValue();
                IValue valTwo = twoValues.get(val.getKey());
                if (valOne.compareTo(valTwo) != 0) {
                    result.add(val.getKey());
                }
            }
        }

        return result;
    }

    boolean isDifferent(TLCState cur, TLCState prev) {
        List<UniqueString> diffValues = this.diff(cur, prev);
        if (!this.isAbstract) {
            return diffValues.size() > 0;
        }

        List<UniqueString> actualDiffValues = new ArrayList<>();
        for (UniqueString k : diffValues) {
            if(!keysToIgnore.contains(k.toString())) {
                actualDiffValues.add(k);
            }
        }

        return actualDiffValues.size() > 0;
    }

    private TLCState rewrite(TLCState s) {
        if (!this.isAbstract) {
            return s;
        }

        FcnRcdValue currentTerms = (FcnRcdValue) s.getVals().get(UniqueString.of("currentTerm"));
        FcnRcdValue states = (FcnRcdValue) s.getVals().get(UniqueString.of("state"));

        Value[] newCurrentTermValues = new IntValue[currentTerms.values.length];
        Value[] newStateValues = new StringValue[states.values.length];
        for (int i = 0; i < states.values.length; i++) {
            StringValue state = (StringValue) states.values[i];
            if (state.val.equals("leader")) {
                newCurrentTermValues[i] = currentTerms.values[i];
                newStateValues[i] = states.values[i];
            } else {
                newCurrentTermValues[i] = IntValue.gen(0);
                newStateValues[i] = new StringValue("follower");
            }
        }

        s.bind(UniqueString.of("currentTerm"), new FcnRcdValue(currentTerms, newCurrentTermValues));
        s.bind(UniqueString.of("state"), new FcnRcdValue(states, newStateValues));
        return s;
    }

    @Override
    public List<TLCState> doAbstraction(List<TLCState> states) {
        List<TLCState> superResult = super.doAbstraction(states);
        List<TLCState> result = new ArrayList<>();
        if (superResult.size() == 0) {
            return states;
        }
        int i = 0, j = 1;
        for(; j < superResult.size(); j++) {
            TLCState cur = rewrite(superResult.get(j));
            TLCState prev = rewrite(superResult.get(i));

            // If cur is not different from previous then don't add current
            if (isDifferent(cur, prev)) {
                result.add(prev);
                i = j;
            }
        }
        if (i == superResult.size() -1) {
            result.add(superResult.get(i));
        }
        return result;
    }
}
