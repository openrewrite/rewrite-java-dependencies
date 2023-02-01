package org.openrewrite.java.dependencies.github.advisories;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.ZonedDateTime;
import java.util.List;

@Value
public class Advisory {
    String id;
    ZonedDateTime published;
    List<Affected> affected;
    List<String> aliases;
    String summary;

    @JsonProperty("database_specific")
    DatabaseSpecific databaseSpecific;
}
