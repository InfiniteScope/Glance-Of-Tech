package site.infinitescope.glance.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Period {
    MORNING("morning"),
    EVENING("evening");

    private final String value;

    Period(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static Period fromValue(String value) {
        for (Period p : values()) {
            if (p.value.equalsIgnoreCase(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown period: " + value);
    }
}
