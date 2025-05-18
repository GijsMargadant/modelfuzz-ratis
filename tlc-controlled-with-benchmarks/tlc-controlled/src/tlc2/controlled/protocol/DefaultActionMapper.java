package tlc2.controlled.protocol;

import java.util.List;
import tlc2.tool.Action;

public class DefaultActionMapper extends BaseActionMapper {
    public DefaultActionMapper(List<Action> enabledActions) {
        super(enabledActions);
    }

    public Action mapAction(AbstractAction abstractAction) {
        return enabledActions.get(0);
    }
}
