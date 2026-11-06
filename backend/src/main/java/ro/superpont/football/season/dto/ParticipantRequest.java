package ro.superpont.football.season.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record ParticipantRequest(@NotNull UUID teamId) {}
