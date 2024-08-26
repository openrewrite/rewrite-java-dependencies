/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.csharp.dependencies.trait;

import lombok.Value;
import org.openrewrite.Cursor;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.trait.SimpleTraitMatcher;
import org.openrewrite.trait.Trait;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toMap;

@Value
public class PackageReference implements Trait<Xml.Tag> {

    Cursor cursor;

    String include;
    String version;

    public Xml.Tag withVersion(String newVersion) {
        Xml.Tag tag = getTree();
        if (!Objects.equals(this.version, newVersion)) {
            InMemoryExecutionContext ctx = new InMemoryExecutionContext();
            tag = (Xml.Tag) new ChangeTagAttribute("//PackageReference", "Version", newVersion, this.version, null)
                    .getVisitor().visitNonNull(tag, ctx);
            tag = (Xml.Tag) new ChangeTagAttribute("/packages/package", "version", newVersion, this.version, null)
                    .getVisitor().visitNonNull(tag, ctx);
        }
        return tag;
    }

    public static class Matcher extends SimpleTraitMatcher<PackageReference> {
        XPathMatcher packageReference = new XPathMatcher("//PackageReference");
        XPathMatcher packageConfig = new XPathMatcher("/packages/package");

        @Override
        protected PackageReference test(Cursor cursor) {
            Object value = cursor.getValue();
            if (value instanceof Xml.Tag) {
                Xml.Tag tag = (Xml.Tag) value;
                if (packageReference.matches(cursor)) {
                    Map<String, String> attrs = asMap(tag.getAttributes());
                    String include = attrs.get("Include");
                    // XXX Floating versions: https://learn.microsoft.com/en-us/nuget/concepts/dependency-resolution#floating-versions
                    String version = attrs.get("Version");
                    // XXX Condition https://learn.microsoft.com/en-us/nuget/consume-packages/package-references-in-project-files#adding-a-packagereference-condition
                    // XXX Assets https://learn.microsoft.com/en-us/nuget/consume-packages/package-references-in-project-files#controlling-dependency-assets
                    // XXX Locking dependencies https://learn.microsoft.com/en-us/nuget/consume-packages/package-references-in-project-files#locking-dependencies
                    if (include != null && version != null) {
                        return new PackageReference(cursor, include, version);
                    }
                } else if (packageConfig.matches(cursor)) {
                    Map<String, String> attrs = asMap(tag.getAttributes());
                    String id = attrs.get("id");
                    String version = attrs.get("version");
                    // XXX Parse `allowedVersions`, `developmentDependency` and `targetFramework`
                    if (id != null && version != null) {
                        return new PackageReference(cursor, id, version);
                    }
                }
            }
            return null;
        }

        private static Map<String, String> asMap(List<Xml.Attribute> attributes) {
            return attributes.stream().collect(toMap(attr -> attr.getKey().getName(), attr -> attr.getValue().getValue()));
        }
    }
}
