package owl.ltl.rewriter;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierFactory.Mode;
import owl.run.PipelineExecutionContext;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformers;

public final class SimplifierTransformer extends Transformers.SimpleTransformer {
  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("simplify-ltl")
    .description("Rewrites / simplifies LTL formulas")
    .optionsBuilder(() -> {
      Option modeOption = new Option("m", "mode", true, "Specify the rewrites to be applied by a "
        + "comma separated list. Possible values are: simple, fairness, fixpoint. By default, "
        + "the \"fixpoint\" mode is chosen.");
      modeOption.setRequired(false);
      modeOption.setArgs(Option.UNLIMITED_VALUES);
      modeOption.setValueSeparator(',');
      return new Options().addOption(modeOption);
    }).parser(settings -> {
      if (!settings.hasOption("mode")) {
        return new SimplifierTransformer(List.of(Mode.SYNTACTIC_FIXPOINT));
      }

      String[] modes = settings.getOptionValues("mode");
      List<Mode> rewrites = new ArrayList<>(modes.length);

      for (String mode : modes) {
        rewrites.add(parseMode(mode));
      }

      return new SimplifierTransformer(rewrites);
    }).build();

  private final List<Mode> rewrites;

  private SimplifierTransformer(List<Mode> rewrites) {
    this.rewrites = ImmutableList.copyOf(rewrites);
  }

  private static Mode parseMode(String mode) throws ParseException {
    switch (mode) {
      case "simple":
        return Mode.SYNTACTIC;
      case "fixpoint":
        return Mode.SYNTACTIC_FIXPOINT;
      case "fairness":
        return Mode.SYNTACTIC_FAIRNESS;
      default:
        throw new ParseException("Unknown mode " + mode);
    }
  }

  @Override
  public Object transform(Object object, PipelineExecutionContext context) {
    checkArgument(object instanceof LabelledFormula);
    LabelledFormula result = (LabelledFormula) object;
    for (Mode rewrite : rewrites) {
      result = SimplifierFactory.apply(result, rewrite);
    }
    return result;
  }
}
