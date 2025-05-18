package tlc2.controlled;

import tlc2.controlled.protocol.ActionMapper;
import tlc2.controlled.protocol.ActionWrapper;
import tlc2.tool.Action;
import tlc2.tool.TLCState;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class RemoteController extends ActionController {

    private final Thread serverThread;
    private final MyServer server;
    private final BlockingQueue<String> actionQueue;
    private final BlockingQueue<String> stateQueue;

    public RemoteController(ActionMapper mapper, Action[] actions) {
        super(mapper, actions);
        actionQueue = new ArrayBlockingQueue<String>(1);
        stateQueue = new ArrayBlockingQueue<String>(1);
        server = new MyServer(actionQueue, stateQueue, "q");
        serverThread = new Thread(server);
        serverThread.start();
    }

    @Override
    public List<ActionWrapper> getNextActions() {
        List<ActionWrapper> nextActions = new ArrayList<>();

        try {
            String inputStr = actionQueue.take();
            System.out.println("Received: " + inputStr);
            nextActions.addAll(mapper.mapListOfActions(inputStr));
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Unknown action");
        }

        return nextActions;
    }

    @Override
    public void setVisitedStates(List<TLCState> state) {
        try {
            String message = "";
            if(state == null) {
                message = "No next state for that action. Choose again.\n";
            } else {
                message = state.toString() + "\n";
            }

            stateQueue.put(message);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean isServerThreadAlive() {
        return serverThread.isAlive();
    }

    private class MyServer implements Runnable {

        private final BlockingQueue<String> actionQueue;
        private final BlockingQueue<String> stateQueue;
        private final String escapeStr;

        public MyServer(BlockingQueue<String> actionQueue, BlockingQueue<String> stateQueue, String escapeStr) {
            this.actionQueue = actionQueue;
            this.stateQueue = stateQueue;
            this.escapeStr = escapeStr;
        }

        @Override
        public void run() {
            System.out.println("Server starts listening");
            try {
                ServerSocket ss =new ServerSocket(2023);
                Socket socket = ss.accept();
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(input));

                String actionStr = "";
                String stateStr = "";
                while(!actionStr.equalsIgnoreCase(escapeStr)) {
                    // send the current state tp the remote process
                    stateStr = stateQueue.take();
                    output.write(stateStr.getBytes(StandardCharsets.UTF_8));

                    // get the next action from the remote process
                    actionStr = reader.readLine();  // reads a single character
                    actionQueue.add(actionStr);
                }

                actionQueue.add(escapeStr);
                System.out.println("Stopping server");
                ss.close();
            } catch(Exception e) {
                System.out.println(e.getMessage());
            }
        }


    }
}
