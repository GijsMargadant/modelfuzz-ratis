package org.apache.ratis.interceptor;

import com.squareup.okhttp.*;

import java.io.IOException;

import org.apache.ratis.client.RaftClientRpc;
import org.apache.ratis.client.impl.ClientProtoUtils;
import org.apache.ratis.client.impl.RaftClientRpcWithProxy;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.interceptor.comm.InterceptorMessage;
import org.apache.ratis.netty.NettyRpcProxy;
import org.apache.ratis.netty.client.NettyClientRpc;
import org.apache.ratis.proto.RaftProtos.RaftClientRequestProto;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.protocol.RaftPeerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterceptorClientRpc extends RaftClientRpcWithProxy<InterceptorRpcProxy> {
    public static final Logger LOG = LoggerFactory.getLogger(InterceptorClientRpc.class);
    public  InterceptorClientRpc(ClientId clientId, RaftProperties raftProperties) {
        super(new InterceptorRpcProxy.PeerMap(clientId.toString(), raftProperties));
    }

    @Override
    public RaftClientReply sendRequest(RaftClientRequest request) throws IOException {
        final RaftPeerId serverId = request.getServerId();
        final InterceptorRpcProxy proxy = getProxies().getProxy(serverId);

        String address = proxy.getPeerAddress();
        final RaftClientRequestProto proto = ClientProtoUtils.toRaftClientRequestProto(request);
        InterceptorMessage.Builder builder = new InterceptorMessage.Builder()
                .setRaftClientRequest(proto)
                .setRequestId(request.getClientId().toString());
        InterceptorMessage msg = builder.build();

        OkHttpClient client = new OkHttpClient();

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        Request httpRequest = new Request.Builder()
                .url("http://" + address)
                .post(RequestBody.create(JSON, msg.toJsonString()))
                .build();
        Response response = null;
        InterceptorMessage replyMsg = null;
        try {
            response = client.newCall(httpRequest).execute();
            if (response != null) {
                ResponseBody body = response.body();
                System.out.println("Client response body: " + body.toString());
                replyMsg = new InterceptorMessage.Builder().buildWithJsonString(body.string());
                if (replyMsg != null) {
                    return ClientProtoUtils.toRaftClientReply(replyMsg.toRaftClientReply());
                }
            }
        } catch (IOException e) { }
        //     LOG.error("Error on client sendRequest: ", e);
        // }

        return null;
    }
}
