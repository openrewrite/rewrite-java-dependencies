package org.openrewrite.java.dependencies;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class RemoveUnusedDependenciesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedDependencies());
    }

    // The test framework does not populate the JavaSourceSet marker with dependencies
    private static final JavaSourceSet jssWithGuava = JavaSourceSet.build("main",
      JavaParser.dependenciesFromClasspath("guava"));

    @DocumentExample
    @Test
    void mavenRemoveUnused() {
        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.mycompany</groupId>
                    <artifactId>app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.google.guava</groupId>
                            <artifactId>guava</artifactId>
                            <version>29.0-jre</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            //language=java
            srcMainJava(
              java(
                """
                  import java.util.List;
                  import java.util.ArrayList;
                  
                  public class A {
                      List<String> a = new ArrayList<>();
                  }
                  """,
                spec -> spec.markers(jssWithGuava)
              )
            )
          )
        );
    }
}
