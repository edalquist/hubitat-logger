package org.dalquist.hubitat.logging;

public interface LogFormatter<T> {
    @FunctionalInterface
    public interface Logger {
        void log(String format, Object... args);
    }

    void format(T msg, Logger logger);
}
