package ro.superpont.football.match.model;

import jakarta.persistence.*;

import java.time.*;
import java.util.*;

public enum MatchStatus {
    SCHEDULED,
    LIVE,
    FINISHED,
    POSTPONED,
    CANCELLED
}
