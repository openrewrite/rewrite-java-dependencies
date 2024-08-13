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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class DependencyInsightTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyInsight());
    }

    @Test
    @DocumentExample
    void csproj() {
        // Taken from
        // https://learn.microsoft.com/en-us/aspnet/web-forms/overview/deployment/web-deployment-in-the-enterprise/understanding-the-project-file
        // https://learn.microsoft.com/en-us/nuget/consume-packages/package-references-in-project-files
        rewriteRun(
          xml(
            //language=xml
            """
              <Project ToolsVersion="4.0" DefaultTargets="FullPublish" xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
                <ItemGroup>
                  <PackageReference Include="Contoso.Utility.UsefulStuff" Version="3.6.*" />
                  <PackageReference Include="Contoso.Utility.SomeOtherUsefulStuff" Version="3.6.0">
                    <ExcludeAssets>compile</ExcludeAssets>
                    <PrivateAssets>contentFiles</PrivateAssets>
                  </PackageReference>
                </ItemGroup>
              </Project>
              """,
            //language=xml
            """
              <Project ToolsVersion="4.0" DefaultTargets="FullPublish" xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
                <ItemGroup>
                  <!--~~(Contoso.Utility.UsefulStuff:3.6.*)~~>--><PackageReference Include="Contoso.Utility.UsefulStuff" Version="3.6.*" />
                  <!--~~(Contoso.Utility.SomeOtherUsefulStuff:3.6.0)~~>--><PackageReference Include="Contoso.Utility.SomeOtherUsefulStuff" Version="3.6.0">
                    <ExcludeAssets>compile</ExcludeAssets>
                    <PrivateAssets>contentFiles</PrivateAssets>
                  </PackageReference>
                </ItemGroup>
              </Project>
              """,
            spec -> spec.path("MyFirst.csproj")
          )
        );
    }

    @Test
    void packagesConfig() {
        // Taken from https://learn.microsoft.com/en-us/nuget/reference/packages-config
        rewriteRun(
          xml(
            //language=xml
            """
              <?xml version="1.0" encoding="utf-8"?>
              <packages>
                <package id="Microsoft.CodeDom.Providers.DotNetCompilerPlatform" version="1.0.0" targetFramework="net46" />
                <package id="Microsoft.Net.Compilers" version="1.0.0" targetFramework="net46" developmentDependency="true" />
                <package id="Microsoft.Web.Infrastructure" version="1.0.0.0" targetFramework="net46" />
                <package id="Microsoft.Web.Xdt" version="2.1.1" targetFramework="net46" />
                <package id="Newtonsoft.Json" version="8.0.3" allowedVersions="[8,10)" targetFramework="net46" />
                <package id="NuGet.Core" version="2.11.1" targetFramework="net46" />
                <package id="NuGet.Server" version="2.11.2" targetFramework="net46" />
                <package id="RouteMagic" version="1.3" targetFramework="net46" />
                <package id="WebActivatorEx" version="2.1.0" targetFramework="net46" />
              </packages>
              """,
            //language=xml
            """
              <?xml version="1.0" encoding="utf-8"?>
              <packages>
                <!--~~(Microsoft.CodeDom.Providers.DotNetCompilerPlatform:1.0.0)~~>--><package id="Microsoft.CodeDom.Providers.DotNetCompilerPlatform" version="1.0.0" targetFramework="net46" />
                <!--~~(Microsoft.Net.Compilers:1.0.0)~~>--><package id="Microsoft.Net.Compilers" version="1.0.0" targetFramework="net46" developmentDependency="true" />
                <!--~~(Microsoft.Web.Infrastructure:1.0.0.0)~~>--><package id="Microsoft.Web.Infrastructure" version="1.0.0.0" targetFramework="net46" />
                <!--~~(Microsoft.Web.Xdt:2.1.1)~~>--><package id="Microsoft.Web.Xdt" version="2.1.1" targetFramework="net46" />
                <!--~~(Newtonsoft.Json:8.0.3)~~>--><package id="Newtonsoft.Json" version="8.0.3" allowedVersions="[8,10)" targetFramework="net46" />
                <!--~~(NuGet.Core:2.11.1)~~>--><package id="NuGet.Core" version="2.11.1" targetFramework="net46" />
                <!--~~(NuGet.Server:2.11.2)~~>--><package id="NuGet.Server" version="2.11.2" targetFramework="net46" />
                <!--~~(RouteMagic:1.3)~~>--><package id="RouteMagic" version="1.3" targetFramework="net46" />
                <!--~~(WebActivatorEx:2.1.0)~~>--><package id="WebActivatorEx" version="2.1.0" targetFramework="net46" />
              </packages>
              """,
            spec -> spec.path("packages.config")
          )
        );
    }
}
