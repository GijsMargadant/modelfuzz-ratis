package org.apache.ratis.interceptor;

import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.util.TimeDuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static org.apache.ratis.conf.ConfUtils.*;

public interface InterceptorConfigKeys {

    String PREFIX="raft.interceptor";

    Logger LOG = LoggerFactory.getLogger(InterceptorConfigKeys.class);
    
    static Consumer<String> getDefaultLog() {
        return LOG::info;
    }

    String ENABLED_KEY = PREFIX + ".intercept";
    boolean ENABLED_DEFAULT = false;

    static boolean enabled(RaftProperties properties) {
        return getBoolean(properties::getBoolean, ENABLED_KEY, ENABLED_DEFAULT, getDefaultLog());
    }

    static void setEnabled(RaftProperties properties, boolean enabled) {
        setBoolean(properties::setBoolean, ENABLED_KEY, enabled);
    }

    String REPLY_WAIT_TIMEOUT_KEY = PREFIX+".reply_wait_timeout";
    TimeDuration REPLY_WAIT_TIMEOUT_DEFAULT = TimeDuration.ONE_MINUTE;

    static TimeDuration replyWaitTimeout(RaftProperties properties) {
        return getTimeDuration(properties.getTimeDuration(REPLY_WAIT_TIMEOUT_DEFAULT.getUnit()), REPLY_WAIT_TIMEOUT_KEY, REPLY_WAIT_TIMEOUT_DEFAULT, getDefaultLog());
    }
    
    static void setReplyWaitTimeout(RaftProperties properties, TimeDuration waitTimeout) {
        setTimeDuration(properties::setTimeDuration, REPLY_WAIT_TIMEOUT_KEY, waitTimeout);
    }

    String ENABLE_REGISTER_KEY = PREFIX + "enable_register";
    boolean ENABLE_REGISTER_DEFAULT = true;

    static boolean enableRegister(RaftProperties properties) {
        return getBoolean(properties::getBoolean, ENABLE_REGISTER_KEY, ENABLE_REGISTER_DEFAULT, getDefaultLog());
    }

    static void setEnableRegister(RaftProperties properties, boolean enableRegister) {
        setBoolean(properties::setBoolean, ENABLE_REGISTER_KEY, enableRegister);
    }

    interface Server {
        Logger LOG = LoggerFactory.getLogger(Server.class);

        static Consumer<String> getDefaultLog() {
            return LOG::info;
        }

        String PREFIX = InterceptorConfigKeys.PREFIX + ".server";
        String HOST_KEY = PREFIX + ".host";
        String HOST_DEFAULT = "127.0.0.1";

        String PORT_KEY = PREFIX + ".port";
        int PORT_DEFAULT = 7074;

        static String host(RaftProperties properties) {
            return get(properties::get, HOST_KEY, HOST_DEFAULT, getDefaultLog());
        }

        static void setHost(RaftProperties properties, String host) {
            set(properties::set, HOST_KEY, host);
        }

        static int port(RaftProperties properties) {
            return getInt(properties::getInt,
                    PORT_KEY, PORT_DEFAULT, getDefaultLog(), requireMin(0), requireMax(65536));
        }

        static void setPort(RaftProperties properties, int port) {
            setInt(properties::setInt, PORT_KEY, port);
        }
    }

    interface InterceptorListener {
        Logger LOG = LoggerFactory.getLogger(InterceptorListener.class);

        static Consumer<String> getDefaultLog() {
            return LOG::info;
        }

        String PREFIX = InterceptorConfigKeys.PREFIX + ".ilistener";
        String HOST_KEY = PREFIX + ".host";
        String HOST_DEFAULT = "127.0.0.1";

        String PORT_KEY = PREFIX + ".port";
        int PORT_DEFAULT = 2023;

        static String host(RaftProperties properties) {
            return get(properties::get, HOST_KEY, HOST_DEFAULT, getDefaultLog());
        }

        static void setHost(RaftProperties properties, String host) {
            set(properties::set, HOST_KEY, host);
        }

        static int port(RaftProperties properties) {
            return getInt(properties::getInt,
                    PORT_KEY, PORT_DEFAULT, getDefaultLog(), requireMin(0), requireMax(65536));
        }

        static void setPort(RaftProperties properties, int port) {
            setInt(properties::setInt, PORT_KEY, port);
        }
    }

    interface Listener {
        Logger LOG = LoggerFactory.getLogger(Listener.class);

        static Consumer<String> getDefaultLog() {
            return LOG::info;
        }

        String PREFIX = InterceptorConfigKeys.PREFIX + ".listener";
        String HOST_KEY = PREFIX + ".host";
        String HOST_DEFAULT = "127.0.0.1";

        String PORT_KEY = PREFIX + ".port";
        int PORT_DEFAULT = 3023;

        static String host(RaftProperties properties) {
            return get(properties::get, HOST_KEY, HOST_DEFAULT, getDefaultLog());
        }

        static void setHost(RaftProperties properties, String host) {
            set(properties::set, HOST_KEY, host);
        }

        static int port(RaftProperties properties) {
            return getInt(properties::getInt,
                    PORT_KEY, PORT_DEFAULT, getDefaultLog(), requireMin(0), requireMax(65536));
        }

        static void setPort(RaftProperties properties, int port) {
            setInt(properties::setInt, PORT_KEY, port);
        }
    }

    static void main(String[] args) {
        printAll(InterceptorConfigKeys.class);
    }
}
