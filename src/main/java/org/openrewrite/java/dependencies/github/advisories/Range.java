package org.openrewrite.java.dependencies.github.advisories;

import lombok.Value;
import org.openrewrite.internal.lang.Nullable;

import java.util.List;

@Value
public class Range {
    List<Event> events;

    @Nullable
    public String getIntroduced() {
        for (Event event : events) {
            if(event.getIntroduced() != null) {
                return event.getIntroduced();
            }
        }
        return null;
    }

    @Nullable
    public String getFixed() {
        for (Event event : events) {
            if(event.getFixed() != null) {
                return event.getFixed();
            }
        }
        return null;
    }

    @Value
    public static class Event {
        @Nullable
        String introduced;

        @Nullable
        String fixed;
    }
}
