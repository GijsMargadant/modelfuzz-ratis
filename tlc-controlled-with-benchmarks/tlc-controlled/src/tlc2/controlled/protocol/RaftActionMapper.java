package tlc2.controlled.protocol;

import java.nio.charset.StandardCharsets;
import java.util.*;
import tlc2.tool.Action;
import tlc2.value.impl.BoolValue;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.Value;

public class RaftActionMapper extends BaseActionMapper{

    private HashMap<String, Action> mappedActions;
    private boolean isAbstract;

    public RaftActionMapper(List<Action> enabledActions, boolean isAbstract) {
        super(enabledActions);
        this.isAbstract = isAbstract;
        this.mappedActions = new HashMap<String, Action>();
        for (Action a : enabledActions) {
            String name = a.getName().toString();
            switch(name){
                case "HandleRequestVoteRequest":
                    Map<String, Value> params = a.getParams();
                    IntValue iP = (IntValue) params.get("i");
                    IntValue jP = (IntValue) params.get("j");
                    IntValue lTermP = (IntValue) params.get("lTerm");
                    IntValue lIndexP = (IntValue) params.get("lIndex");
                    IntValue termP = (IntValue) params.get("term");
                    String key = String.format("%s_%d_%d_%d_%d_%d", name, iP.val, jP.val, lTermP.val, lIndexP.val, termP.val);
                    mappedActions.put(key, a);
                    break;
                case "HandleRequestVoteResponse":
                    params = a.getParams();
                    iP = (IntValue) params.get("i");
                    jP = (IntValue) params.get("j");
                    termP  = (IntValue) params.get("term");
                    BoolValue grantP = (BoolValue) params.get("grant");
                    key = String.format("%s_%d_%d_%d_%b", name, iP.val, jP.val, termP.val, grantP.val);
                    mappedActions.put(key, a);
                    break;
                case "HandleNilAppendEntriesRequest":
                    params = a.getParams();
                    iP = (IntValue) params.get("i");
                    jP = (IntValue) params.get("j");
                    termP  = (IntValue) params.get("term");
                    IntValue pLogIndexP  = (IntValue) params.get("pLogIndex");
                    IntValue pLogTermP  = (IntValue) params.get("pLogTerm");
                    IntValue cIndexP = (IntValue) params.get("cIndex");
                    key = String.format("%s_%d_%d_%d_%d_%d_%d", name, iP.val, jP.val, termP.val, pLogIndexP.val, pLogTermP.val, cIndexP.val);
                    mappedActions.put(key, a);
                    break;
                case "HandleAppendEntriesRequest":
                    params = a.getParams();
                    iP = (IntValue) params.get("i");
                    jP = (IntValue) params.get("j");
                    termP  = (IntValue) params.get("term");
                    pLogIndexP  = (IntValue) params.get("pLogIndex");
                    pLogTermP  = (IntValue) params.get("pLogTerm");
                    IntValue entryTermP = (IntValue) params.get("entryTerm");
                    IntValue entryValueP = (IntValue) params.get("entryValue");
                    cIndexP = (IntValue) params.get("cIndex");
                    key = String.format("%s_%d_%d_%d_%d_%d_%d_%d_%d", name, iP.val, jP.val, termP.val, pLogIndexP.val, pLogTermP.val, entryTermP.val, entryValueP.val, cIndexP.val);
                    mappedActions.put(key, a);
                    break;
                case "HandleAppendEntriesResponse":
                    params = a.getParams();
                    iP = (IntValue) params.get("i");
                    jP = (IntValue) params.get("j");
                    termP  = (IntValue) params.get("term");
                    BoolValue successP = (BoolValue) params.get("success");
                    IntValue mIndexP = (IntValue) params.get("mIndex");
                    key = String.format("%s_%d_%d_%d_%b_%d", name, iP.val, jP.val, termP.val, successP.val, mIndexP.val);
                    mappedActions.put(key, a);
                    break;
                case "UpdateSnapshotIndex":
                    params = a.getParams();
                    iP = (IntValue) params.get("i");
                    IntValue siP = (IntValue) params.get("si");
                    key = String.format("%s_%d_%d", name, iP.val, siP.val);
                    mappedActions.put(key, a);
                    break;
                case "AddToActive":
                    params = a.getParams();
                    iP = (IntValue) params.get("i");
                    key = String.format("%s_%d", name, iP.val);
                    mappedActions.put(key, a);
                    break;
                case "RemoveFromActive":
                    params = a.getParams();
                    iP = (IntValue) params.get("i");
                    key = String.format("%s_%d", name, iP.val);
                    mappedActions.put(key, a);
                    break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> getParam(AbstractAction abstractAction, String param) {
        if(!abstractAction.params.containsKey(param)) {
            return Optional.empty();
        }
        Object paramValue = abstractAction.params.get(param);
        try {
            T result = (T) paramValue;
            return Optional.of(result);
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<List<Map<String, Object>>> getEntries(AbstractAction abstractAction) {
        if(!abstractAction.params.containsKey("entries")) {
            return Optional.empty();
        }
        Object paramValue = abstractAction.params.get("entries");
        try {
            List<Map<String, Object>> entries = (List<Map<String, Object>>) paramValue;
            return Optional.of(entries);
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    protected Action mapClientRequest(int requestID, int leader) {
        for(Action a: this.enabledActionMap.get("ClientRequest")) {
            Map<String, Value> params = a.getParams();
            if(params.containsKey("i") && params.containsKey("v")) {
                IntValue i = (IntValue) params.get("i");
                IntValue v = (IntValue) params.get("v");
                if (i.val == leader && v.val == requestID) {
                    return a;
                }
            }
        }
        return null;
    }

    protected Action mapBecomeLeader(int node) {
        String actionKey = "BecomeLeader";
        if (this.isAbstract) {
            actionKey = "ElectLeader";
        }
        for (Action a : this.enabledActionMap.get(actionKey)) {
            Map<String, Value> params = a.getParams();
            if(params.containsKey("i")) {
                IntValue i = (IntValue) params.get("i");
                if (i.val == node) {
                    return a;
                }
            }
        }
        return null;
    }

    protected Action mapTimeout(int node) {
        for (Action a : this.enabledActionMap.get("Timeout")) {
            Map<String, Value> params = a.getParams();
            if(params.containsKey("i")) {
                IntValue i = (IntValue) params.get("i");
                if (i.val == node) {
                    return a;
                }
            }
        }
        return null;
    }

    protected Action mapAdvanceCommitIndex(int node) {
        for (Action a : this.enabledActionMap.get("AdvanceCommitIndex")) {
            Map<String, Value> params = a.getParams();
            if(params.containsKey("i")) {
                IntValue i = (IntValue) params.get("i");
                if (i.val == node) {
                    return a;
                }
            }
        }
        return null;
    }

    protected Action mapHandleRequestVoteRequest(int i, int j, int lTerm, int lIndex, int term) {
        if (this.isAbstract) {
            return null;
        }
        String key = String.format("%s_%d_%d_%d_%d_%d","HandleRequestVoteRequest", i, j, lTerm, lIndex, term);
        if (this.mappedActions.containsKey(key)) {
            return this.mappedActions.get(key);
        } 
        return null;
    }

    protected Action mapHandleRequestVoteResponse(int i, int j, int term, boolean grant) {
        if (this.isAbstract) {
            return null;
        }
        String key = String.format("%s_%d_%d_%d_%b", "HandleRequestVoteResponse", i, j, term, grant);
        if (this.mappedActions.containsKey(key)) {
            return this.mappedActions.get(key);
        } 
        return null;
    }

    protected Action mapHandleAppendEntriesResponse(int i, int j, int term, boolean success, int mIndex) {
        String key = String.format("%s_%d_%d_%d_%b_%d", "HandleAppendEntriesResponse", i, j, term, success, mIndex);
        if (this.mappedActions.containsKey(key)) {
            return this.mappedActions.get(key);
        } 
        return null;
    }

    protected Action mapHandleAppendEntriesRequest(int i,int j,int pLogIndex,int pLogTerm,int term, List<Map<String, Object>> entries ,int cIndex) {
        if(entries.size() == 0) {
            String key = String.format("%s_%d_%d_%d_%d_%d_%d", "HandleNilAppendEntriesRequest", i, j, term, pLogIndex, pLogTerm, cIndex);
            if (this.mappedActions.containsKey(key)) {
                return this.mappedActions.get(key);
            }
        } else {
            Double eTerm = (Double) entries.get(0).get("Term");
            int eValue = 0;
            if(entries.get(0).containsKey("Data")) {
                Object data = entries.get(0).get("Data");
                String s = "";
                if (data instanceof byte[]) {
                    byte[] data_b = (byte[]) entries.get(0).get("Data");
                    s = new String(data_b, StandardCharsets.UTF_8);
                } else if(data instanceof String) {
                    s = (String) data;
                } else {
                    return null;
                }

                try {
                    eValue = Integer.parseInt(s);
                } catch (Exception e) {
                    return null;
                }
            }
            String key = String.format("%s_%d_%d_%d_%d_%d_%d_%d_%d", "HandleAppendEntriesRequest", i, j, term, pLogIndex, pLogTerm, eTerm.intValue(), eValue, cIndex);
            if (this.mappedActions.containsKey(key)) {
                return this.mappedActions.get(key);
            }
        }
        return null;
    }

    protected Action mapUpdateSnapshotIndex(int i, int si) {
        String key = String.format("%s_%d_%d", "UpdateSnapshotIndex", i, si);
        if (this.mappedActions.containsKey(key)) {
            return this.mappedActions.get(key);
        }
        return null;
    }

    protected Action mapAddToActive(int i) {
        String key = String.format("AddToActive_%d", i);
        if (this.mappedActions.containsKey(key)) {
            return this.mappedActions.get(key);
        }
        return null;
    }

    protected Action mapRemoveFromActive(int i) {
        String key = String.format("RemoveFromActive_%d", i);
        if (this.mappedActions.containsKey(key)) {
            return this.mappedActions.get(key);
        }
        return null;
    }

    public Action mapAction(AbstractAction abstractAction) {
        try {
            switch (abstractAction.name) {
                case "AdvanceCommitIndex":
                    Double i = (Double) abstractAction.params.get("i");
                    return this.mapAdvanceCommitIndex(i.intValue());
                case "MembershipChange":
                    String action = (String) abstractAction.params.get("action");
                    if (action == null ) {
                        return null;
                    }
                    Double node = (Double) abstractAction.params.get("node");
                    if (action.equals("Add")) {
                        return this.mapAddToActive(node.intValue());
                    } else if (action.equals("Remove")) {
                        return this.mapRemoveFromActive(node.intValue());
                    }
                case "Add":
                    i = (Double) abstractAction.params.get("i");
                    return this.mapAddToActive(i.intValue());
                case "UpdateSnapshot":
                    node = (Double) abstractAction.params.get("node");
                    Double si = (Double) abstractAction.params.get("snapshot_index");
                    return this.mapUpdateSnapshotIndex(node.intValue(), si.intValue());
                case "Remove":
                    i = (Double) abstractAction.params.get("i");
                    return this.mapRemoveFromActive(i.intValue());
                case "ClientRequest":
                    Optional<Double> requestID = this.getParam(abstractAction, "request");
                    Optional<Double> leader = this.getParam(abstractAction, "leader");
                    if(requestID.isEmpty() || leader.isEmpty()) {
                        return null;
                    }
                    return mapClientRequest(requestID.get().intValue(), leader.get().intValue());
                case "SendMessage":
                    break;
                case "DeliverMessage":
                    Optional<String> messageType = this.getParam(abstractAction, "type");
                    if(messageType.isEmpty()) {
                        return null;
                    }
                    switch (messageType.get()) {
                        case "MsgVote":
                            // Handle request vote 
                            Optional<Double> sender = this.getParam(abstractAction, "from");
                            Optional<Double> receiver = this.getParam(abstractAction, "to");
                            Optional<Double> term = this.getParam(abstractAction, "term");
                            Optional<Double> logTerm  = this.getParam(abstractAction, "log_term");
                            Optional<Double> logIndex = this.getParam(abstractAction, "index");
                            return mapHandleRequestVoteRequest(
                                receiver.get().intValue(), 
                                sender.get().intValue(), 
                                logTerm.get().intValue(), 
                                logIndex.get().intValue(), 
                                term.get().intValue()
                            );
                        case "MsgVoteResp":
                            // Handle request vote response
                            sender = this.getParam(abstractAction, "from");
                            receiver = this.getParam(abstractAction, "to");
                            term = this.getParam(abstractAction, "term");
                            Optional<Boolean> grant = this.getParam(abstractAction, "reject");
                            return mapHandleRequestVoteResponse(
                                receiver.get().intValue(),
                                sender.get().intValue(), 
                                term.get().intValue(),
                                !grant.get().booleanValue()
                            );
                        case "MsgApp":
                            // Handle append entries request
                            sender = this.getParam(abstractAction, "from");
                            receiver = this.getParam(abstractAction, "to");
                            term = this.getParam(abstractAction, "term");
                            Optional<Double> cIndex = this.getParam(abstractAction, "commit");
                            Optional<Double> pLogTerm  = this.getParam(abstractAction, "log_term");
                            Optional<Double> pLogIndex = this.getParam(abstractAction, "index");
                            Optional<List<Map<String, Object>>> entries = this.getEntries(abstractAction);
                            if(entries.isEmpty()) {
                                return null;
                            }
                            return mapHandleAppendEntriesRequest(
                                receiver.get().intValue(), 
                                sender.get().intValue(), 
                                pLogIndex.get().intValue(), 
                                pLogTerm.get().intValue(), 
                                term.get().intValue(), 
                                entries.get(), 
                                cIndex.get().intValue()
                            );
                        case "MsgAppResp":
                            // Handle append entries response
                            sender = this.getParam(abstractAction, "from");
                            receiver = this.getParam(abstractAction, "to");
                            term = this.getParam(abstractAction, "term");
                            grant = this.getParam(abstractAction, "reject");
                            Optional<Double> mIndex = this.getParam(abstractAction, "index");
                            return mapHandleAppendEntriesResponse(
                                receiver.get().intValue(),
                                sender.get().intValue(), 
                                term.get().intValue(),
                                !grant.get().booleanValue(),
                                mIndex.get().intValue()
                            );
                    }
                    break;
                case "BecomeLeader":
                    Optional<Double> nodeID = this.getParam(abstractAction, "node");
                    if(nodeID.isEmpty()) {
                        return null;
                    }
                    return mapBecomeLeader(nodeID.get().intValue());
                case "Timeout":
                    nodeID = this.getParam(abstractAction, "node");
                    if(nodeID.isEmpty()) {
                        return null;
                    }
                    return mapTimeout(nodeID.get().intValue());
                case "SendEvent":
                    String event = (String) abstractAction.params.get("event"); 
                    switch (event) {
                        case "Microsoft.Coyote.Samples.CloudMessaging.Events.VoteRequestEvent":
                            // Handle request vote 
                            Optional<Double> sender = this.getParam(abstractAction, "sender_id");
                            Optional<Double> receiver = this.getParam(abstractAction, "receiver_id");
                            Optional<Double> term = this.getParam(abstractAction, "term");
                            Optional<Double> logTerm  = this.getParam(abstractAction, "log_term");
                            Optional<Double> logIndex = this.getParam(abstractAction, "index");
                            return mapHandleRequestVoteRequest(
                                receiver.get().intValue(), 
                                sender.get().intValue(), 
                                logTerm.get().intValue(), 
                                logIndex.get().intValue(), 
                                term.get().intValue()
                            );
                        case "Microsoft.Coyote.Samples.CloudMessaging.Events.VoteResponseEvent":
                            // Handle request vote response
                            sender = this.getParam(abstractAction, "sender_id");
                            receiver = this.getParam(abstractAction, "receiver_id");
                            term = this.getParam(abstractAction, "term");
                            Optional<Boolean> grant = this.getParam(abstractAction, "reject");
                            return mapHandleRequestVoteResponse(
                                receiver.get().intValue(),
                                sender.get().intValue(), 
                                term.get().intValue(),
                                !grant.get().booleanValue()
                            );
                        case "Microsoft.Coyote.Samples.CloudMessaging.Events.AppendLogEntriesRequestEvent":
                            // Handle append entries request
                            sender = this.getParam(abstractAction, "sender_id");
                            receiver = this.getParam(abstractAction, "receiver_id");
                            term = this.getParam(abstractAction, "term");
                            Optional<Double> cIndex = this.getParam(abstractAction, "commit");
                            Optional<Double> pLogTerm  = this.getParam(abstractAction, "log_term");
                            Optional<Double> pLogIndex = this.getParam(abstractAction, "index");
                            Optional<List<Map<String, Object>>> entries = this.getEntries(abstractAction);
                            if(entries.isEmpty()) {
                                return null;
                            }
                            return mapHandleAppendEntriesRequest(
                                receiver.get().intValue(), 
                                sender.get().intValue(), 
                                pLogIndex.get().intValue(), 
                                pLogTerm.get().intValue(), 
                                term.get().intValue(), 
                                entries.get(), 
                                cIndex.get().intValue()
                            );
                        case "Microsoft.Coyote.Samples.CloudMessaging.Events.AppendLogEntriesResponseEvent":
                            // Handle append entries response
                            sender = this.getParam(abstractAction, "sender_id");
                            receiver = this.getParam(abstractAction, "receiver_id");
                            term = this.getParam(abstractAction, "term");
                            grant = this.getParam(abstractAction, "reject");
                            Optional<Double> mIndex = this.getParam(abstractAction, "index");
                            return mapHandleAppendEntriesResponse(
                                receiver.get().intValue(),
                                sender.get().intValue(), 
                                term.get().intValue(),
                                !grant.get().booleanValue(),
                                mIndex.get().intValue()
                            );
                    }
                case "InvokedAction":
                    action = (String) abstractAction.params.get("action"); 
                    switch (action) {
                        case "BecomeLeader":
                            nodeID = this.getParam(abstractAction, "actor_id");
                            if(nodeID.isEmpty()) {
                                return null;
                            }
                            return mapBecomeLeader(nodeID.get().intValue());
                        case "BecomeCandidate":
                            nodeID = this.getParam(abstractAction, "actor_id");
                            if(nodeID.isEmpty()) {
                                return null;
                            }
                            return mapTimeout(nodeID.get().intValue());
                    }
                case "ReceiveEvent":
                    event = (String) abstractAction.params.get("event"); 
                    switch (event) {
                        case "Microsoft.Coyote.Samples.CloudMessaging.Events.ClientRequestEvent":
                            requestID = this.getParam(abstractAction, "request");
                            leader = this.getParam(abstractAction, "receiver_id");
                            if(requestID.isEmpty() || leader.isEmpty() || leader.get().intValue() < 0) {
                                return null;
                            }
                            return mapClientRequest(requestID.get().intValue(), leader.get().intValue());
                    }
            }
        } catch (Exception e) {
            e.getMessage();
        }
        return null;

        // To Map
        // [ ] 1. Restart(p)
        // [x] 2. Timeout(p)
        // [x] 3. BecomeLeader(p)
        // [x] 4. ClientRequest(i,v)
        // [x] 5. AdvanceCommitIndex(p)
        // [x] 6. HandleRequestVoteRequest(i,j,lTerm,lIndex,term)
        // [x] 7. HandleRequestVoteResponse(i, j, term, grant)
        // [x] 8. HandleNilAppendEntriesRequest(i, j, pLogIndex, pLogTerm, term, cIndex)
        // [x] 9. HandleAppendEntriesRequest(i, j, pLogIndex, pLogTerm, term, entryTerm, entryValue, cIndex)
        // [x] 10. HandleAppendEntriesResponse(i, j, term, success, mIndex)  
    }
}
