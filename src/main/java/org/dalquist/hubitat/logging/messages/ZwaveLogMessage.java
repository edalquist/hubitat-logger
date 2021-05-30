package org.dalquist.hubitat.logging.messages;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ZwaveLogMessage {
    public int seqNo;
    public String name;
    public String id; // in hex
    public Map<Integer, int[]> imeReport;
    public LocalDateTime time;
    public String type;

    public Optional<Boolean> getRouteChanged() {
        int[] values = imeReport.get(0);
        if (values == null || values.length != 1) {
            return Optional.empty();
        }
        return Optional.of(values[0] == 1);
    }

    public Optional<Integer> getTransmissionTimeMs() {
        int[] values = imeReport.get(1);
        if (values == null || values.length != 2) {
            return Optional.empty();
        }
        int transmissionTime = ((values[0] & 0xff) << 2) | (values[1] & 0xff);
        return Optional.of(transmissionTime);
    }

    public Optional<String> getRepeaters() {
        int[] values = imeReport.get(2);
        if (values == null || values.length != 5 || values[0] == 0) {
            return Optional.empty();
        }
        return Optional.of(IntStream.of(values).limit(4).filter(i -> i != 0).mapToObj(i -> String.format("%02X", i))
                .collect(Collectors.joining(" > ")));
    }

    public Optional<Float> getSpeed() {
        int[] values = imeReport.get(2);
        if (values == null || values.length != 5) {
            return Optional.empty();
        }
        switch (values[4]) {
            case 1:
                return Optional.of(9.6f);
            case 2:
                return Optional.of(40f);
            case 3:
                return Optional.of(100f);
            default:
                return Optional.empty();
        }
    }

    public Optional<String> getRssi() {
        int[] values = imeReport.get(3);
        if (values == null) {
            return Optional.empty();
        }

        return Optional.of(IntStream.of(values).mapToObj(this::getRSSIValue).collect(Collectors.joining(", ")));
    }

    private String getRSSIValue(int rssiByte) {
        if ((rssiByte & 0xff) == 127) {
            return "N/A";
        } else if ((rssiByte & 0xff) == 126) {
            return "MAX";
        } else if ((rssiByte & 0xff) == 125) {
            return "MIN";
        } else {
            return rssiByte + " dBm";
        }
    }

    public Optional<Integer> getAckChannel() {
        int[] values = imeReport.get(4);
        if (values == null || values.length == 0) {
            return Optional.empty();
        }
        return Optional.of(values[0]);
    }

    public Optional<Integer> getTransmitChannel() {
        int[] values = imeReport.get(5);
        if (values == null || values.length == 0) {
            return Optional.empty();
        }
        return Optional.of(values[0]);
    }
}
