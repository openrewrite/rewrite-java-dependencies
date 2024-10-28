/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.dependencies;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.time.LocalDate;
import java.util.Optional;

public class RemoveExpiredSuppressions extends Recipe {
    private static final XPathMatcher X_PATH_MATCHER = new XPathMatcher("/suppressions/suppress");

    @Override
    public String getDisplayName() {
        return "Remove expired suppressions";
    }

    @Override
    public String getDescription() {
        return "Remove expired vulnerability suppressions from `DependencyCheck` `suppression.xml` files.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {

            @Override
            public @Nullable Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (X_PATH_MATCHER.matches(getCursor())) {
                    Optional<Xml.Attribute> untilAttribute = t.getAttributes().stream()
                            .filter(attribute -> "until".equals(attribute.getKeyAsString()))
                            .findFirst();
                    if (untilAttribute.isPresent()) {
                        String until = untilAttribute.get().getValue().getValue();
                        if (LocalDate.parse(until.substring(0, 10)).isBefore(LocalDate.now())) {
                            return null;
                        }
                    }
                }
                return t;
            }
        };
    }
}
