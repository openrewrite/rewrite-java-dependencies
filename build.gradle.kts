plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A rewrite module automating Java dependency management."

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation("org.openrewrite:rewrite-maven:$rewriteVersion")
    implementation("org.openrewrite:rewrite-gradle:$rewriteVersion")

    implementation("org.openrewrite.gradle.tooling:model:latest.release")
    implementation("org.owasp:dependency-check-core:latest.release")
    implementation("org.owasp:dependency-check-utils:latest.release")

    implementation("org.apache.lucene:lucene-core:8.11.0")
    implementation("org.apache.lucene:lucene-analyzers-common:8.11.0")
    implementation("org.apache.lucene:lucene-queryparser:8.11.0")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.2.+")
}
