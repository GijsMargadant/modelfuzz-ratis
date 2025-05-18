package org.apache.ratis.interceptor;

import com.squareup.okhttp.*;

import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.interceptor.comm.InterceptorMessage;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.util.PeerProxyMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

// The proxy initiates a communication to each peer and is used to communicate to that peer
// We need the proxy to send messages that are not intercepted
public class InterceptorRpcProxy implements Closeable {
    public static class PeerMap extends PeerProxyMap<InterceptorRpcProxy> {
        private final RaftProperties properties;

        public PeerMap(String name, RaftProperties properties) {
            super(name);
            this.properties = properties;
        }

        @Override
        public InterceptorRpcProxy createProxyImpl(RaftPeer peer)
                throws IOException {
            return new InterceptorRpcProxy(peer, properties);
        }

        @Override
        public void close() {
            super.close();
        }
    }

    public static final Logger LOG = LoggerFactory.getLogger(InterceptorRpcProxy.class);
    private final RaftPeer peer;
    private OkHttpClient client = new OkHttpClient();

    public InterceptorRpcProxy(RaftPeer peer, RaftProperties raftProperties) {
        this.peer = peer;
    }

    @Override
    public void close() {}

    public InterceptorMessage send(InterceptorMessage request) {
        // TODO:
        //  [X] need to figure out the right address to the peer and send a http request to that peer
        String address = getPeerAddress();
        LOG.debug("Peer address: " + address);
        String json = request.toJsonString();

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        Request httpRequest = new Request.Builder()
                .url("http://" + address)
                .post(RequestBody.create(JSON, json))
                .build();
        Response response = null;
        InterceptorMessage msg = null;
        try {
            response = client.newCall(httpRequest).execute();
            if (response.isSuccessful()) {
                if (response != null) {
                    String responseBody = response.body().string();
                    response.body().close();
                    LOG.debug("Constructing message from response: " + responseBody);
                    msg = new InterceptorMessage.Builder().buildWithJsonString(responseBody);
                    if (msg != null) {
                        return msg;
                    }
                }
                LOG.error("Response is null.");
            }   
            LOG.error("Response unsuccesfull.");
        } catch (Exception e) {
            LOG.error("Could not send message: ", e);
        }

        return null;
    }

    public String getPeerAddress() {
        return this.peer.getAddress();
    }
}
