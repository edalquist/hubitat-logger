package org.dalquist.hubitat.logging;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StackSize;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vlkan.rfos.RotatingFileOutputStream;
import com.vlkan.rfos.RotationConfig;

import org.eclipse.jetty.websocket.core.WebSocketTimeoutException;

@ClientEndpoint
public final class WebSocketLogger<T> {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private static final ScheduledExecutorService RECONNECT_EXECUTOR = Executors.newScheduledThreadPool(4);

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeJsonDeserializer()).create();

    private final AtomicBoolean cleanup = new AtomicBoolean(false);
    private final AtomicBoolean connect = new AtomicBoolean(false);
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private volatile CountDownLatch closedLatch;
    private volatile Session wsSesson;
    private volatile PrintStream logStream;

    private final WebSocketContainer wsContainer;
    private final URI serverTarget;
    private final Class<T> jsonLogType;
    private final RotationConfig rotationConfig;
    private final LogFormatter<T> logFormatter;

    public WebSocketLogger(WebSocketContainer wsContainer, String serverTarget, Class<T> jsonLogType,
            RotationConfig rotationConfig, LogFormatter<T> logFormatter) throws URISyntaxException {
        this(wsContainer, new URI(serverTarget), jsonLogType, rotationConfig, logFormatter);
    }

    public WebSocketLogger(WebSocketContainer wsContainer, URI serverTarget, Class<T> jsonLogType,
            RotationConfig rotationConfig, LogFormatter<T> logFormatter) {
        this.wsContainer = wsContainer;
        this.serverTarget = serverTarget;
        this.jsonLogType = jsonLogType;
        this.rotationConfig = rotationConfig;
        this.logFormatter = logFormatter;
    }

    public AutoCloseable connectAsync() throws DeploymentException, IOException {
        synchronized (connect) {
            logger.atInfo().log("Connecting to %s", serverTarget);
            if (connect.getAndSet(true)) {
                throw new IllegalStateException("Illegal to call connectAsync on already connected socket");
            }

            closedLatch = new CountDownLatch(1);
            cleanup.set(false);

            try {
                logStream = new PrintStream(new RotatingFileOutputStream(rotationConfig), true, StandardCharsets.UTF_8);
                wsSesson = wsContainer.connectToServer(this, serverTarget);
            } catch (DeploymentException | IOException | RuntimeException e) {
                logger.atSevere().withCause(e).log("Failed to connect to %s", serverTarget);
                disconnect();
                throw e;
            }

            logger.atInfo().log("Connected to %s", serverTarget);
        }

        return () -> disconnect();
    }

    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private void reconnect(Session session) {
        if (!reconnectScheduled.compareAndSet(false, true)) {
            logger.atInfo().log("Skipping reconnect to %s, there is already one scheduled", serverTarget);
            return;
        }

        // Incrementally slow down retries up to 30s
        int reconnectDelay = Math.min(reconnectCount.getAndIncrement(), 30);
        logger.atInfo().log("Reconnecting to %s in %ss", serverTarget, reconnectDelay);

        RECONNECT_EXECUTOR.schedule(() -> doReconnect(session), reconnectDelay, TimeUnit.SECONDS);
    }

    private void doReconnect(Session session) {
        synchronized (connect) {
            if (!connect.get()) {
                logger.atWarning().log("Illegal to call reconnect after disconnect");
                return;
            }
            if (this.wsSesson != session) {
                logger.atInfo().log("Ignoring reconnect for stale session: %s", serverTarget);
                return;
            }

            if (wsSesson != null) {
                logger.atInfo().log("Closing old WS Session: %s", serverTarget);
                try {
                    wsSesson.close();
                } catch (IOException e) {
                    logger.atWarning().withCause(e).log("Failed to disconnect WS Session before reconnectiong: %s",
                            serverTarget);
                }
            }

            logger.atInfo().log("Reconnect attempt %s to %s", reconnectCount.get(), serverTarget);
            try {
                wsSesson = wsContainer.connectToServer(this, serverTarget);
                logger.atInfo().log("Reconnect attempt %s to %s", reconnectCount.get(), serverTarget);
                reconnectCount.set(0);
                reconnectScheduled.set(false);
            } catch (DeploymentException | IOException e) {
                logger.atSevere().withCause(e).log("Reconnect attempt %s to %s failed", reconnectCount.get(),
                        serverTarget);

                // re-queue another reconnect
                reconnectScheduled.set(false);
                reconnect(wsSesson);
            }
        }
    }

    public boolean isConnected() {
        return wsSesson != null && wsSesson.isOpen();
    }

    public boolean awaitDisconnect(long timeout, TimeUnit unit) throws InterruptedException {
        synchronized (connect) {
            if (!connect.get()) {
                return true;
            }
        }

        try {
            wsSesson.getAsyncRemote().sendPing(ByteBuffer.allocate(1));
        } catch (IllegalArgumentException | IOException e) {
            logger.atWarning().withCause(e).log("Ping Failed");
        }

        if (isConnected()) {
            logger.atInfo().atMostEvery(1, TimeUnit.MINUTES).log("%s is connected", serverTarget);
        } else {
            logger.atInfo().atMostEvery(1, TimeUnit.MINUTES).log("%s is disconnected", serverTarget);
            reconnect(wsSesson);
        }
        return closedLatch.await(timeout, unit);
    }

    public void disconnect() {
        if (connect.getAndSet(false)) {
            logger.atInfo().log("Disconnect from %s", serverTarget);
            cleanup();
        } else {
            logger.atInfo().log("Already disconnected from %s", serverTarget);
        }
    }

    private void cleanup() {
        // Only let one cleanup call through
        if (!cleanup.getAndSet(true)) {
            return;
        }
        synchronized (connect) {
            logger.atInfo().log("Cleaning up connection to %s", serverTarget);
            if (wsSesson != null) {
                logger.atInfo().log("Closing WS Session to %s", serverTarget);
                try {
                    wsSesson.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    wsSesson = null;
                }
            }

            // Esnure log is written & closed
            if (logStream != null) {
                logger.atInfo().log("Saving log file for %s", serverTarget);
                logStream.flush();
                logStream.close();
                logStream = null;
            }

            logger.atInfo().log("Cleaned up connection to %s", serverTarget);
            closedLatch.countDown();
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        logger.atInfo().log("Connection opened to %s", serverTarget);
    }

    @FunctionalInterface
    interface Logger {
        public void log(String format, Object... args);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            T msg = GSON.fromJson(message, jsonLogType);
            logFormatter.format(msg, logStream::printf);
            logStream.println();
        } catch (RuntimeException e) {
            logger.atSevere().withCause(e).log("Error handling message from %s\n%s", serverTarget, message);
            disconnect();
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.atInfo().log("Connection to %s closed: %s", serverTarget, closeReason.getCloseCode());

        if (connect.get()) {
            reconnect(session);
        } else {
            cleanup();
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        if (error instanceof WebSocketTimeoutException) {
            logger.atInfo().log("%s, reconnect: %s", error.getMessage(), serverTarget);
        } else {
            logger.atWarning().withCause(error).log("Unexpected error on: %s", serverTarget);
        }

        if (connect.get()) {
            reconnect(session);
        } else {
            cleanup();
        }
    }
}
