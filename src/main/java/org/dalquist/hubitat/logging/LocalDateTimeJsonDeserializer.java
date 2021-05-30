package org.dalquist.hubitat.logging;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public final class LocalDateTimeJsonDeserializer implements JsonDeserializer<LocalDateTime> {

    private DateTimeFormatter HUBITAT_TIMESTAMP_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public LocalDateTime deserialize(JsonElement element, Type type, JsonDeserializationContext ctx)
            throws JsonParseException {
        return LocalDateTime.parse(element.getAsString(), HUBITAT_TIMESTAMP_PATTERN);
    }
}
