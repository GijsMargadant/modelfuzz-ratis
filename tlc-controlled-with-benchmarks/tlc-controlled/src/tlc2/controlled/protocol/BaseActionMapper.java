package tlc2.controlled.protocol;

import java.util.*;
import java.lang.reflect.*;

import com.google.gson.*;
import com.google.gson.annotations.*;

import tlc2.tool.Action;

public abstract class BaseActionMapper implements ActionMapper {
    
    protected final List<Action> enabledActions;
    protected HashMap<String, List<Action>> enabledActionMap;

    public BaseActionMapper(List<Action> enabledActions) {
        this.enabledActions = enabledActions;
        this.enabledActionMap = new HashMap<String, List<Action>>();
        for (Action a: enabledActions) {
            String name = a.getName().toString();
            List<Action> currentActions;
            if (!this.enabledActionMap.containsKey(name)) {
                currentActions = new ArrayList<Action>();
            } else {
                currentActions = this.enabledActionMap.get(name);
            }
            currentActions.add(a);
            this.enabledActionMap.put(name, currentActions);
        }
    }

    protected abstract Action mapAction(AbstractAction a);

    public ActionWrapper mapSingleAction(String actionString) {
        AbstractAction abstractAction = fromJson(actionString);
        if (abstractAction.isReset()) {
            return ActionWrapper.reset();
        }
        return ActionWrapper.action(mapAction(abstractAction));
    }

    public List<ActionWrapper> mapListOfActions(String actionString) {
        List<ActionWrapper> outList = new ArrayList<>();
        List<AbstractAction> abstractActions = listFromJson(actionString);
        for(AbstractAction a:abstractActions) {
            if(a.isReset()) {
                outList.add(ActionWrapper.reset());
            } else {
                Action mappedAction = mapAction(a);
                if (mappedAction != null) {
                    outList.add(ActionWrapper.action(mappedAction));
                }
            }
        }
        return outList;
    }

    private static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Base64.getDecoder().decode(json.getAsString());
        }

        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(Base64.getEncoder().encodeToString(src));
        }
    }

    private static Gson getGsonInstance() {
        return new GsonBuilder().registerTypeHierarchyAdapter(byte[].class,
            new ByteArrayToBase64TypeAdapter()).create();
    }

    protected AbstractAction fromJson(String jsonString) {
        try{
            Gson gson = getGsonInstance();
            AbstractAction action = gson.fromJson(jsonString, AbstractAction.class);
            return action;
        } catch (JsonSyntaxException e) {
            System.out.println("[AbstractAction] Invalid action. Error: "+e.getMessage());
        }
        return null;
    }

    protected List<AbstractAction> listFromJson(String listJsonString) {
        List<AbstractAction> actions = new ArrayList<AbstractAction>();
        try{
            Gson gson = getGsonInstance();
            AbstractAction[] array = gson.fromJson(listJsonString, AbstractAction[].class);
            actions.addAll(Arrays.asList(array));
        } catch (JsonSyntaxException e) {
            System.out.println("[AbstractAction] Invalid action. Error: "+e.getMessage());
        }

        return actions;
    }

    protected class AbstractAction {

        @SerializedName(value = "name", alternate = {"Name"})
        public final String name;

        @SerializedName(value = "params", alternate = {"Params"})
        public final HashMap<String, Object> params;


        @SerializedName(value = "reset", alternate = {"Reset"})
        public final boolean reset;

        public AbstractAction(String name, HashMap<String, Object> params, boolean reset) {
            this.name = name;
            this.params = params;
            this.reset = reset;
        }

        public boolean isReset() {
            return this.reset;
        }
    }
}
