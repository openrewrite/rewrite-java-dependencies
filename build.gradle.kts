plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A rewrite module automating Java dependency management."

val parseDefinitionMigrations: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations {
    named(parseDefinitionMigrations.implementationConfigurationName) { extendsFrom(implementation.get()) }
    named(parseDefinitionMigrations.compileOnlyConfigurationName) { extendsFrom(compileOnly.get()) }
    named(parseDefinitionMigrations.annotationProcessorConfigurationName) { extendsFrom(annotationProcessor.get()) }
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-groovy")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")

    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite.gradle.tooling:model:${rewriteVersion}")
    testImplementation(gradleApi())
    testImplementation(parseDefinitionMigrations.output)

    testRuntimeOnly("org.openrewrite:rewrite-java-21")
    testRuntimeOnly("com.google.guava:guava:33.7.2-jre")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.2.13")
    // For FindDuplicateClasses tests - logback and slf4j-nop both define SLF4J binding classes
    testRuntimeOnly("org.slf4j:slf4j-nop:1.7.36")
}

tasks {
    // ./gradlew parseDefinitionMigrations --args="./oga-maven-plugin src/main/resources/migrations.csv"
    val parseDefinitionMigrations by registering(JavaExec::class) {
        group = "generate"
        description = "Parse oga-maven-plugin and generate a CSV file."
        mainClass = "org.openrewrite.java.dependencies.oldgroupids.ParseDefinitionMigrations"
        classpath = sourceSets.getByName("parseDefinitionMigrations").runtimeClasspath
    }

    // ./gradlew updateMigrations
    val updateMigrations by registering(JavaExec::class) {
        group = "generate"
        description = "Refresh src/main/resources/migrations.csv from the latest oga-maven-plugin definitions."
        mainClass = "org.openrewrite.java.dependencies.oldgroupids.UpdateMigrations"
        classpath = sourceSets.getByName("parseDefinitionMigrations").runtimeClasspath
        args(layout.buildDirectory.dir("oga-maven-plugin").get().asFile, "src/main/resources/migrations.csv")
    }
}

license {
    exclude("**/*.json")
}
