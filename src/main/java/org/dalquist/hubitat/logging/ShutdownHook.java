package org.dalquist.hubitat.logging;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;

final class ShutdownHook extends Thread {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private ImmutableSet<WebSocketLogger<?>> loggers;

    ShutdownHook(WebSocketLogger<?>... loggers) {
        this.loggers = ImmutableSet.copyOf(loggers);
    }

    ShutdownHook(Collection<WebSocketLogger<?>> loggers) {
        this.loggers = ImmutableSet.copyOf(loggers);
    }

    @Override
    public void run() {
        logger.atInfo().log("Disconnecting %s Loggers", loggers.size());
        loggers.forEach(WebSocketLogger::disconnect);
        loggers.forEach(l -> {
            try {
                l.awaitDisconnect(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }
}