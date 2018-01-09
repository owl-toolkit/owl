package owl.automaton.minimizations;

import static com.google.common.base.Preconditions.checkArgument;

import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.automaton.MutableAutomaton;
import owl.run.ModuleSettings.TransformerSettings;
import owl.run.PipelineExecutionContext;
import owl.run.Transformer;
import owl.run.env.Environment;

public class ImplicitMinimizeTransformer implements Transformer {
  public static final TransformerSettings settings = new TransformerSettings() {
    @Override
    public Transformer create(CommandLine settings, Environment environment)
      throws ParseException {
      String levelString = settings.getOptionValue("level");
      @Nullable
      MinimizationUtil.MinimizationLevel level = getLevel(levelString);

      if (level == null) {
        throw new ParseException("Invalid value for \"level\": " + levelString);
      }

      return new ImplicitMinimizeTransformer(level);
    }

    @Override
    public String getKey() {
      return "minimize-aut";
    }

    @Override
    public Options getOptions() {
      return new Options()
        .addOption("l", "level", true, "Level of minimization (light,medium,all)");
    }
  };

  private final MinimizationUtil.MinimizationLevel level;

  public ImplicitMinimizeTransformer() {
    this(MinimizationUtil.MinimizationLevel.ALL);
  }

  public ImplicitMinimizeTransformer(MinimizationUtil.MinimizationLevel level) {
    this.level = level;
  }

  @Nullable
  private static MinimizationUtil.MinimizationLevel getLevel(@Nullable String string) {
    if (string == null) {
      return MinimizationUtil.MinimizationLevel.ALL;
    }
    switch (string) {
      case "light":
        return MinimizationUtil.MinimizationLevel.LIGHT;
      case "medium":
        return MinimizationUtil.MinimizationLevel.MEDIUM;
      case "all":
        return MinimizationUtil.MinimizationLevel.ALL;
      default:
        return null;
    }
  }

  @Override
  public Object transform(Object object, PipelineExecutionContext context) {
    checkArgument(object instanceof MutableAutomaton, "Expected mutable automaton, got %s",
      object.getClass());
    MutableAutomaton<?, ?> automaton = (MutableAutomaton<?, ?>) object;
    MinimizationUtil.minimizeDefault(automaton, level);
    return automaton;
  }
}
