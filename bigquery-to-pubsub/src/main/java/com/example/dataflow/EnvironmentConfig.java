package com.example.dataflow;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns per-environment properties files into pipeline arguments.
 *
 * <p>The environment is chosen with {@code --env=<name>} (or the {@code APP_ENV} environment
 * variable). Settings are loaded from {@code application.properties} and then {@code
 * application-<name>.properties} on the classpath (src/main/resources), the second overriding the
 * first. Each property {@code key=value} becomes {@code --key=value}. Arguments given on the command
 * line always win over the files.
 *
 * <p>With no environment selected, the arguments are passed through unchanged.
 */
final class EnvironmentConfig {

  static final String ENV_ARG = "--env=";
  static final String ENV_VAR = "APP_ENV";

  private EnvironmentConfig() {}

  static String[] resolveArgs(String[] args) {
    return resolveArgs(args, System.getenv(ENV_VAR));
  }

  static String[] resolveArgs(String[] args, String envFromVariable) {
    String env = envFromVariable;
    List<String> cliArgs = new ArrayList<>();
    for (String arg : args) {
      if (arg.startsWith(ENV_ARG)) {
        env = arg.substring(ENV_ARG.length());
      } else {
        cliArgs.add(arg);
      }
    }
    if (env == null || env.trim().isEmpty()) {
      return cliArgs.toArray(new String[0]);
    }

    Properties props = new Properties();
    load(props, "application.properties", false);
    load(props, "application-" + env.trim() + ".properties", true);

    Set<String> cliKeys = new HashSet<>();
    for (String arg : cliArgs) {
      cliKeys.add(keyOf(arg));
    }

    List<String> merged = new ArrayList<>();
    for (String key : new TreeSet<>(props.stringPropertyNames())) {
      String value = props.getProperty(key).trim();
      if (!value.isEmpty() && !cliKeys.contains(key)) {
        merged.add("--" + key + "=" + value);
      }
    }
    merged.addAll(cliArgs);
    return merged.toArray(new String[0]);
  }

  private static String keyOf(String arg) {
    String s = arg.startsWith("--") ? arg.substring(2) : arg;
    int eq = s.indexOf('=');
    return eq >= 0 ? s.substring(0, eq) : s;
  }

  private static void load(Properties props, String resource, boolean required) {
    try (InputStream in = EnvironmentConfig.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        if (required) {
          throw new IllegalArgumentException(
              "No " + resource + " on the classpath. Add it to src/main/resources.");
        }
        return;
      }
      props.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + resource, e);
    }
  }
}
