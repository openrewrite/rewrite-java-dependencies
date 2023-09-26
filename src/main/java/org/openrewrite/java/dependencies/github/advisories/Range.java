/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
            if (event.getIntroduced() != null) {
                return event.getIntroduced();
            }
        }
        return null;
    }

    @Nullable
    public String getFixed() {
        for (Event event : events) {
            if (event.getFixed() != null) {
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
