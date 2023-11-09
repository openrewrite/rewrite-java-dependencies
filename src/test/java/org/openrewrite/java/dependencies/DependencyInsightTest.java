package org.openrewrite.java.dependencies;

import org.junit.jupiter.api.Test;
import org.openrewrite.maven.Assertions;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

public class DependencyInsightTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DependencyInsight("org.springframework*", "*"));
    }

    @Test
    void maven() {
        rewriteRun(
          //language=xml
          pomXml("""
              <project>
                <groupId></groupId>
                <artifactId></artifactId>
                <version></version>
                
                <dependencies>
                  <dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId>
                    <version>5.2.6.RELEASE</version>
                  </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId></groupId>
                <artifactId></artifactId>
                <version></version>
                
                <dependencies>
                  <!--~~>--><dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId>
                    <version>5.2.6.RELEASE</version>
                  </dependency>
                </dependencies>
              </project>
              """)
        );
    }
}
