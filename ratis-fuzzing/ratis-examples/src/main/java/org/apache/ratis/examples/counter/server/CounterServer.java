/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis.examples.counter.server;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.examples.common.Constants;
import org.apache.ratis.examples.counter.CounterCommand;
import org.apache.ratis.interceptor.InterceptorConfigKeys;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.server.RaftServerRpc;
import org.apache.ratis.util.NetUtils;
import org.apache.ratis.util.SizeInBytes;
import org.apache.ratis.util.TimeDuration;

import okhttp3.Interceptor;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Simplest Ratis server, use a simple state machine {@link CounterStateMachine}
 * which maintain a counter across multi server.
 * This server application designed to run several times with different
 * parameters (1,2 or 3). server addresses hard coded in {@link Constants}
 * <p>
 * Run this application three times with three different parameter set-up a
 * ratis cluster which maintain a counter value replicated in each server memory
 */
public final class CounterServer implements Closeable {
  private final RaftServer server;

  public CounterServer(RaftPeer peer, File storageDir, RaftGroup RAFT_GROUP, int fuzzerPort, int interceptorListenerPort, int restart) throws IOException {
    //create a property object
    final RaftProperties properties = new RaftProperties();

    //set the storage directory (different for each peer) in the RaftProperty object
    RaftServerConfigKeys.setStorageDir(properties, Collections.singletonList(storageDir));
    RaftServerConfigKeys.Snapshot.setAutoTriggerEnabled(properties, true);

    //set the read policy to Linearizable Read.
    //the Default policy will route read-only requests to leader and directly query leader statemachine.
    //Linearizable Read allows to route read-only requests to any group member
    //and uses ReadIndex to guarantee strong consistency.
    RaftServerConfigKeys.Read.setOption(properties, RaftServerConfigKeys.Read.Option.LINEARIZABLE);
    //set the linearizable read timeout
    RaftServerConfigKeys.Read.setTimeout(properties, TimeDuration.ONE_SECOND);

    //set the port (different for each peer) in RaftProperty object
    final int port = NetUtils.createSocketAddr(peer.getAddress()).getPort();
    System.out.println("RPC port: " + port);
    InterceptorConfigKeys.Listener.setPort(properties, port);
    InterceptorConfigKeys.Server.setPort(properties, fuzzerPort);
    InterceptorConfigKeys.InterceptorListener.setPort(properties, interceptorListenerPort);
    InterceptorConfigKeys.setEnabled(properties, true);
    InterceptorConfigKeys.setEnableRegister(properties, restart == 0);
    RaftServerConfigKeys.Rpc.setFirstElectionTimeoutMin(properties, TimeDuration.valueOf(5, TimeUnit.SECONDS));
    RaftServerConfigKeys.Rpc.setFirstElectionTimeoutMax(properties, TimeDuration.valueOf(5, TimeUnit.SECONDS));
    RaftServerConfigKeys.Rpc.setTimeoutMin(properties, TimeDuration.valueOf(2, TimeUnit.SECONDS));
    RaftServerConfigKeys.Rpc.setTimeoutMax(properties, TimeDuration.valueOf(2, TimeUnit.SECONDS));
    RaftServerConfigKeys.LeaderElection.setPreVote(properties, false);
    // GrpcConfigKeys.Server.setPort(properties, port);

    //create the counter state machine which holds the counter value
    final CounterStateMachine counterStateMachine = new CounterStateMachine();

    //build the Raft server
    this.server = RaftServer.newBuilder()
        .setGroup(RAFT_GROUP)
        .setProperties(properties)
        .setServerId(peer.getId())
        .setStateMachine(counterStateMachine)
        .build();
  }

  public void start() throws IOException {
    server.start();
  }

  @Override
  public void close() throws IOException {
    server.close();
  }

  public RaftServerRpc getServerRpc() {
    return server.getServerRpc();
  }

  public static void main(String[] args) {
    // java -Dlog4j.configuration=file:ratis-examples/src/main/resources/log4j.properties -cp ratis-examples/target/ratis-examples-2.5.1.jar org.apache.ratis.examples.counter.server.CounterServer 0 7073 6002 3 127.0.0.1:10000,127.0.0.1:10001,127.0.0.1:10002 02511d47-d67c-49a3-9011-abb3109a44c1 0
    try {
      int run_id = Integer.parseInt(args[0]);
      int fuzzerPort = Integer.parseInt(args[1]);
      int interceptorListenerPort = Integer.parseInt(args[2]);
      final int peerIndex = Integer.parseInt(args[3]);

      String[] addresses = args[4].split(",");
      final List<RaftPeer> peers = new ArrayList<>(addresses.length);
      final int priority = 0;
      for (int i = 0; i < addresses.length; i++) {
        peers.add(RaftPeer.newBuilder().setId(Integer.toString(i+1)).setAddress(addresses[i]).setPriority(priority).build());
      }

      final List<RaftPeer> PEERS = Collections.unmodifiableList(peers);
      final UUID GROUP_ID = UUID.fromString(args[5]); // "02511d47-d67c-49a3-9011-abb3109a44c1"
      final RaftGroup RAFT_GROUP = RaftGroup.valueOf(RaftGroupId.valueOf(GROUP_ID), PEERS);

      int restart = Integer.parseInt(args[6]);

      System.setProperty("exp.build.data", "./data");
      // TODO: Bootup system
      startServer(run_id, peerIndex, PEERS, RAFT_GROUP, restart, fuzzerPort, interceptorListenerPort);
      System.exit(0);
    } catch(Throwable e) {
      e.printStackTrace();
      try {
        TimeUnit.MILLISECONDS.sleep(10);
      } catch (InterruptedException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      System.exit(1);
    } 
  }

  private static void startServer(int runId, int peerIndex, List<RaftPeer> PEERS, RaftGroup RAFT_GROUP, int restart, int fuzzerPort, int interceptorListenerPort) throws Exception {
    //get peer and define storage dir
    final RaftPeer currentPeer = PEERS.get(peerIndex-1);
    final File storageDir = new File("./data/" + runId + "/" + currentPeer.getId());
    // //start a counter server
    // try(CounterServer counterServer = new CounterServer(currentPeer, storageDir, RAFT_GROUP, fuzzerPort, interceptorListenerPort, restart)) {
    //   counterServer.start();

    //   // Loop forever
    //   while(true){}
    // }
    
    try(CounterServer counterServer = new CounterServer(currentPeer, storageDir, RAFT_GROUP, fuzzerPort, interceptorListenerPort, restart)) {
      counterServer.start();

      // boolean crashFlag;
      while(!counterServer.getServerRpc().getParam("Shutdown")) {
        // crashFlag = counterServer.getServerRpc().getParam("Crash");
        // if (crashFlag) {
        //   counterServer.close();
        //   break;
        // }
        TimeUnit.MILLISECONDS.sleep(1);
      }

      // if (!counterServer.getServerRpc().getParam("Crash")) {
      HashMap<String, Object> eventParams = new HashMap<>();
      eventParams.put("type", "ShutdownReady");
      counterServer.getServerRpc().sendEvent(eventParams);
      // }
    }
  }
}
