package owl.run.env;

import com.google.common.base.Strings;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class DefaultEnvironmentSettings implements EnvironmentSettings {
  @Override
  public Environment buildEnvironment(CommandLine settings) {
    String annotationsEnv = System.getenv("OWL_ANNOTATIONS");
    boolean annotationsFromEnv = !Strings.isNullOrEmpty(annotationsEnv)
      && !"0".equals(annotationsEnv);
    boolean annotations = annotationsFromEnv || settings.hasOption("annotations");
    boolean parallel = settings.hasOption("parallel");
    boolean metaInformation = !settings.hasOption("nometa");
    return DefaultEnvironment.of(annotations, parallel, metaInformation);
  }

  @Override
  public Options getOptions() {
    Option parallel = new Option("p", "parallel", false,
      "Enable parallel processing (where supported)");
    Option annotations = new Option("a", "annotations", false,
      "Gather additional labels etc. (where supported)");
    Option disableMeta = new Option("nm", "nometa", false,
      "Disable display of meta-information");

    Options options = new Options();
    options.addOption(parallel);
    options.addOption(annotations);
    options.addOption(disableMeta);
    return options;
  }
}
