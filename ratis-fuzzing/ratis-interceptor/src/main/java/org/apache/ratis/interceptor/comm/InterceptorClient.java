package org.apache.ratis.interceptor.comm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.squareup.okhttp.*;

import org.apache.ratis.protocol.exceptions.TimeoutIOException;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.thirdparty.com.google.common.reflect.TypeToken;
import org.apache.ratis.util.IOUtils;
import org.apache.ratis.util.TimeDuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class InterceptorClient {
    // TODO
    //  [ ] Need to initiate a connection pool to the interceptor server
    //  [X] Need to define an message interface to communicate with the interceptor server
    //  [X] Need to figure out how to start the listener server (own the thread? extend the thread interface?)
    //  [X] Need to tag and keep track of requests and implement a future interface
    //  [X] Need to add send event

    @FunctionalInterface
    public static interface MessageHandler {
        InterceptorMessage apply(InterceptorMessage interceptorMessage) throws IOException;
    }

    public static final Logger LOG = LoggerFactory.getLogger(InterceptorClient.class);
    private RaftServer raftServer;
    private InetSocketAddress fuzzerAddress;
    private InetSocketAddress listenAddress;
    private InterceptorServer listenServer;
    private TimeDuration replyWaitTime;
    private OkHttpClient client;
    private MessagePollingThread pollingThread; 
    private AtomicInteger counter;
    private Random random;

    private boolean shutdown;
    // private boolean crash;
    private boolean enableRegister;

    public InterceptorClient(
        RaftServer raftServer, 
        InetSocketAddress fuzzerAddress, 
        InetSocketAddress listenAddress, 
        TimeDuration replyWaitTime,
        MessageHandler messageHandler,
        boolean enableRegister
    ) {
        this.raftServer = raftServer;
        this.fuzzerAddress = fuzzerAddress;
        this.listenAddress = listenAddress;
        this.replyWaitTime = replyWaitTime;

        this.client = new OkHttpClient();
        this.client.setConnectTimeout(60, TimeUnit.SECONDS);

        this.shutdown = false;
        // this.crash = false;
        this.enableRegister = enableRegister;

        try {
            this.listenServer = new InterceptorServer(listenAddress);
        } catch (Exception e) {
            LOG.error("Error on initializing InterceptorServer: ", e);
        }
        this.counter = new AtomicInteger();
        this.random = new Random((long) this.listenAddress.getPort());
        this.pollingThread = new MessagePollingThread(this.listenServer, messageHandler, this);
    }

    public void start() throws IOException {
        try {
            LOG.info("Starting interceptor client");
            this.listenServer.startServer();
            this.pollingThread.start();
            if (this.enableRegister)
                register();
        } catch (Exception e) {
            LOG.error("Error on starting InterceptorServer: ", e);
        }
    }

    public void stop() throws IOException {
        LOG.info("Stopping interceptor client");
        this.pollingThread.interrupt();
        this.listenServer.stopServer();
    }

    public void register() throws IOException {
        LOG.info("Registering! Address: " + this.listenAddress.toString().replace("/", ""));
        JsonObject ob = new JsonObject();
        ob.addProperty("id", this.raftServer.getId().toString());
        ob.addProperty("addr", this.listenAddress.toString().replace("/", ""));

        Gson gson = new GsonBuilder().create();
        String registerString = gson.toJson(ob);

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        Request request = new Request.Builder()
                .url("http://"+this.fuzzerAddress.toString()+"/replica")
                .post(RequestBody.create(JSON, registerString))
                .build();

        Response response = client.newCall(request).execute();
        if (response != null) {
            response.body().close();
        }
    }

    public String getNewRequestId() {
        return this.raftServer.getId().toString() + "_" + Integer.toString(this.counter.getAndIncrement());
    }

    public String getNewMessageId() {
        return this.random.ints(48, 123)
            .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i>= 97))
            .limit(16)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    private void sendMessageToServer(String message) throws IOException {
        LOG.debug("Sending message: "+message);
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        Request request = new Request.Builder()
                .url("http://"+this.fuzzerAddress.toString()+"/message")
                .post(RequestBody.create(JSON, message))
                .build();

        Response response = client.newCall(request).execute();
        if (response != null) {
            response.body().close();
        }
    }

    public InterceptorMessage sendMessage(InterceptorMessage.Builder messageBuilder) throws IOException{
        // TODO:
        //  [X] need to construct a future to wait for a message on
        //  [X] use the message builder to construct a message after assigning message id, from address
        InterceptorMessage message = messageBuilder
            .setID(getNewMessageId())
            .setFrom(raftServer.getId().toString())
            .build();

        String requestId = message.getRequestId();
        CompletableFuture<InterceptorMessage> reply = new CompletableFuture<>();
        this.pollingThread.addPendingRequests(requestId, reply);

        LOG.debug("Sending message from " + message.getFrom() + " to " + message.getTo() + " of type " + message.getType());

        sendMessageToServer(message.toJsonString());

        try {
            return reply.get(this.replyWaitTime.getDuration(), this.replyWaitTime.getUnit());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw IOUtils.toInterruptedIOException("Message sending interrupted", e);
        } catch (ExecutionException e) {
            throw IOUtils.toIOException(e);
        } catch (TimeoutException e) {
            LOG.error("Did not receive a reply for request: "+requestId);
            throw new TimeoutIOException(e.getMessage(), e);
        }
    }

    public void sendEventToServer(String jsonEvent) throws IOException {
        LOG.debug("Sending event: "+jsonEvent);
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        Request request = new Request.Builder()
                .url("http://"+this.fuzzerAddress.toString()+"/event")
                .post(RequestBody.create(JSON, jsonEvent))
                .build();

        Response response = client.newCall(request).execute();
        if (response != null) {
            response.body().close();
        }
    }

    public void sendEvent(HashMap<String, Object> eventParams) throws IOException {
        Gson gson = new GsonBuilder().create();
        Type typeObject = new TypeToken<HashMap>() {}.getType();
        String json = gson.toJson(eventParams, typeObject);

        sendEventToServer(gson.toJson(json));
    }

    public void setShutdown(boolean b) {
        if (b) {
            this.shutdown = true;
            try {
                this.stop();
            } catch (Exception e) {
                LOG.error("Could not stop client: ", e);
            }
        }
    }

    // public void setCrash(boolean b) {
    //     if (b) {
    //         this.crash = true;
    //         try {
    //             this.stop();
    //         } catch (Exception e) {
    //             LOG.error("Could not stop client: ", e);
    //         }
    //     }
    // }

    public boolean getParam(String param) {
        switch (param) {
            case "Shutdown":
                return this.shutdown;
            // case "Crash":
            //     return this.crash;
            default:
                break;
        }
        return false;
    }

    // TODO: 
    //  [X] polling functions that reads the messages
    //  [X] completes the futures that were waiting
    //  [x] calls the handler for the messages that are not pending
    private class MessagePollingThread extends Thread {
        public final Logger LOG = LoggerFactory.getLogger(MessagePollingThread.class);
        private Map<String, CompletableFuture<InterceptorMessage>> pendingRequests;
        private InterceptorServer listenServer;
        private MessageHandler messageHandler;
        private InterceptorClient iClient;

        public MessagePollingThread(InterceptorServer listenServer, MessageHandler messageHandler, InterceptorClient iClient) {
            this.listenServer = listenServer;
            this.pendingRequests = new ConcurrentHashMap<>();
            this.messageHandler = messageHandler;
            this.iClient = iClient;
        }

        public void addPendingRequests(String requestId, CompletableFuture<InterceptorMessage> reply) {
            this.pendingRequests.put(requestId, reply);
        }

        public void pollAndCompleteMessages() {
            while(!this.isInterrupted()) {
                LOG.debug("Polling for messages.");
                List<InterceptorMessage> receivedMessages = this.listenServer.getReceivedMessages();

                if (receivedMessages.size() > 0) {
                    for (InterceptorMessage message : receivedMessages) {
                        try {
                            if (message == null)
                                continue;
                            LOG.debug("Processing new message: "+message.toJsonString());
                            LOG.debug("Processing new message from " + message.getFrom() + " to " + message.getTo() + " of type " + message.getType());
                            String requestID = message.getRequestId();
                            CompletableFuture<InterceptorMessage> messageFuture = pendingRequests.get(requestID);
                            if(messageFuture != null) {
                                // Then this is a reply message to a request that is sent
                                LOG.debug("received a response for id: "+requestID);
                                messageFuture.complete(message);
                                pendingRequests.remove(requestID);
                            } else {
                                if (message.getType().equals("shutdown")) {
                                    this.iClient.setShutdown(true);
                                } 
                                // else if (message.getType().equals("crash")) {
                                //     this.iClient.setCrash(true);
                                // } 
                                else {
                                    // Otherwise its a new request that the process needs to reply to
                                    LOG.debug("handling a new request: "+ requestID);
                                    InterceptorMessage reply = messageHandler.apply(message);
                                    if (reply == null)
                                        continue;
                                    LOG.debug("Sending message from " + reply.getFrom() + " to " + reply.getTo() + " of type " + reply.getType());
                                    iClient.sendMessageToServer(reply.toJsonString());
                                }
                            }
                        } catch (Exception e) {
                            LOG.error("Error processing new message: "+e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                try {
                    Thread.sleep(1);
                } catch (Exception e) {
                    LOG.error("Error while polling: ", e);
                } finally { }
            }
        }

        @Override
        public void run() {
            pollAndCompleteMessages();
        }
    }
}
