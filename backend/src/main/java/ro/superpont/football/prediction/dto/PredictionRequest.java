package ro.superpont.football.prediction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record PredictionRequest(
        @NotNull @Min(0) @Max(99) @JsonDeserialize(using = ScoreDeserializer.class)
        Integer homeScore,
        @NotNull @Min(0) @Max(99) @JsonDeserialize(using = ScoreDeserializer.class)
        Integer awayScore) {
    public static class ScoreDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Integer.class, "Prediction scores must be JSON integers");
            }
            return parser.getIntValue();
        }
    }
}
