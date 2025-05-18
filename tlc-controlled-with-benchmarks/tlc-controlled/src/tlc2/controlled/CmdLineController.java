package tlc2.controlled;

import tlc2.controlled.protocol.ActionMapper;
import tlc2.controlled.protocol.ActionWrapper;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.util.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CmdLineController extends ActionController {

    private final BufferedReader reader;
    private TLCState currentState;


    public CmdLineController(ActionMapper mapper, Action[] actions, TLCState initialState) {
        super(mapper, actions);
        System.out.println("[Controller] Started the command line controller. Takes the next action from the user.");
        reader = new BufferedReader(new InputStreamReader(System.in));
        actionsToRun = new ArrayList<>();
        statesVisited = new ArrayList<>();
        statesVisited.add(initialState);
        currentState = initialState;

        // System.out.println("List of actions: ");
        // System.out.println(getActionsAsStr(actions));
    }

    @Override
    public List<ActionWrapper> getNextActions() {
        System.out.println("[Controller] ---- Selection of the next action ----" );
        // System.out.println("[Controller] Current state: " + currentState.toString());
        // System.out.println("[Controller] Actions: \n" + getActionsAsStr(actions)); //TODO Global setting for prints
        System.out.println("[Controller] Enter the next action (-1 for quit): " );
        List<ActionWrapper> mappedActions = new ArrayList<>();
        while (mappedActions.size() == 0) {
            String input = readInput();
            mappedActions = mapper.mapListOfActions(input);
            if (mappedActions.size() == 0) {
                System.out.println("[Controller] Input does not map to any action, please try again");
            }
        }
        return mappedActions;
    }

    @Override
    public void setVisitedStates(List<TLCState> states) {
        if(states == null || states.isEmpty()) {
            System.out.println("No next state for that action. Choose again.\n\n");
        } else {
            TLCState s = states.get(states.size()-1);
            System.out.println("Setting the state to: " + s);
            currentState = s;
            statesVisited.addAll(states);
        }
    }

    private String readInput() {
        while(true){
            try {
                return reader.readLine();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private String getActionsAsStr(Action[] actions) {
        StringBuilder s = new StringBuilder();
        for(int i = 0; i < actions.length; i++) {
            Action a = actions[i];
            s.append("   Action #").append(i).append(": ").append(a.getName());
            // s.append(new AbstractAction(a.));

            // Prints parameters:
            Context c = a.con.next();
            s.append("(");
            for(int j = 2; j < a.con.depth(); j++) {
                s.append(c.getValue());
                s.append(",");
                c = c.next();
            }
            s.append(")");

            // s.append(" id: ").append(a.pred.myUID);

            s.append("\n");
        }
        return s.toString();
    }
}
