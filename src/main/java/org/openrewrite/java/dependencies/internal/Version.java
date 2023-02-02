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

public interface Version {
    /**
     * Returns the original {@link String} representation of the version.
     */
    String getSource();

    /**
     * Returns all the parts of this version. e.g. 1.2.3 returns [1,2,3] or 1.2-beta4 returns [1,2,beta,4].
     */
    String[] getParts();

    /**
     * Returns all the numeric parts of this version as {@link Long}, with nulls in non-numeric positions. eg. 1.2.3 returns [1,2,3] or 1.2-beta4 returns [1,2,null,4].
     */
    Long[] getNumericParts();
}
