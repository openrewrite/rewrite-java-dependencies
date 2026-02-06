/*
 * Copyright 2025 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Recipe;

import java.util.Arrays;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindRepositoryOrder extends Recipe {

    @Override
    public String getDisplayName() {
        return "Maven repository order";
    }

    @Override
    public String getDescription() {
        return "Determine the order in which dependencies will be resolved for each `pom.xml` or " +
               "`build.gradle` based on its defined repositories and effective settings.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                new org.openrewrite.maven.search.FindRepositoryOrder(),
                new org.openrewrite.gradle.search.FindRepositoryOrder()
        );
    }
}
