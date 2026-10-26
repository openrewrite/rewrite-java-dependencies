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
package org.openrewrite.java.dependencies.oldgroupids;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class UpdateMigrations {
    private static final String OGA_MAVEN_PLUGIN = "https://github.com/jonathanlermitage/oga-maven-plugin.git";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: UpdateMigrations <oga-maven-plugin-dir> <migrations-csv>");
            System.err.println("  oga-maven-plugin-dir: Shallow clone of jonathanlermitage/oga-maven-plugin, created or refreshed as needed.");
            System.exit(1);
        }
        File repo = new File(args[0]);
        File csv = new File(args[1]);

        checkoutOgaMavenPlugin(repo);
        git(repo, "log", "-1", "--format=Parsing oga-maven-plugin %h (%cs) into " + csv);
        ParseDefinitionMigrations.parseDefinitionMigrations(repo, csv);
    }

    private static void checkoutOgaMavenPlugin(File repo) throws IOException, InterruptedException {
        if (new File(repo, ".git").isDirectory()) {
            git(repo, "fetch", "--depth", "1", "origin", "HEAD");
            git(repo, "reset", "--hard", "FETCH_HEAD");
        } else {
            git(null, "clone", "--depth", "1", OGA_MAVEN_PLUGIN, repo.getPath());
        }
    }

    private static void git(@Nullable File directory, String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        int exitCode = new ProcessBuilder(command).directory(directory).inheritIO().start().waitFor();
        if (exitCode != 0) {
            throw new IOException("git " + String.join(" ", args) + " exited with " + exitCode);
        }
    }
}
