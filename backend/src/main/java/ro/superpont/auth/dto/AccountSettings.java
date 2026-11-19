package ro.superpont.auth.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record AccountSettings(
        @NotNull @JsonDeserialize(using = BooleanDeserializer.class) Boolean hidePredictionsUntilKickoff) {
    public static class BooleanDeserializer extends ValueDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.hasToken(JsonToken.VALUE_TRUE)) return true;
            if (parser.hasToken(JsonToken.VALUE_FALSE)) return false;
            return context.reportInputMismatch(Boolean.class, "Visibility preference must be a JSON boolean");
        }
    }
}
