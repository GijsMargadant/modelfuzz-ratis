package org.apache.ratis.interceptor.comm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.ratis.interceptor.InterceptorRpcService;
import org.apache.ratis.proto.RaftProtos.*;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.thirdparty.com.codahale.metrics.MetricRegistryListener.Base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class InterceptorMessage {
    public static final Logger LOG = LoggerFactory.getLogger(InterceptorMessage.class);
    private final String from;
    private final String to;
    private final byte[] data;
    private final String type;
    private final String id;
    private final String requestId;

    private Map<String, Object> params;

    private InterceptorMessage(String from, String to, String type, byte[] data, String id) {
        this.from = from;
        this.to = to;
        this.data = data;
        this.type = type;
        this.id = id;
        this.requestId = "";
        this.params = new HashMap<>();
    }

    private InterceptorMessage(String from, String to, String type, byte[] data, String id, String requestId) {
        this.from = from;
        this.to = to;
        this.data = data;
        this.type = type;
        this.id = id;
        this.requestId = requestId;
        this.params = new HashMap<>();
    }

    public void setParam(String key, Object value) {
        this.params.put(key, value);
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public String getFrom() {
        return this.from;
    }

    public String getTo() {
        return this.to;
    }

    public String getType() {
        return this.type;
    }

    // Used to check if the message contains a request that the RPC service needs to poll for a reply
    public boolean needToWaitForReply() {
        return this.requestId != "" && (this.type == "request_vote_request" || this.type == "append_entries_request");
    }

    public String getRequestId() {
        return this.requestId;
    }

    public String toJsonString() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("from", this.from);
            json.addProperty("to", this.to);
            json.addProperty("type", this.type);
            json.addProperty("id", this.id);

            JsonObject dataJson = new JsonObject();
            dataJson.addProperty("data", Base64.getEncoder().encodeToString(this.data));
            dataJson.addProperty("request_id", this.requestId);

            Gson gson = new GsonBuilder().create();
            JsonElement paramsJson = gson.toJsonTree(this.params);

            byte[] dataBytes = gson.toJson(dataJson).getBytes();

            json.addProperty("data", Base64.getEncoder().encodeToString(dataBytes));
            json.add("params", paramsJson);

            return gson.toJson(json);
        } catch (Exception e) {
            LOG.error("Could not convert to Json string: ", e);
        }
        return null;
    }

    public RequestVoteRequestProto toRequestVoteRequest() throws IOException{
        return InterceptorMessageUtils.toRequestVoteRequest(this.data);
    }

    public RequestVoteReplyProto toRequestVoteReply() throws IOException{
        return InterceptorMessageUtils.toRequestVoteReply(this.data);
    }

    public AppendEntriesRequestProto toAppendEntriesRequest() throws IOException{
        return InterceptorMessageUtils.toAppendEntriesRequest(this.data);
    }

    public AppendEntriesReplyProto toAppendEntriesReply() throws IOException{
        return InterceptorMessageUtils.toAppendEntriesReply(this.data);
    }

    public InstallSnapshotRequestProto toInstallSnapshotRequest() throws IOException{
        return InterceptorMessageUtils.toInstallSnapshotRequest(this.data);
    }

    public InstallSnapshotReplyProto toInstallSnapshotReply() throws IOException{
        return InterceptorMessageUtils.toInstallSnapshotReply(this.data);
    }

    public StartLeaderElectionRequestProto toStartLeaderElectionRequest() throws IOException{
        return InterceptorMessageUtils.toStartLeaderElectionRequest(this.data);
    }

    public RaftClientRequestProto toRaftClientRequest() throws IOException{
        return InterceptorMessageUtils.toRaftClientRequest(this.data);
    }

    public RaftClientReplyProto toRaftClientReply() throws IOException{
        return InterceptorMessageUtils.toRaftClientReply(this.data);
    }

    public StartLeaderElectionReplyProto toStartLeaderElectionReply() throws IOException{
        return InterceptorMessageUtils.toStartLeaderElectionReply(this.data);
    }

    public static class Builder {
        public static final Logger LOG = LoggerFactory.getLogger(Builder.class);
        private String from;
        private String id;
        private String requestId;
        private String mType;
        private Map<String, Object> params;

        private RequestVoteRequestProto requestVoteRequest;
        private RequestVoteReplyProto requestVoteReply;
        private AppendEntriesRequestProto appendEntriesRequest;
        private AppendEntriesReplyProto appendEntriesReply;
        private InstallSnapshotRequestProto installSnapshotRequest;
        private InstallSnapshotReplyProto installSnapshotReply;
        private StartLeaderElectionRequestProto startLeaderElectionRequest;
        private StartLeaderElectionReplyProto startLeaderElectionReply;
        private RaftClientRequestProto raftClientRequest;
        private RaftClientReplyProto raftClientReply;


        public Builder() {}

        public Builder setFrom(String from) {
            this.from = from;
            return this;
        }

        public Builder setType(String type) {
            this.mType = type;
            return this;
        }

        public Builder setParams(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        public Builder setID(String id) {
            this.id = id;
            return this;
        }

        public Builder setRequestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder setAppendEntriesRequest(AppendEntriesRequestProto appendEntriesRequest) {
            this.appendEntriesRequest = appendEntriesRequest;
            return this;
        }

        public Builder setAppendEntriesReply(AppendEntriesReplyProto appendEntriesReply) {
            this.appendEntriesReply = appendEntriesReply;
            return this;
        }

        public Builder setRequestVoteRequest(RequestVoteRequestProto requestVoteRequest) {
            this.requestVoteRequest = requestVoteRequest;
            return this;
        }

        public Builder setRequestVoteReply(RequestVoteReplyProto requestVoteReply) {
            this.requestVoteReply = requestVoteReply;
            return this;
        }

        public Builder setInstallSnapshotRequest(InstallSnapshotRequestProto installSnapshotRequest) {
            this.installSnapshotRequest = installSnapshotRequest;
            return this;
        }

        public Builder setInstallSnapshotReply(InstallSnapshotReplyProto installSnapshotReply) {
            this.installSnapshotReply = installSnapshotReply;
            return this;
        }

        public Builder setStartLeaderElectionRequest(StartLeaderElectionRequestProto startLeaderElectionRequest) {
            this.startLeaderElectionRequest = startLeaderElectionRequest;
            return this;
        }

        public Builder setStartLeaderElectionReply(StartLeaderElectionReplyProto startLeaderElectionReply) {
            this.startLeaderElectionReply = startLeaderElectionReply;
            return this;
        }

        public Builder setRaftClientRequest(RaftClientRequestProto raftClientRequest) {
            this.raftClientRequest = raftClientRequest;
            return this;
        }

        public Builder setRaftClientReply(RaftClientReplyProto raftClientReply) {
            this.raftClientReply = raftClientReply;
            return this;
        }

        public InterceptorMessage build() throws IOException {
            byte[] data;
            String to;
            String type = "";
            
            if(this.requestVoteRequest != null) {
                data = InterceptorMessageUtils.fromRequestVoteRequest(this.requestVoteRequest);
                to = this.requestVoteRequest.getServerRequest().getReplyId().toStringUtf8();
                type = InterceptorMessageUtils.MessageType.RequestVoteRequest.toString();
            } else if(this.requestVoteReply != null) {
                data = InterceptorMessageUtils.fromRequestVoteReply(this.requestVoteReply);
                to = this.requestVoteReply.getServerReply().getRequestorId().toStringUtf8();
                type = InterceptorMessageUtils.MessageType.RequestVoteReply.toString();
            } else if(this.appendEntriesRequest != null) {
                data = InterceptorMessageUtils.fromAppendEntriesRequest(this.appendEntriesRequest);
                to = this.appendEntriesRequest.getServerRequest().getReplyId().toStringUtf8();
                type = InterceptorMessageUtils.MessageType.AppendEntriesRequest.toString();
            } else if(this.appendEntriesReply != null) {
                data = InterceptorMessageUtils.fromAppendEntriesReply(this.appendEntriesReply);
                to = this.appendEntriesReply.getServerReply().getRequestorId().toStringUtf8();
                type = InterceptorMessageUtils.MessageType.AppendEntriesReply.toString();
            } else if (this.installSnapshotRequest != null) {
                data = InterceptorMessageUtils.fromInstallSnapshotRequest(this.installSnapshotRequest);
                to = this.installSnapshotRequest.getServerRequest().getReplyId().toStringUtf8();
                type = InterceptorMessageUtils.MessageType.InstallSnapshotRequest.toString();
            } else if (this.installSnapshotReply != null) {
                data = InterceptorMessageUtils.fromInstallSnapshotReply(this.installSnapshotReply);
                to = this.installSnapshotReply.getServerReply().getRequestorId().toStringUtf8();
                type = InterceptorMessageUtils.MessageType.InstallSnapshotReply.toString();
            } else if (this.startLeaderElectionRequest != null) {
                data = InterceptorMessageUtils.fromStartLeaderElectionRequest(this.startLeaderElectionRequest);
                to = this.startLeaderElectionRequest.getServerRequest().getReplyId().toStringUtf8();
                type = InterceptorMessageUtils.MessageType.StartLeaderElectionRequest.toString();
            } else if (this.startLeaderElectionReply != null) {
                data = InterceptorMessageUtils.fromStartLeaderElectionReply(this.startLeaderElectionReply);
                to = this.startLeaderElectionReply.getServerReply().getRequestorId().toStringUtf8();
                type = InterceptorMessageUtils.MessageType.StartLeaderElectionReply.toString();
            } else if (this.raftClientRequest != null) {
                data = InterceptorMessageUtils.fromRaftClientRequest(this.raftClientRequest);
                to = this.raftClientRequest.getRpcRequest().getReplyId().toStringUtf8();
                type = InterceptorMessageUtils.MessageType.RaftClientRequest.toString();
            } else if (this.raftClientReply != null) {
                data = InterceptorMessageUtils.fromRaftClientReply(this.raftClientReply);
                to = this.raftClientReply.getRpcReply().getRequestorId().toStringUtf8();
                type = InterceptorMessageUtils.MessageType.RaftClientReply.toString();
            } else if (this.mType.equals("shutdown") || this.mType.equals("crash")) {
                data = null;
                to = null;
                type = this.mType;
            } else {
                throw new IOException("invalid message type");
            }
            InterceptorMessage message = new InterceptorMessage(this.from, to, type, data, id, requestId);
            if (this.params != null)
                message.setParams(this.params);
            return message;
        }

        public InterceptorMessage buildWithJsonString(String jsonString) {
            if (jsonString == null) {
                LOG.error("Received null string at buildWithJsonString.");
                return null;
            }

            try {
                JsonObject ob = JsonParser.parseString(jsonString).getAsJsonObject();
                if (ob == null) {
                    LOG.error("JsonObject null at buildWithJsonString.");
                    return null;
                }
                InterceptorMessage.Builder builder = new InterceptorMessage.Builder();
                InterceptorMessageUtils.MessageType messageType = InterceptorMessageUtils.MessageType.fromString(ob.get("type").getAsString());
                LOG.debug("messageType: " + messageType);
                JsonObject compositeData = JsonParser.parseString(new String(Base64.getDecoder().decode(ob.get("data").getAsString()), StandardCharsets.ISO_8859_1)).getAsJsonObject();
                byte[] data = Base64.getDecoder().decode(compositeData.get("data").getAsString());
                if(compositeData.has("request_id")){
                    String requestId = compositeData.get("request_id").getAsString();
                    builder.setRequestId(requestId);
                    LOG.debug("Request id: "+requestId);
                }
                switch (messageType) {
                    case RequestVoteRequest:
                        builder.setRequestVoteRequest(InterceptorMessageUtils.toRequestVoteRequest(data));
                        break;
                    case RequestVoteReply:
                        builder.setRequestVoteReply(InterceptorMessageUtils.toRequestVoteReply(data));
                        break;
                    case AppendEntriesRequest:
                        builder.setAppendEntriesRequest(InterceptorMessageUtils.toAppendEntriesRequest(data));
                        break;
                    case AppendEntriesReply:
                        builder.setAppendEntriesReply(InterceptorMessageUtils.toAppendEntriesReply(data));
                        break;
                    case InstallSnapshotRequest:
                        builder.setInstallSnapshotRequest(InterceptorMessageUtils.toInstallSnapshotRequest(data));
                        break;
                    case InstallSnapshotReply:
                        builder.setInstallSnapshotReply(InterceptorMessageUtils.toInstallSnapshotReply(data));
                        break;
                    case StartLeaderElectionRequest:
                        builder.setStartLeaderElectionRequest(InterceptorMessageUtils.toStartLeaderElectionRequest(data));
                        break;
                    case StartLeaderElectionReply:
                        builder.setStartLeaderElectionReply(InterceptorMessageUtils.toStartLeaderElectionReply(data));
                        break;
                    case RaftClientRequest:
                        builder.setRaftClientRequest(InterceptorMessageUtils.toRaftClientRequest(data));
                        break;
                    case RaftClientReply:
                        builder.setRaftClientReply(InterceptorMessageUtils.toRaftClientReply(data));
                        break;
                    case Shutdown:
                        builder.setType("shutdown");
                        break;
                    case Crash:
                        builder.setType("crash");
                        break;
                    default:
                        break;
                }

                InterceptorMessage message = builder.build();
                LOG.debug("Message built.");
                return message;
            } catch (Exception e) {
                LOG.error("Error while building with Json string: ", e);
            }

            return null;
        }
    }
}
