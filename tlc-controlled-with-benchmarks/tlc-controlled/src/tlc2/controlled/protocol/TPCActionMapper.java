package tlc2.controlled.protocol;

import tlc2.tool.Action;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// TwoPhaseCommit (TwoPhaseCommit with parametric number of transaction requests) action mapper
public class TPCActionMapper extends BaseActionMapper {
    
    private boolean isAbstract;

    public TPCActionMapper(List<Action> enabledActions, boolean isAbstract) {
        super(enabledActions);
        this.isAbstract = isAbstract;
    }

    private Optional<Integer> getRequestId(AbstractAction abstractAction) {
        if(!abstractAction.params.containsKey("request_id")) {
            return Optional.empty();
        }
        Object requestIDObject = abstractAction.params.get("request_id");
        if (requestIDObject instanceof String) {
            Integer requestId = Integer.parseInt((String) abstractAction.params.get("request_id"));
            return Optional.of(requestId);
        }
        try {
            Double requestID = (Double) requestIDObject;
            return Optional.of(requestID.intValue());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    protected Action mapClientRequest() {
        if (!this.enabledActionMap.containsKey("NextRequest")) {
            return null;
        }
        return this.enabledActionMap.get("NextRequest").get(0); 
    }

    protected Action mapIAction(String key, int request) {
        if (!this.enabledActionMap.containsKey(key)) {
            return null;
        }
        for (Action a: this.enabledActionMap.get(key)) {
            Map<String, Value> params = a.getParams();
            if (params.containsKey("i")) {
                IntValue i = (IntValue) params.get("i");
                if (i.val == request) {
                    return a;
                }
            }
        }
        return null;
    }

    protected Action mapRIAction(String key, int rm, int request) {
        if (!this.enabledActionMap.containsKey(key)) {
            return null;
        }
        for (Action a: this.enabledActionMap.get(key)) {
            Map<String, Value> params = a.getParams();
            if (params.containsKey("i") && params.containsKey("r")) {
                IntValue i = (IntValue) params.get("i");
                IntValue r = (IntValue) params.get("r");
                if (i.val == request && r.val == rm) {
                    return a;
                }
            }
        }
        return null;
    }

    protected Action mapTMSendPrepared(int requestID) {
        if (this.isAbstract) {
            return null;
        }
        return this.mapIAction("TMSendPrepareReq", requestID);
    }

    protected Action mapTMSendGlobalCommit(int requestID) {
        if (this.isAbstract) {
            return null;
        }
        return this.mapIAction("TMSendPrepareReq", requestID);
    }

    protected Action mapTMRcvPrepared(int rm, int requestID) {
        if (this.isAbstract) {
            return null;
        }
        return this.mapRIAction("TMRcvPrepared", rm, requestID);
    }

    protected Action mapTMRcvAborted(int rm, int requestID) {
        if (this.isAbstract) {
            return null;
        }
        return this.mapRIAction("TMRcvAborted", rm, requestID);
    }

    protected Action mapRMSendPrepared(int rm, int requestID) {
        String key = this.isAbstract ? "RMPrepared" : "RMSendPrepared";
        return this.mapRIAction(key, rm, requestID);
    }

    protected Action mapRMSendAborted(int rm, int requestID) {
        String key = this.isAbstract ? "RMAborted" : "RMSendAborted";
        return this.mapRIAction(key, rm, requestID);
    }

    protected Action mapRMRcvPrepareReq(int rm, int requestID) {
        if (this.isAbstract) {
            return null;
        }
        return this.mapRIAction("RMRcvPrepareReq", rm, requestID);
    }

    protected Action mapRMRcvGlobalAbort(int rm, int requestID) {
        String key = this.isAbstract ? "RMAborted" : "RMRcvGlobalAbort";
        return this.mapRIAction(key, rm, requestID);
    }

    protected Action mapRMRcvGlobalCommit(int rm, int requestID) {
        String key = this.isAbstract ? "RMCommitted" : "RMRcvGlobalCommit";
        return this.mapRIAction(key, rm, requestID);
    }

    public Action mapAction(AbstractAction abstractAction) {
        try{
            String message = abstractAction.name;
            
            switch (message) {
                case "SendEvent":
                    String event = (String) abstractAction.params.get("event");
                    switch (event) {
                        case "TwoPhaseCommit.ClientRequestEvent":
                            return this.mapClientRequest();
                        case "TwoPhaseCommit.RequestEvent":
                            Optional<Integer> requestId = getRequestId(abstractAction);
                            if (requestId.isEmpty()) {
                                return null;
                            }
                            int request = requestId.get();
                            if (!this.enabledActionMap.containsKey("TMSendPrepareReq")) {
                                return null;
                            }
                            return this.mapTMSendPrepared(request);
                        case "TwoPhaseCommit.PreparedEvent":
                            int sender = Integer.parseInt((String) abstractAction.params.get("sender_id"));

                            requestId = getRequestId(abstractAction);
                            if (requestId.isEmpty()) {
                                return null;
                            }
                            return this.mapRMSendPrepared(sender, requestId.get());
                        case "TwoPhaseCommit.AbortEvent":
                            sender = Integer.parseInt((String) abstractAction.params.get("sender_id"));
                            requestId = getRequestId(abstractAction);
                            if (requestId.isEmpty()) {
                                return null;
                            }
                            return this.mapRMSendAborted(sender, requestId.get());
                            // Map to "RMSendAborted"
                        case "TwoPhaseCommit.GlobalCommitEvent":
                            requestId = getRequestId(abstractAction);
                            if (requestId.isEmpty()) {
                                return null;
                            }
                            return this.mapTMSendGlobalCommit(requestId.get());
                            // Map to "TMSendGlobalCommit"
                        case "TwoPhaseCommit.GlobalAbortEvent":
                            break;
                            // Map to "TMSendGlobalAbort" but the model doesn't have corresponding action for this
                    }
                    break;
                case "ReceiveEvent":
                    event = (String) abstractAction.params.get("event");
                    Optional<Integer> requestId = getRequestId(abstractAction);
                    if (requestId.isEmpty()) {
                        return null;
                    }
                    int i_val = requestId.get();
                    switch (event) {
                        case "TwoPhaseCommit.RequestEvent":
                            int r_val = Integer.parseInt((String) abstractAction.params.get("receiver_id"));
                            return this.mapRMRcvPrepareReq(r_val, i_val);
                            // Map to "RMRcvPrepareReq"
                        case "TwoPhaseCommit.PreparedEvent":
                            r_val = Integer.parseInt((String) abstractAction.params.get("sender_id"));
                            return this.mapTMRcvPrepared(r_val,i_val);
                            // Map to "TMRcvPrepared"
                        case "TwoPhaseCommit.AbortEvent":
                            r_val = Integer.parseInt((String) abstractAction.params.get("sender_id"));
                            return this.mapTMRcvAborted(r_val,i_val);
                            // Map to "TMRcvAborted"
                        case "TwoPhaseCommit.GlobalAbortEvent":
                            r_val = Integer.parseInt((String) abstractAction.params.get("receiver_id"));
                            return this.mapRMRcvGlobalAbort(r_val,i_val);
                            // Map to "RMRcvGlobalAbort"
                        case "TwoPhaseCommit.GlobalCommitEvent":
                            r_val = Integer.parseInt((String) abstractAction.params.get("receiver_id"));
                            return this.mapRMRcvGlobalCommit(r_val,i_val);
                            // Map to "RMRcvGlobalCommit"
                    }
                    break;
            }
            return null;
        } catch (Exception e) {
            System.out.println("[TPCActionMapper] Invalid action");
            System.out.println("Error: "+e.getMessage());
        }

        return null;
    }

}
