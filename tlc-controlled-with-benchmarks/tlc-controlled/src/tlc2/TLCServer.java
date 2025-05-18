package tlc2;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import com.google.gson.Gson;
import com.sun.net.httpserver.*;

import tlc2.controlled.protocol.ActionMapper;
import tlc2.controlled.protocol.ActionMapperFactory;
import tlc2.controlled.protocol.ActionWrapper;
import tlc2.controlled.protocol.StateAbstractor;
import tlc2.controlled.protocol.StateAbstractorFactory;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.Action;
import tlc2.tool.ITool;
import tlc2.tool.StateVec;
import tlc2.tool.TLCState;
import tlc2.tool.impl.FastTool;
import tlc2.tool.impl.Tool;
import tlc2.value.impl.CounterExample;
import tlc2.util.RandomGenerator;
import tlc2.util.FP64;
import util.FileUtil;
import util.SimpleFilenameToStream;

public class TLCServer extends TLC {

    private ITool tool;
    private ActionMapper mapper;
    private StateAbstractor abstractor;

    public TLCServer() {
        super();
    }

    public void init() {
        this.tool = new FastTool(mainFile, configFile, resolver, Tool.Mode.Simulation, params);
        this.mapper = ActionMapperFactory.getMapper(this.mapperParams, Arrays.asList(this.tool.getActions()), this.tool.getRootName());
        this.abstractor = StateAbstractorFactory.getStateAbstractor(this.mapperParams);
        FP64.Init(fpIndex);
    }

    public List<TLCState> simulate(String input) throws Exception{
        
        StateVec initStates = computeInitStates(this.tool);
		Queue<ActionWrapper> actionsToRun = new ArrayDeque<ActionWrapper>();
		List<TLCState> statesVisited = new ArrayList<TLCState>();

        StateVec nextStates = new StateVec(1);
        TLCState curState = randomState(initStates);

        statesVisited.add(curState);
        actionsToRun.addAll(this.mapper.mapListOfActions(input));
        while(true) {
            nextStates.clear();
            while(nextStates.empty()) {
                ActionWrapper nextAction = actionsToRun.remove();
                if(nextAction.isReset() || nextAction.isQuit() || nextAction.action.equals(Action.UNKNOWN)) {
                    return this.abstractor.doAbstraction(statesVisited);
                }
                nextStates = nextStates.addElements(tool.getNextStates(nextAction.action, curState));
                if(nextStates.empty()) {
                    statesVisited.add(curState);
                }
            }
            assert(nextStates.size() == 1);
            final TLCState s1 = nextStates.elementAt(0);
            s1.execCallable();
            curState = s1;
            statesVisited.add(curState);
        }
    }

    private TLCState randomState(StateVec states) {
        RandomGenerator gen = new RandomGenerator();
        final int len = states.size();
		if (len > 0) {
			final int index = (int) Math.floor(gen.nextDouble() * len);
			return states.elementAt(index);
		}
		return null;
    }

    public StateVec computeInitStates(ITool tool) throws Exception{
        final int res = tool.checkAssumptions();
		if (res != EC.NO_ERROR) {
			throw new Exception("Error checking assumptions: "+res);
		}
		
		TLCState curState = null;
        StateVec initStates;

		//
		// Compute the initial states.
		//
		try {
			// The init states are calculated only ever once and never change
			// in the loops below. Ideally the variable would be final.
			final StateVec inits = tool.getInitStates();
			initStates = new StateVec(inits.size());
			
            Action[] invariants = tool.getInvariants();
			// Check all initial states for validity.
			for (int i = 0; i < inits.size(); i++) {
				curState = inits.elementAt(i);
				if (tool.isGoodState(curState)) {
					for (int j = 0; j < invariants.length; j++) {
						if (!tool.isValid(invariants[j], curState)) {
							// We get here because of invariant violation.
                            String errorMessage = MP.getError(EC.TLC_INVARIANT_VIOLATED_INITIAL,
									new String[] { tool.getInvNames()[j], tool.evalAlias(curState, curState).toString() });
							tool.checkPostConditionWithCounterExample(new CounterExample(curState));
                            throw new Exception(errorMessage);
							
						}
					}
				} else {
					throw new Exception(MP.getError(EC.TLC_STATE_NOT_COMPLETELY_SPECIFIED_INITIAL, curState.toString()));
				}
				
				if (tool.isInModel(curState)) {
					initStates.addElement(curState);
				}
			}
		} catch (Exception e) {
            
			final String errorMessage;
			if (curState != null) {
				errorMessage = MP.getError(EC.TLC_INITIAL_STATE,
						new String[] { (e.getMessage() == null) ? e.toString() : e.getMessage(), curState.toString() });
			} else {
				errorMessage = e.getMessage(); // LL changed call 7 April 2012
			}
			throw new Exception(errorMessage);
		}

		// It appears deepNormalize brings the states into a canonical form to
		// speed up equality checks.
		initStates.deepNormalize();
        return initStates;
    }

    public static class ServerResponse {
        public List<String> states;

        public List<Long> keys;

        public ServerResponse(List<String> states, List<Long> keys) {
            this.states = states;
            this.keys = keys;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Initializing...");
        final TLCServer tlcServer = new TLCServer();
        if (!tlcServer.handleParameters(args)) {
            System.exit(1);
        }
        final String dir = FileUtil.parseDirname(tlcServer.getMainFile());
        if (!dir.isEmpty()) {
            tlcServer.setResolver(new SimpleFilenameToStream(dir));
        } else {
            tlcServer.setResolver(new SimpleFilenameToStream());
        }
        tlcServer.init();
        
        int serverPort = 2023;
        int index = 0;
		while (index < args.length) {
            if (args[index].equals("-serverport")) {
                index++;
                if (index < args.length) {
                    try {
                        serverPort = Integer.parseInt(args[index]);
                    } catch (NumberFormatException e) {
                        MP.printError(EC.WRONG_COMMANDLINE_PARAMS_TLC, "server port should be a number");
                        System.exit(1);;
                    }
                }
            }
            index++;
        }
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(serverPort), 0);
            httpServer.createContext("/execute", new HttpHandler() {
                public void handle(HttpExchange t) throws IOException {
                    if(!t.getRequestMethod().equalsIgnoreCase("POST")) {
                        t.sendResponseHeaders(405, -1);
                        return;
                    }
                    try {
                        byte[] requestBytes = t.getRequestBody().readAllBytes();
                        String request = new String(requestBytes, StandardCharsets.UTF_8);
                        List<TLCState> trace = tlcServer.simulate(request);
                        List<String> stringTrace = new ArrayList<>();
                        List<Long> fingerprintTrace = new ArrayList<>();
                        for( TLCState state : trace) {
                            stringTrace.add(state.toString());
                            fingerprintTrace.add(state.fingerPrint());
                        }
                        Gson gson = new Gson();
                        String response = gson.toJson(new ServerResponse(stringTrace, fingerprintTrace));
                        t.sendResponseHeaders(200, response.length());
                        OutputStream responseStream = t.getResponseBody();
                        responseStream.write(response.getBytes());
                        responseStream.close();
                    } catch (Exception e) {
                        String errorMessage = e.getMessage();
                        t.sendResponseHeaders(500, errorMessage.length());
                        OutputStream response = t.getResponseBody();
                        response.write(errorMessage.getBytes());
                        response.close();
                    }
                }
            });
            httpServer.setExecutor(null);
            System.out.println("Server starts listening on port: "+Integer.toString(serverPort));
            httpServer.start();
        } catch (Exception e) {
            System.out.println("Error running server: "+e.getMessage());
        }
    }
}
