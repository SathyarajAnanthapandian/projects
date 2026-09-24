package com.example.dataflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.not;

import org.junit.Test;

public class EnvironmentConfigTest {

  @Test
  public void noEnvPassesArgsThrough() {
    String[] args = {"--outputTopic=projects/p/topics/t", "--inputTable=p:d.t"};
    assertThat(EnvironmentConfig.resolveArgs(args, null), arrayContaining(args));
  }

  @Test
  public void envLoadsCommonAndEnvironmentFiles() {
    String[] resolved = EnvironmentConfig.resolveArgs(new String[] {"--env=dev"}, null);
    assertThat(resolved, hasItemInArray("--runner=DirectRunner"));
    assertThat(resolved, hasItemInArray("--outputTopic=projects/my-dev-project/topics/my-topic"));
    assertThat(resolved, not(hasItemInArray("--env=dev")));
  }

  @Test
  public void commandLineOverridesFile() {
    String[] resolved =
        EnvironmentConfig.resolveArgs(
            new String[] {"--env=qa", "--outputTopic=projects/x/topics/override"}, null);
    assertThat(resolved, hasItemInArray("--outputTopic=projects/x/topics/override"));
    assertThat(
        resolved, not(hasItemInArray("--outputTopic=projects/my-qa-project/topics/my-topic")));
  }

  @Test
  public void envVariableSelectsEnvironment() {
    String[] resolved = EnvironmentConfig.resolveArgs(new String[0], "prod");
    assertThat(resolved, hasItemInArray("--project=my-prod-project"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void unknownEnvironmentFails() {
    EnvironmentConfig.resolveArgs(new String[] {"--env=nope"}, null);
  }
}
