plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A rewrite module automating Java dependency management."

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-groovy")
    implementation("org.openrewrite:rewrite-properties")

    runtimeOnly("org.openrewrite:rewrite-java-17")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite.gradle.tooling:model:${rewriteVersion}")
    testImplementation(gradleApi())
    testRuntimeOnly("com.google.guava:guava:latest.release")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.2.+")
}

tasks {
    // ./gradlew parseDefinitionMigrations --args="./oga-maven-plugin src/main/resources/migrations.csv"
    val parseDefinitionMigrations by registering(JavaExec::class) {
        group = "generate"
        description = "Parse oga-maven-plugin and generate a CSV file."
        mainClass = "org.openrewrite.java.dependencies.oldgroupids.ParseDefinitionMigrations"
        classpath = sourceSets.getByName("main").runtimeClasspath
    }
}

license {
    exclude("**/*.json")
}
