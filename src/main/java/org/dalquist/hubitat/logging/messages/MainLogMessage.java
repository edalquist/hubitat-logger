package org.dalquist.hubitat.logging.messages;

import java.time.LocalDateTime;

public final class MainLogMessage {
    public String name;
    public String msg;
    public int id;
    public LocalDateTime time;
    public String type; // TODO enum?
    public String level; // TODO enum?
}
