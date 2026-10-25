/*
 * Copyright 2026 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.maven.MavenSettings;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

public final class MavenCentralMirror {

    private MavenCentralMirror() {
    }

    /**
     * Settings with no mirror but one of Maven Central, the mirror CI names in MAVEN_CENTRAL_MIRROR_URL, _USERNAME and
     * _PASSWORD, so that Central is reached as the rest of the build reaches it. With no mirror named, none at all.
     */
    public static MavenSettings settingsMirroringOnlyCentral(ExecutionContext ctx) {
        if (System.getenv("MAVEN_CENTRAL_MIRROR_URL") == null) {
            return new MavenSettings(null, null, null, null, null, null);
        }
        return MavenSettings.parse(new Parser.Input(Path.of("settings.xml"), () -> new ByteArrayInputStream(
          //language=xml
          """
            <settings>
                <servers>
                    <server>
                        <id>central-mirror</id>
                        <username>${env.MAVEN_CENTRAL_MIRROR_USERNAME}</username>
                        <password>${env.MAVEN_CENTRAL_MIRROR_PASSWORD}</password>
                    </server>
                </servers>
                <mirrors>
                    <mirror>
                        <id>central-mirror</id>
                        <mirrorOf>central</mirrorOf>
                        <url>${env.MAVEN_CENTRAL_MIRROR_URL}</url>
                    </mirror>
                </mirrors>
            </settings>
            """.getBytes())), ctx);
    }
}
