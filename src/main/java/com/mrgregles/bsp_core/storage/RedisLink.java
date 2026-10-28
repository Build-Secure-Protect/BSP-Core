package com.mrgregles.bsp_core.storage;

import com.mrgregles.bsp_core.BSPCore;

import javax.annotation.Nullable;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A small Redis client of our own, speaking the RESP protocol over two sockets: one that publishes
 * (and answers PING), one that stays subscribed. It knows only what the network needs: AUTH,
 * SELECT, PING, PUBLISH, SUBSCRIBE. No library is bundled, so nothing fights the hybrid server's
 * class loaders. Both sockets reconnect with a backoff when they drop; while Redis is away the
 * database polling carries on as before, so Redis only makes the network quicker, never a requirement.
 *
 * <p>Messages are plain strings {@code <kind>:<origin server id>:<payload>}; a server ignores its own.
 */
public final class RedisLink implements AutoCloseable {
    private final String host, password, channel, serverId;
    private final int port, db;
    private final boolean ssl;
    private final Consumer<String[]> onMessage;
    private volatile boolean closed;
    private Socket pub;
    private InputStream pubIn;
    private OutputStream pubOut;
    private Thread subThread;
    private volatile Socket sub;
    private volatile String lastError = "";
    private volatile boolean subscribed;

    public RedisLink(String host, int port, String password, boolean ssl, int db, String channel, String serverId, Consumer<String[]> onMessage) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.ssl = ssl;
        this.db = db;
        this.channel = channel;
        this.serverId = serverId;
        this.onMessage = onMessage;
    }

    public String describe() {
        return host + ":" + port + " channel \"" + channel + "\"" + (subscribed ? ", listening" : lastError.isEmpty() ? ", connecting" : ", down: " + lastError);
    }

    public boolean listening() {
        return subscribed;
    }

    // ------------------------------------------------------------------ RESP

    /** Encodes a command as a RESP array of bulk strings. */
    public static byte[] encode(String... parts) {
        StringBuilder sb = new StringBuilder("*").append(parts.length).append("\r\n");
        for (String p : parts) {
            byte[] b = p.getBytes(StandardCharsets.UTF_8);
            sb.append('$').append(b.length).append("\r\n").append(new String(b, StandardCharsets.UTF_8)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Reads one RESP value: a String for simple strings, bulk strings and integers; a List for arrays; null for a null bulk; throws on an error reply. */
    @Nullable
    public static Object decode(InputStream in) throws IOException {
        int type = in.read();
        if (type < 0) {
            throw new IOException("connection closed");
        }
        String line = readLine(in);
        switch (type) {
            case '+':
                return line;
            case ':':
                return line;
            case '-':
                throw new IOException("Redis error: " + line);
            case '$': {
                int n = Integer.parseInt(line);
                if (n < 0) {
                    return null;
                }
                byte[] buf = new byte[n];
                int read = 0;
                while (read < n) {
                    int r = in.read(buf, read, n - read);
                    if (r < 0) {
                        throw new IOException("connection closed");
                    }
                    read += r;
                }
                readLine(in);
                return new String(buf, StandardCharsets.UTF_8);
            }
            case '*': {
                int n = Integer.parseInt(line);
                if (n < 0) {
                    return null;
                }
                List<Object> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    out.add(decode(in));
                }
                return out;
            }
            default:
                throw new IOException("unexpected RESP type " + (char) type);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\r') {
                in.read(); // \n
                return sb.toString();
            }
            sb.append((char) c);
        }
        throw new IOException("connection closed");
    }

    /** Splits a message into kind, origin and payload; null if it is not ours or came from this server. */
    @Nullable
    public static String[] parse(String message, String self) {
        String[] p = message.split(":", 3);
        if (p.length < 3 || p[1].equals(self)) {
            return null;
        }
        return p;
    }

    // ------------------------------------------------------------------ sockets

    /** The publishing connection waits at most this long for an answer; a half-open connection fails instead of blocking for good. */
    static final int REPLY_TIMEOUT_MS = 5000;
    /** The listening connection: after this long with nothing heard it sends PING; a second silence means the connection is dead. */
    static final int LISTEN_TIMEOUT_MS = 45_000;

    private Socket open() throws IOException {
        return open(REPLY_TIMEOUT_MS);
    }

    private Socket open(int readTimeoutMs) throws IOException {
        SocketFactory f = ssl ? SSLSocketFactory.getDefault() : SocketFactory.getDefault();
        Socket s = f.createSocket();
        s.connect(new InetSocketAddress(host, port), 4000);
        s.setSoTimeout(REPLY_TIMEOUT_MS); // AUTH and SELECT answer at once
        s.setKeepAlive(true);
        InputStream in = new BufferedInputStream(s.getInputStream());
        OutputStream out = s.getOutputStream();
        if (!password.isEmpty()) {
            out.write(encode("AUTH", password));
            out.flush();
            decode(in);
        }
        if (db != 0) {
            out.write(encode("SELECT", String.valueOf(db)));
            out.flush();
            decode(in);
        }
        s.setSoTimeout(readTimeoutMs);
        return s;
    }

    /** Sends PING on a fresh connection and returns what came back; throws when Redis cannot be reached. */
    public String ping() throws IOException {
        try (Socket s = open()) {
            InputStream in = new BufferedInputStream(s.getInputStream());
            s.getOutputStream().write(encode("PING"));
            s.getOutputStream().flush();
            return String.valueOf(decode(in));
        }
    }

    /** Publishes {@code kind:self:payload}. Blocks briefly; call from the storage thread, never the server thread. */
    public synchronized void publish(String kind, String payload) {
        if (!closed && !publishTo(channel, kind + ":" + serverId + ":" + payload)) {
            BSPCore.LOGGER.warn("BSP-Core redis: could not publish ({}); the database polling covers it", lastError);
        }
    }

    /** Publishes {@code message} as it is on {@code toChannel}. False when Redis could not be reached or the link is closed. Storage thread only. */
    public synchronized boolean publishTo(String toChannel, String message) {
        if (closed) {
            return false;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (pub == null || pub.isClosed()) {
                    pub = open();
                    pubIn = new BufferedInputStream(pub.getInputStream());
                    pubOut = pub.getOutputStream();
                }
                pubOut.write(encode("PUBLISH", toChannel, message));
                pubOut.flush();
                decode(pubIn);
                return true;
            } catch (IOException e) {
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                closeQuietly(pub);
                pub = null;
            }
        }
        return false;
    }

    /** Runs one command on the publishing connection and returns Redis's answer. Storage thread only. */
    public synchronized Object call(String... args) throws IOException {
        if (closed) {
            throw new IOException("closed");
        }
        IOException failed = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (pub == null || pub.isClosed()) {
                    pub = open();
                    pubIn = new BufferedInputStream(pub.getInputStream());
                    pubOut = pub.getOutputStream();
                }
                pubOut.write(encode(args));
                pubOut.flush();
                return decode(pubIn);
            } catch (IOException e) {
                if (String.valueOf(e.getMessage()).startsWith("Redis error")) {
                    throw e; // Redis answered: the connection is fine, the command was not
                }
                failed = e;
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                closeQuietly(pub);
                pub = null;
            }
        }
        throw failed;
    }

    public String lastError() {
        return lastError;
    }

    /** Starts the subscriber thread. */
    public void start() {
        subThread = new Thread(this::subscribeLoop, "BSP-Core redis");
        subThread.setDaemon(true);
        subThread.start();
    }

    private void subscribeLoop() {
        long backoff = 2000;
        while (!closed) {
            try {
                Socket s = open(LISTEN_TIMEOUT_MS);
                sub = s;
                InputStream in = new BufferedInputStream(s.getInputStream());
                s.getOutputStream().write(encode("SUBSCRIBE", channel));
                s.getOutputStream().flush();
                decode(in); // the subscribe confirmation
                subscribed = true;
                lastError = "";
                backoff = 2000;
                BSPCore.LOGGER.info("BSP-Core redis: listening on {}:{} channel \"{}\"", host, port, channel);
                boolean pinged = false;
                while (!closed) {
                    Object v;
                    try {
                        v = decode(in);
                    } catch (java.net.SocketTimeoutException quiet) {
                        // nothing heard for a while: ask; a connection that cannot answer even that is dead (a half-open socket)
                        if (pinged) {
                            throw new IOException("no answer to PING; reconnecting");
                        }
                        s.getOutputStream().write(encode("PING"));
                        s.getOutputStream().flush();
                        pinged = true;
                        continue;
                    }
                    pinged = false;
                    if (v instanceof List<?> l && l.size() == 3 && "message".equals(l.get(0)) && l.get(2) instanceof String text) {
                        String[] p = parse(text, serverId);
                        if (p != null) {
                            onMessage.accept(p);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                subscribed = false;
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                if (!closed) {
                    BSPCore.LOGGER.warn("BSP-Core redis: subscriber dropped ({}); retrying in {} s", lastError, backoff / 1000);
                }
            } finally {
                closeQuietly(sub);
                sub = null;
            }
            if (closed) {
                return;
            }
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                return;
            }
            backoff = Math.min(60_000, backoff * 2);
        }
    }

    private static void closeQuietly(@Nullable Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // gone
            }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        closeQuietly(pub);
        closeQuietly(sub);
        if (subThread != null) {
            subThread.interrupt();
        }
    }
}
