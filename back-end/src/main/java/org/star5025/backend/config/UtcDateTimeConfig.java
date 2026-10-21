package org.star5025.backend.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class UtcDateTimeConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcTaskTimes() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                @Override
                public void serialize(LocalDateTime value, JsonGenerator output, SerializerProvider provider) throws IOException {
                    output.writeString(value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                }
            });
            builder.deserializerByType(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
                @Override
                public LocalDateTime deserialize(JsonParser input, DeserializationContext context) throws IOException {
                    String value = input.getValueAsString();
                    try {
                        return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                    } catch (DateTimeParseException error) {
                        try {
                            // Legacy offset-free API inputs are interpreted as UTC, never server-local time.
                            return LocalDateTime.parse(value);
                        } catch (DateTimeParseException invalid) {
                            return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, value, "Expected ISO-8601 date/time");
                        }
                    }
                }
            });
        };
    }
}
