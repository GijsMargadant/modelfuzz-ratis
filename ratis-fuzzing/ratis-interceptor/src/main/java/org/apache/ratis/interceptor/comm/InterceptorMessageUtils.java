package org.apache.ratis.interceptor.comm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.ratis.proto.RaftProtos.*;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.thirdparty.com.codahale.metrics.MetricRegistryListener.Base;
import org.apache.ratis.thirdparty.com.google.gson.JsonSerializer;
import org.apache.ratis.thirdparty.com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterceptorMessageUtils {

    public static final Logger LOG = LoggerFactory.getLogger(InterceptorMessageUtils.class);

    public static enum MessageType {
        RequestVoteRequest("request_vote_request"),
        RequestVoteReply("request_vote_reply"),
        AppendEntriesRequest("append_entries_request"),
        AppendEntriesReply("append_entries_reply"),
        InstallSnapshotRequest("install_snapshot_request"),
        InstallSnapshotReply("install_snapshot_reply"),
        StartLeaderElectionRequest("start_leader_election_request"),
        StartLeaderElectionReply("start_leader_election_reply"),
        RaftClientRequest("raft_client_request"),
        RaftClientReply("raft_client_reply"),
        Shutdown("shutdown"),
        Crash("crash"),
        None("");

        private String type;
        private MessageType(String type) {
            this.type = type;
        }

        public String toString() {
            return this.type;
        }

        public static MessageType fromString(String type) {
            switch (type) {
                case "request_vote_request":
                    return RequestVoteRequest;
                case "request_vote_reply":
                    return RequestVoteReply;
                case "append_entries_request":
                    return AppendEntriesRequest;
                case "append_entries_reply":
                    return AppendEntriesReply;
                case "install_snapshot_request":
                    return InstallSnapshotRequest;
                case "install_snapshot_reply":
                    return InstallSnapshotReply;
                case "start_leader_election_request":
                    return StartLeaderElectionRequest; 
                case "start_leader_election_reply":
                    return StartLeaderElectionReply;
                case "raft_client_request":
                    return RaftClientRequest;
                case "raft_client_reply":
                    return RaftClientReply;
                case "shutdown":
                    return Shutdown;
                case "crash":
                    return Crash;
                default:
                    return None;
            }
        }
    }


    public static byte[] fromRequestVoteRequest(RequestVoteRequestProto request) throws IOException{
        String out = JsonFormat.printer().print(request);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] fromRequestVoteReply(RequestVoteReplyProto reply) throws IOException{
        String out = JsonFormat.printer().print(reply);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] fromAppendEntriesRequest(AppendEntriesRequestProto request) throws IOException{
        String out = JsonFormat.printer().print(request);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] fromAppendEntriesReply(AppendEntriesReplyProto reply) throws IOException {
        String out = JsonFormat.printer().print(reply);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] fromInstallSnapshotRequest(InstallSnapshotRequestProto request) throws IOException{
        String out = JsonFormat.printer().print(request);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] fromInstallSnapshotReply(InstallSnapshotReplyProto reply) throws IOException {
        String out = JsonFormat.printer().print(reply);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] fromStartLeaderElectionRequest(StartLeaderElectionRequestProto request) throws IOException{
        String out = JsonFormat.printer().print(request);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] fromStartLeaderElectionReply(StartLeaderElectionReplyProto reply) throws IOException {
        String out = JsonFormat.printer().print(reply);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] fromRaftClientRequest(RaftClientRequestProto request) throws IOException{
        String out = JsonFormat.printer().print(request);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] fromRaftClientReply(RaftClientReplyProto reply) throws IOException {
        String out = JsonFormat.printer().print(reply);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public static RequestVoteRequestProto toRequestVoteRequest(byte[] data) throws IOException{
        try {
            String jsonData = new String(data, StandardCharsets.UTF_8);

            RequestVoteRequestProto.Builder builder = RequestVoteRequestProto.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(jsonData, builder);

            return builder.build();
        } catch (Exception e) {
            LOG.error("Error on toRequestVoteRequest: ", e);
        }
        return null;
    }

    public static RequestVoteReplyProto toRequestVoteReply(byte[] data) throws IOException{
        try {
            String jsonData = new String(data, StandardCharsets.UTF_8);

            RequestVoteReplyProto.Builder builder = RequestVoteReplyProto.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(jsonData, builder);

            return builder.build();
        } catch (Exception e) {
            LOG.error("Error on toRequestVoteReply", e);
        }

        return null;
    }

    public static AppendEntriesRequestProto toAppendEntriesRequest(byte[] data) throws IOException{
        String jsonData = new String(data, StandardCharsets.UTF_8);

        AppendEntriesRequestProto.Builder builder = AppendEntriesRequestProto.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(jsonData, builder);

        return builder.build();
    }

    public static AppendEntriesReplyProto toAppendEntriesReply(byte[] data) throws IOException{
        String jsonData = new String(data, StandardCharsets.UTF_8);

        AppendEntriesReplyProto.Builder builder = AppendEntriesReplyProto.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(jsonData, builder);

        return builder.build();
    }

    public static InstallSnapshotRequestProto toInstallSnapshotRequest(byte[] data) throws IOException{
        String jsonData = new String(data, StandardCharsets.UTF_8);

        InstallSnapshotRequestProto.Builder builder = InstallSnapshotRequestProto.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(jsonData, builder);

        return builder.build();
    }

    public static InstallSnapshotReplyProto toInstallSnapshotReply(byte[] data) throws IOException{
        String jsonData = new String(data, StandardCharsets.UTF_8);

        InstallSnapshotReplyProto.Builder builder = InstallSnapshotReplyProto.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(jsonData, builder);

        return builder.build();
    }

    public static StartLeaderElectionRequestProto toStartLeaderElectionRequest(byte[] data) throws IOException{
        String jsonData = new String(data, StandardCharsets.UTF_8);

        StartLeaderElectionRequestProto.Builder builder = StartLeaderElectionRequestProto.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(jsonData, builder);

        return builder.build();
    }

    public static StartLeaderElectionReplyProto toStartLeaderElectionReply(byte[] data) throws IOException{
        String jsonData = new String(data, StandardCharsets.UTF_8);

        StartLeaderElectionReplyProto.Builder builder = StartLeaderElectionReplyProto.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(jsonData, builder);

        return builder.build();
    }

    public static RaftClientRequestProto toRaftClientRequest(byte[] data) throws IOException{
        String jsonData = new String(data, StandardCharsets.UTF_8);

        RaftClientRequestProto.Builder builder = RaftClientRequestProto.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(jsonData, builder);

        return builder.build();
    }

    public static RaftClientReplyProto toRaftClientReply(byte[] data) throws IOException{
        String jsonData = new String(data, StandardCharsets.UTF_8);

        RaftClientReplyProto.Builder builder = RaftClientReplyProto.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(jsonData, builder);

        return builder.build();
    }
}
