package tlc2.controlled.protocol;

import java.util.List;

public interface ActionMapper {

    // Takes an action in the json form and maps it to Action in TLAChecker
    ActionWrapper mapSingleAction(String actionString);

    // Takes a list of actions in the json form and maps it to List<Action>
    List<ActionWrapper> mapListOfActions(String actionsString);
}
