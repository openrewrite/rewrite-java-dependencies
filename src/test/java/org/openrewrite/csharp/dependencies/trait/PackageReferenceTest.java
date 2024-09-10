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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.RewriteTest.toRecipe;
import static org.openrewrite.xml.Assertions.xml;

class PackageReferenceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new PackageReference.Matcher().asVisitor(ref -> ref.withVersion("3.6.1"))));
    }

    @Test
    @DocumentExample
    void updateVersion() {
        rewriteRun(
          xml(
            //language=xml
            """
              <Project>
                <ItemGroup>
                  <PackageReference Include="Contoso.Utility.UsefulStuff" Version="3.6.0" />
                </ItemGroup>
              </Project>
              """,
            //language=xml
            """
              <Project>
                <ItemGroup>
                  <PackageReference Include="Contoso.Utility.UsefulStuff" Version="3.6.1" />
                </ItemGroup>
              </Project>
              """,
            spec -> spec.path("MyFirst.csproj")
          )
        );
    }
}
