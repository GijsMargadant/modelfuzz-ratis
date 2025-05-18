// package org.apache.ratis.server.fuzzer;

// import java.nio.charset.StandardCharsets;
// import java.util.ArrayList;
// import java.util.HashSet;
// import java.util.Vector;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.CopyOnWriteArrayList;
// import java.util.concurrent.TimeUnit;
// import java.util.concurrent.atomic.AtomicInteger;
// import java.util.concurrent.locks.ReentrantLock;

// import org.apache.ratis.server.fuzzer.comm.FuzzerCaller;
// import org.apache.ratis.server.fuzzer.comm.GsonHelper;
// import org.apache.ratis.server.fuzzer.comm.JsonMessage;
// import org.apache.ratis.server.fuzzer.comm.MessageHandler;
// import org.apache.ratis.server.fuzzer.comm.NettyRouter;
// import org.apache.ratis.server.fuzzer.comm.NettyServer;
// import org.apache.ratis.server.fuzzer.comm.Route;
// import org.apache.ratis.server.fuzzer.events.Event;
// import org.apache.ratis.server.fuzzer.events.ShutdownReadyEvent;
// import org.apache.ratis.server.fuzzer.messages.Message;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import com.google.gson.Gson;
// import com.google.gson.JsonObject;

// import io.netty.handler.codec.http.*;
// import io.netty.bootstrap.ServerBootstrap;
// import io.netty.channel.*;
// import io.netty.channel.nio.NioEventLoopGroup;
// import io.netty.channel.socket.SocketChannel;
// import io.netty.channel.socket.nio.NioServerSocketChannel;
// import io.netty.handler.logging.LogLevel;
// import io.netty.handler.logging.LoggingHandler;

// public class FuzzerClient extends Thread{

//     Logger LOG = LoggerFactory.getLogger(FuzzerClient.class);

//     private final String fuzzerAddress = "127.0.0.1";
//     private int fuzzerPort;
//     private final String serverClientAddress = "127.0.0.1";
//     private int serverClientPort;
//     public static int portOffset;

//     private EventLoopGroup bossGroup;
//     private EventLoopGroup workerGroup;

//     private FuzzerCaller client;
//     private ReentrantLock clientLock;
//     private NettyServer server;
//     private Channel serverChannel;
//     private MessageHandler messageHandler;

//     private ConcurrentHashMap<String, Message> messageMap;
//     AtomicInteger msgIdCounter;

//     private boolean crashFlag;
//     private boolean shutdownFlag;
//     private boolean electionFlag;
//     public boolean controlled;

//     public FuzzerClient() {
//         this.fuzzerPort = 7074;
//         this.client = new FuzzerCaller(fuzzerAddress + ":" + Integer.toString(fuzzerPort));
//         this.clientLock = new ReentrantLock(true);
//         this.messageHandler = new MessageHandler();
//         this.messageMap = new ConcurrentHashMap<String, Message>();
//         this.msgIdCounter = new AtomicInteger(0);
//         this.shutdownFlag = false;
//         this.electionFlag = false;
//         this.controlled = true;

//         this.bossGroup = new NioEventLoopGroup();
//         this.workerGroup = new NioEventLoopGroup();
//     }

//     private static class SingletonClient {
//         private static final FuzzerClient INSTANCE = new FuzzerClient();
//     }

//     public static FuzzerClient getInstance() {
//         return SingletonClient.INSTANCE;
//     }

//     public void initServer() {
//         NettyRouter router = new NettyRouter();

//         Route messagesRoute = new Route("/message");
//         messagesRoute.post(messageHandler);
//         router.addRoute(messagesRoute);

//         Route replicasRoute = new Route("/replica");
//         replicasRoute.post(messageHandler);
//         router.addRoute(replicasRoute);

//         Route eventsRoute = new Route("/event");
//         eventsRoute.post(messageHandler);
//         router.addRoute(eventsRoute);

//         this.server = new NettyServer(router);
//         SingletonClient.INSTANCE.start();
//     }

//     public void close() throws InterruptedException {
//         this.serverChannel.close();
//         this.workerGroup.shutdownGracefully();
//         this.bossGroup.shutdownGracefully();
//     }

//     @Override
//     public void run() {
//         try {
//             ServerBootstrap b = new ServerBootstrap();
//             b.group(this.bossGroup, this.workerGroup)
//                 .channel(NioServerSocketChannel.class)
//                 .handler(new LoggingHandler(LogLevel.INFO))
//                 .childHandler(new ChannelInitializer<SocketChannel>() {
//                     @Override
//                     public void initChannel(SocketChannel ch) throws Exception {
//                         ChannelPipeline p = ch.pipeline();
//                         p.addLast(new HttpRequestDecoder());
//                         p.addLast(new HttpObjectAggregator(1048576));
//                         p.addLast(new HttpResponseEncoder());
//                         p.addLast(server);
//                     }
//                 })
//                 .childOption(ChannelOption.SO_KEEPALIVE, true);
//             this.serverChannel = b.bind(serverClientAddress, serverClientPort).sync().channel();
//             this.serverChannel.closeFuture().sync();
//         } catch (Exception e) {
//             System.out.println("-------- EXCEPTION --------");
//             e.printStackTrace();
//         } finally {
//             workerGroup.shutdownGracefully();
//             bossGroup.shutdownGracefully();
//         }
//     }

//     public void registerServer(String id) {
//         JsonObject json = new JsonObject();
//         json.addProperty("id", id);
//         json.addProperty("ready", true);
//         json.addProperty("addr", serverClientAddress + ":" + Integer.toString(serverClientPort));
//         json.addProperty("info", "");

//         Gson gson = GsonHelper.gson;
//         try {
//             this.clientLock.lock();
//             client.sendReplica(gson.toJson(json));
//         } catch (Exception e) {
//             e.printStackTrace();
//         } finally {
//             this.clientLock.unlock();
//         }
//     }

//     public String generateId() {
//         return Integer.valueOf(this.msgIdCounter.getAndIncrement()).toString();
//     }

//     public void interceptMessage(Message m) {
//         try {
//             this.clientLock.lock();
//             client.sendMessage(m.toJsonString());
//         } catch (Exception e) {
//             // e.printStackTrace();
//         } finally {
//             this.clientLock.unlock();
//         }
//         messageMap.put(m.getId(), m);
//     }

//     public void sendEvent(Event e) {
//         if (this.shutdownFlag)
//             return;
//         try {
//             this.clientLock.lock();
//             client.sendEvent(e.toJsonString());
//         } catch (Exception ignored) {} 
//         finally {
//             this.clientLock.unlock();
//         }
//     }

//     public void sendShutdownReadyEvent() {
//         ShutdownReadyEvent e = new ShutdownReadyEvent();
//         try {
//             this.clientLock.lock();
//             client.sendEvent(e.toJsonString());
//         } catch (Exception ignored) {} 
//         finally {
//             this.clientLock.unlock();
//         }
//     }

//     public void getAndExecuteMessages(){
//         if (this.shutdownFlag) 
//             return;
//         Vector<JsonMessage> messages = this.messageHandler.getMessages();
//         for(JsonMessage msg : messages) {
//             if (msg.id == null) {
//                 System.out.println("Null message id.");
//                 continue;
//             }
//             Message msgImpl = this.messageMap.remove(msg.id);
//             if (msgImpl != null)
//                 try {
//                     msgImpl.invoke();
//                 } catch (Exception e) {
//                     System.out.println("Error on message invoke: " + msgImpl.getType());
//                     e.printStackTrace();
//                 }
//         }
//     }

//     public void startShutdown() {
//         this.shutdownFlag = true;
//     }

//     public void setElection(boolean b) {
//         this.electionFlag = b;
//     }

//     public boolean getElection() {
//         return this.electionFlag;
//     }

//     public void addCrash(String id) {
//         this.crashFlag = true;
//     }

//     public boolean getCrash() {
//         return this.crashFlag;
//     }

//     public boolean shouldShutdown() {
//         return this.shutdownFlag;
//     }


//     public void setServerClientPort(int port) {
//         this.serverClientPort = port;
//     }

//     public void setFuzzerPort(int port) {
//         this.fuzzerPort = port;
//         this.client.updateFuzzerAddress(this.fuzzerAddress + ":" + Integer.toString(fuzzerPort));
//     }
    
//     public void clearMessageQueue() {
//         this.messageMap.clear();
//         this.messageHandler.clearMessages();
//     }

//     public boolean isControlledExecution() {
//         return controlled;
//     }
// }