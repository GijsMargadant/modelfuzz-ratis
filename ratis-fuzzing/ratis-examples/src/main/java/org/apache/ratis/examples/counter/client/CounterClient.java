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
package org.apache.ratis.examples.counter.client;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.examples.common.Constants;
import org.apache.ratis.examples.counter.CounterCommand;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Counter client application, this application sends specific number of
 * INCREMENT command to the Counter cluster and at the end sends a GET command
 * and print the result
 * <p>
 * Parameter to this application indicate the number of INCREMENT command, if no
 * parameter found, application use default value which is 10
 */
public final class CounterClient implements Closeable {
  //build the client
  private final RaftClient client;
  
  public CounterClient(RaftGroup RAFT_GROUP) {
    this.client = RaftClient.newBuilder()
      .setProperties(new RaftProperties())
      .setRaftGroup(RAFT_GROUP)
      .build();
  }

  @Override
  public void close() throws IOException {
    client.close();
  }

  private void writeToElle(String elleFile, String elleVal) {
    try {
      File file = new File(elleFile);
      if (!file.exists())
        file.createNewFile();
      FileWriter fileWriter = new FileWriter(file, true); 
      PrintWriter printWriter = new PrintWriter(fileWriter);
      printWriter.print(elleVal);
      printWriter.close();
      fileWriter.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void run(int request) throws Exception {
    System.out.printf("Sending %s command", CounterCommand.INCREMENT);
    // final List<Future<RaftClientReply>> futures = new ArrayList<>();

    //send INCREMENT command(s)
    // use BlockingApi

    final RaftClientReply reply = client.io().send(CounterCommand.INCREMENT.getMessage());
    if (reply.isSuccess()) {
        final String count = reply.getMessage().getContent().toStringUtf8();
        System.out.println("Counter is incremented to " + count);
        // elleVal =  "{:type :ok, :f :add, :value 1, :op-index " + count + ", :process " + client.getId().toString() + ", :time " + Instant.now().toEpochMilli() + ", :index " + ((2*request)-1) + "}\n";
        // writeToElle(elleFile, elleVal);
      } else {
        System.err.println("Failed " + reply);
        throw new Exception("Failed client request");
      }
    // futures.add(fut);
    // String elleVal = "{:type :invoke, :f :add, :value 1, :op-index " + request + ", :process " + client.getId().toString() + ", :time " + Instant.now().toEpochMilli() + ", :index " + ((2*request)-2) + "}\n";

    // writeToElle(elleFile, elleVal);    

    //wait for the futures
    // for (Future<RaftClientReply> f : futures) {
    //   final RaftClientReply reply = f.get();
    //   if (reply.isSuccess()) {
    //     final String count = reply.getMessage().getContent().toStringUtf8();
    //     System.out.println("Counter is incremented to " + count);
    //     // elleVal =  "{:type :ok, :f :add, :value 1, :op-index " + count + ", :process " + client.getId().toString() + ", :time " + Instant.now().toEpochMilli() + ", :index " + ((2*request)-1) + "}\n";
    //     // writeToElle(elleFile, elleVal);
    //   } else {
    //     System.err.println("Failed " + reply);
    //     throw new Exception("Failed client request");
    //   }
    // }
    // futures.clear();
    client.close();

    // for (Future<RaftClientReply> f : futures) {
    //   f.get();
    // }
  }

  public static void main(String[] args) {
    // java -Dlog4j.configuration=file:../ratis-examples/src/main/resources/log4j.properties -cp ratis-examples/target/ratis-examples-2.5.1.jar org.apache.ratis.examples.counter.client.CounterClient 1 127.0.0.1:10000,127.0.0.1:10001,127.0.0.1:10002 02511d47-d67c-49a3-9011-abb3109a44c1
    int request = Integer.parseInt(args[0]);

    String[] addresses = args[1].split(",");
    final List<RaftPeer> peers = new ArrayList<>(addresses.length);
    final int priority = 0;
    for (int i = 0; i < addresses.length; i++) {
      peers.add(RaftPeer.newBuilder().setId(Integer.toString(i+1)).setAddress(addresses[i]).setPriority(priority).build());
    }
    final List<RaftPeer> PEERS = Collections.unmodifiableList(peers);
    final UUID GROUP_ID = UUID.fromString(args[2]); // "02511d47-d67c-49a3-9011-abb3109a44c1"
    final RaftGroup RAFT_GROUP = RaftGroup.valueOf(RaftGroupId.valueOf(GROUP_ID), PEERS);
    
    try(CounterClient client = new CounterClient(RAFT_GROUP)) {
      client.run(request);
      System.exit(0);
    } catch (Throwable e) {
      e.printStackTrace();
      System.err.println();
      System.err.println("args = " + Arrays.toString(args));
      System.err.println();
      System.err.println("Usage: java org.apache.ratis.examples.counter.client.CounterClient [increment] [async|io]");
      System.err.println();
      System.err.println("       increment: the number of INCREMENT commands to be sent (default is 10)");
      System.err.println("       async    : use the AsyncApi (default)");
      System.err.println("       io       : use the BlockingApi");
      System.exit(1);
    }
  }
}
