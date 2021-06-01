package org.dalquist.hubitat.logging;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.options.OptionsParser;
import com.vlkan.rfos.RotationConfig;
import com.vlkan.rfos.policy.DailyRotationPolicy;
import com.vlkan.rfos.policy.SizeBasedRotationPolicy;

import org.apache.commons.text.StringEscapeUtils;
import org.dalquist.hubitat.logging.messages.EventLogMessage;
import org.dalquist.hubitat.logging.messages.MainLogMessage;
import org.dalquist.hubitat.logging.messages.ZigbeeLogMessage;
import org.dalquist.hubitat.logging.messages.ZwaveLogMessage;
import org.eclipse.jetty.util.component.LifeCycle;

/**
 */
public class App {
    static {
        System.setProperty("flogger.backend_factory",
                "com.google.common.flogger.backend.slf4j.Slf4jBackendFactory#getInstance");
    }

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    public static void main(String[] args) throws Exception {
        OptionsParser parser = OptionsParser.newOptionsParser(AppOptions.class);
        parser.parseAndExitUponError(args);
        AppOptions options = parser.getOptions(AppOptions.class);

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();
        wsContainer.setAsyncSendTimeout(100); // Set a LOW send timeout, we are very close to the server and want to
                                              // detect connection loss quickly
        try (AutoCloseable wsContainerClosable = () -> LifeCycle.stop(wsContainer)) {
            ImmutableList<WebSocketLogger<?>> loggers = ImmutableList.<WebSocketLogger<?>>builder()
                    // Main logger
                    .add(createLogger(options, wsContainer, MainLogMessage.class, "logsocket", App::formatLogMessage))
                    // Event logger
                    .add(createLogger(options, wsContainer, EventLogMessage.class, "eventsocket",
                            App::formatEventLogMessage))
                    // ZWave Logger
                    .add(createLogger(options, wsContainer, ZwaveLogMessage.class, "zwaveLogsocket",
                            App::formatZwaveLogMessage))
                    // Zigbee Logger
                    .add(createLogger(options, wsContainer, ZigbeeLogMessage.class, "zigbeeLogsocket",
                            App::formatZigbeeLogMessage))
                    // create logger set
                    .build();

            Runtime.getRuntime().addShutdownHook(new ShutdownHook(loggers));

            try (AutoCloseable closable = connectAsync(loggers)) {
                boolean allDone;
                do {
                    allDone = true;
                    for (WebSocketLogger<?> logger : loggers) {
                        allDone = logger.awaitDisconnect(1, TimeUnit.SECONDS) && allDone;
                    }
                } while (!allDone);
            }
        }
    }

    private static AutoCloseable connectAsync(Collection<WebSocketLogger<?>> loggers) throws Exception {
        ImmutableList<CompositeAutoCloseable.ThrowingSupplier<AutoCloseable>> closableSuppliers = loggers.stream()
                .map(wsLogger -> (CompositeAutoCloseable.ThrowingSupplier<AutoCloseable>) wsLogger::connectAsync)
                .collect(ImmutableList.toImmutableList());
        return CompositeAutoCloseable.fromSuppliers(closableSuppliers);
    }

    private static <T> WebSocketLogger<T> createLogger(AppOptions options, WebSocketContainer wsContainer,
            Class<T> logType, String logFile, LogFormatter<T> logFormatter) throws URISyntaxException {
        return new WebSocketLogger<>(wsContainer, String.format("ws://%s/%s", options.hubAddr, logFile), logType,
                createRotationConfig(options, logFile), logFormatter);
    }

    private static RotationConfig createRotationConfig(AppOptions options, String logFile) {
        Path logDir = Path.of(options.logDir).toAbsolutePath();
        File file = logDir.resolve(String.format("%s.log", logFile)).toFile();
        String pattern = logDir.resolve(String.format("%s-%%d{%s}.log", logFile, options.rotationPattern)).toString();
        logger.atInfo().log("Creating log file %s with rotation pattern %s", file, pattern);
        return RotationConfig.builder().file(file).filePattern(pattern).clock(LocalSystemClock.getInstance())
                .policy(DailyRotationPolicy.getInstance())
                .policy(new SizeBasedRotationPolicy(1024L * 1024 * options.rotationSize)).build();
    }

    static void formatLogMessage(MainLogMessage msg, LogFormatter.Logger logger) {
        logger.log("%-23s %s[%03d] %5S  %s: %s", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(msg.time), msg.type,
                msg.id, msg.level, msg.name, StringEscapeUtils.unescapeHtml4(msg.msg).replace("&apos;", "'"));
    }

    static void formatEventLogMessage(EventLogMessage msg, LogFormatter.Logger logger) {
        if ("DEVICE".equals(msg.source)) {
            logger.log("%-23s %s[%d:%03d] %s: %s = %s%s, %s",
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)),
                    msg.source, msg.hubId, msg.deviceId, msg.displayName, msg.name, msg.value,
                    "null".equals(msg.unit) ? "" : msg.unit, msg.descriptionText);
        } else {
            logger.log("%-23s %s[%d:%03d] %s: %s = %s%s, %s",
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()), msg.source, msg.hubId,
                    msg.installedAppId, msg.displayName, msg.name, msg.value, "null".equals(msg.unit) ? "" : msg.unit,
                    msg.descriptionText);
        }
    }

    static void formatZwaveLogMessage(ZwaveLogMessage msg, LogFormatter.Logger logger) {
        logger.log(
                "%-23s   %s[%03d] %32s: %s %3dms %5.1fkbps routeChanged: %s, repeaters: [%s], channels: ack %d / tx %d, rssi: [%s]",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(msg.time), msg.id, msg.seqNo, msg.name, msg.type,
                msg.getTransmissionTimeMs().orElse(-1), msg.getSpeed().orElse(Float.NaN),
                msg.getRouteChanged().orElse(false) ? "Y" : "N", msg.getRepeaters().orElse(""),
                msg.getAckChannel().orElse(-1), msg.getTransmitChannel().orElse(-1), msg.getRssi().orElse(""));
    }

    static void formatZigbeeLogMessage(ZigbeeLogMessage msg, LogFormatter.Logger logger) {
        logger.log(
                "%-23s %4X[%03d] %32s: %s profile: 0x%03X, cluster: 0x%03X, group: %d, src: %X, dst: %X, lhLqi: %d, lhRssi: %d",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(msg.time), msg.id, msg.sequence, msg.name, msg.type,
                msg.profileId, msg.clusterId, msg.groupId, msg.sourceEndpoint, msg.destinationEndpoint, msg.lastHopLqi,
                msg.lastHopRssi);
    }
}
