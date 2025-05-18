package tlc2.controlled.protocol;

import tlc2.tool.Action;

public class ActionWrapper {
    public Action action;
    private boolean reset;
    private boolean quit;

    private ActionWrapper(Action a) {
        this.action = a;
        this.reset = false;
        this.quit = false;
    }
    private ActionWrapper(boolean reset, boolean quit) {
        this.reset = reset;
        this.quit = quit;
    }

    public static ActionWrapper action(Action a) {
        return new ActionWrapper(a);
    }

    public static ActionWrapper quit() {
        return new ActionWrapper(false, true);
    }

    public static ActionWrapper reset() {
        return new ActionWrapper(true, false);
    }

    public boolean isQuit() {
        return this.quit;
    }

    public boolean isReset() {
        return this.reset;
    }

    public boolean isAction() {
        return this.action != null;
    }
}
