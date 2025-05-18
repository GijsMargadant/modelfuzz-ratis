package org.apache.ratis.interceptor.comm;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.iki.elonen.NanoHTTPD;
public class InterceptorServer extends NanoHTTPD {
        
    // TODO:
    //  1. Need to start a http server and listen to messages
    //  3. Add the necessary stuff from the previous instrumentation to start the server here

    Logger LOG = LoggerFactory.getLogger(InterceptorServer.class);

    private List<InterceptorMessage> receivedMessages;

    public InterceptorServer(InetSocketAddress listenAddress) throws IOException {
        super(listenAddress.getPort());
        this.receivedMessages = new CopyOnWriteArrayList<InterceptorMessage>();
    }

    public void startServer() throws IOException {
        LOG.info("Starting interceptor server!");
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false); 
    }

    public void stopServer() throws IOException {
        stop();
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (session.getMethod() == Method.POST) {
            try {
                LOG.debug("Received a message http request");
                HashMap<String, String> body = new HashMap<>();
                session.parseBody(body);
                String requestBody = body.get("postData");
                requestBody = requestBody.replaceAll("\\\\", "");
                requestBody = requestBody.substring(1, requestBody.length()-1);
                LOG.debug("Received a new message: " + requestBody);
                InterceptorMessage message = (new InterceptorMessage.Builder()).buildWithJsonString(requestBody);
                this.receivedMessages.add(message);
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "200 OK");
            } catch (IOException | ResponseException e) {
                LOG.error("Exception while receiving POST.", e);
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, 
            "The requested resource does not exist");
    }

    public List<InterceptorMessage> getReceivedMessages() {
        List<InterceptorMessage> ret = new CopyOnWriteArrayList<InterceptorMessage>(this.receivedMessages);
        this.receivedMessages.clear();
        return ret;
    }
}
