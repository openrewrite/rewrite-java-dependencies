/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openrewrite.java.dependencies.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VersionParser {
    private final Map<String, Version> cache = new ConcurrentHashMap<>();

    public VersionParser() {
    }

    public Version transform(String original) {
        return cache.computeIfAbsent(original, this::parse);
    }

    private Version parse(String original) {
        List<String> parts = new ArrayList<>();
        boolean digit = false;
        int startPart = 0;
        int pos = 0;
        int endBaseStr = 0;
        for (; pos < original.length(); pos++) {
            char ch = original.charAt(pos);
            if (ch == '.' || ch == '_' || ch == '-' || ch == '+') {
                parts.add(original.substring(startPart, pos));
                startPart = pos + 1;
                digit = false;
                if (ch != '.' && endBaseStr == 0) {
                    endBaseStr = pos;
                }
            } else if (ch >= '0' && ch <= '9') {
                if (!digit && pos > startPart) {
                    if (endBaseStr == 0) {
                        endBaseStr = pos;
                    }
                    parts.add(original.substring(startPart, pos));
                    startPart = pos;
                }
                digit = true;
            } else {
                if (digit) {
                    if (endBaseStr == 0) {
                        endBaseStr = pos;
                    }
                    parts.add(original.substring(startPart, pos));
                    startPart = pos;
                }
                digit = false;
            }
        }
        if (pos > startPart) {
            parts.add(original.substring(startPart, pos));
        }
        return new DefaultVersion(original, parts);
    }

    private static class DefaultVersion implements Version {
        private final String source;
        private final String[] parts;
        private final Long[] numericParts;

        public DefaultVersion(String source, List<String> parts) {
            this.source = source;
            this.parts = parts.toArray(new String[0]);
            this.numericParts = new Long[this.parts.length];
            for (int i = 0; i < parts.size(); i++) {
                try {
                    this.numericParts[i] = Long.parseLong(this.parts[i]);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        @Override
        public String toString() {
            return source;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            DefaultVersion other = (DefaultVersion) obj;
            return source.equals(other.source);
        }

        @Override
        public int hashCode() {
            return source.hashCode();
        }

        @Override
        public String[] getParts() {
            return parts;
        }

        @Override
        public Long[] getNumericParts() {
            return numericParts;
        }

        @Override
        public String getSource() {
            return source;
        }
    }
}
