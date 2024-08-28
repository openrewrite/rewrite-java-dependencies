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
package org.openrewrite.csharp.dependencies;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class UpgradeDependencyVersionTest implements RewriteTest {

    @Test
    @DocumentExample
    void packagesConfig() {
        rewriteRun(
          spec -> spec.recipe(new UpgradeDependencyVersion("Microsoft.*", "2.1.2")),
          xml(
            //language=xml
            """
              <?xml version="1.0" encoding="utf-8"?>
              <packages>
                <package id="Microsoft.Web.Xdt" version="2.1.1" targetFramework="net46" />
                <package id="WebActivatorEx" version="2.1.0" targetFramework="net46" />
              </packages>
              """,
            //language=xml
            """
              <?xml version="1.0" encoding="utf-8"?>
              <packages>
                <package id="Microsoft.Web.Xdt" version="2.1.2" targetFramework="net46" />
                <package id="WebActivatorEx" version="2.1.0" targetFramework="net46" />
              </packages>
              """,
            spec -> spec.path("packages.config")
          )
        );
    }

    @Test
    void csproj() {
        rewriteRun(
          spec -> spec.recipe(new UpgradeDependencyVersion("Contoso.Utility.SomeOther*", "3.6.2")),
          xml(
            //language=xml
            """
              <Project>
                <ItemGroup>
                  <PackageReference Include="Contoso.Utility.UsefulStuff" Version="3.6.1" />
                  <PackageReference Include="Contoso.Utility.SomeOtherUsefulStuff" Version="3.6.0" />
                </ItemGroup>
              </Project>
              """,
            //language=xml
            """
              <Project>
                <ItemGroup>
                  <PackageReference Include="Contoso.Utility.UsefulStuff" Version="3.6.1" />
                  <PackageReference Include="Contoso.Utility.SomeOtherUsefulStuff" Version="3.6.2" />
                </ItemGroup>
              </Project>
              """,
            spec -> spec.path("MyFirst.csproj")
          )
        );
    }
}
