package tlc2.controlled;

import tlc2.controlled.protocol.ActionMapper;
import tlc2.controlled.protocol.ActionWrapper;
import tlc2.tool.Action;
import tlc2.tool.TLCState;

import java.util.List;

public abstract class ActionController {

    protected ActionMapper mapper;
    protected Action[] enabledActions;

    // The list of all actions asked by the controller, collected during the interaction
    protected List<Action> actionsToRun;

    // The list of all states visited in correspondence, collected during the interaction
    protected List<TLCState> statesVisited;

    public ActionController(ActionMapper mapper, Action[] actions) {
        this.mapper = mapper;
        this.enabledActions = actions;
    }

    // returns the next action in jason format
    public abstract List<ActionWrapper> getNextActions();

    // sets the current state of the execution
    public abstract void setVisitedStates(List<TLCState> state);
}
