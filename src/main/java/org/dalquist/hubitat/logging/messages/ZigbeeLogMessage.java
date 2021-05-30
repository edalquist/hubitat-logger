package org.dalquist.hubitat.logging.messages;

import java.time.LocalDateTime;

public final class ZigbeeLogMessage {
    public String name;
    public int id; // hex
    public int profileId; // hex
    public int clusterId; // hex
    public int sourceEndpoint;
    public int destinationEndpoint;
    public int groupId;
    public int sequence;
    public int lastHopLqi;
    public int lastHopRssi;
    public LocalDateTime time;
    public String type;
}
/*
 * { "name":"Drier - Plug", "id":11051, "profileId":260, "clusterId":2820,
 * "sourceEndpoint":1, "destinationEndpoint":1, "groupId":0, "sequence":247,
 * "lastHopLqi":255, "lastHopRssi":-67, "time":"2021-05-29 07:28:50.326",
 * "type":"zigbeeRx" }
 */