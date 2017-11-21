package owl.ltl.rewriter;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.run.ModuleSettings.TransformerSettings;
import owl.run.Transformer;
import owl.run.Transformers;
import owl.run.env.Environment;

public class RewriterTransformer {
  public static final TransformerSettings settings = new TransformerSettings() {
    @Override
    public Transformer create(CommandLine settings, Environment environment)
      throws ParseException {
      List<RewriterEnum> rewrites = new ArrayList<>();
      String[] modes = settings.getOptionValues("mode");

      for (String mode : modes) {
        rewrites.add(parseMode(mode));
      }

      RewriterTransformer transformer = new RewriterTransformer(rewrites);

      return Transformers.fromFunction(LabelledFormula.class, input -> {
        LabelledFormula result = input;
        for (RewriterEnum rewrite : transformer.rewrites) {
          result = RewriterFactory.apply(rewrite, result);
        }
        return result;
      });
    }

    @Override
    public String getKey() {
      return "rewrite";
    }

    @Override
    public Options getOptions() {
      return options();
    }
  };

  private final List<RewriterEnum> rewrites;

  public RewriterTransformer(RewriterEnum... rewrites) {
    this.rewrites = ImmutableList.copyOf(rewrites);
  }

  public RewriterTransformer(List<RewriterEnum> rewrites) {
    this.rewrites = ImmutableList.copyOf(rewrites);
  }

  private static Options options() {
    Option modeOption = new Option("m", "mode", true, "Specify the rewrites to be applied by a "
      + "comma separated list. Possible values are: modal, modal-iter, pullup, pushdown, fairness");
    modeOption.setRequired(true);
    modeOption.setArgs(Option.UNLIMITED_VALUES);
    modeOption.setValueSeparator(',');
    return new Options().addOption(modeOption);
  }

  private static RewriterEnum parseMode(String mode) throws ParseException {
    switch (mode) {
      case "modal":
        return RewriterEnum.MODAL;
      case "modal-iter":
        return RewriterEnum.MODAL_ITERATIVE;
      case "pullup":
        return RewriterEnum.PULLUP_X;
      case "pushdown":
        return RewriterEnum.PUSHDOWN_X;
      case "fairness":
        return RewriterEnum.FAIRNESS;
      default:
        throw new ParseException("Unknown mode " + mode);
    }
  }

}
