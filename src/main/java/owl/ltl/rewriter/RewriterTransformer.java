package owl.ltl.rewriter;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.cli.ImmutableTransformerSettings;
import owl.cli.ModuleSettings.TransformerSettings;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.run.env.Environment;
import owl.run.transformer.Transformer;
import owl.run.transformer.Transformers;

public class RewriterTransformer implements Transformer.Factory {
  public static final TransformerSettings settings = ImmutableTransformerSettings.builder()
    .key("rewrite")
    .options(options())
    .transformerSettingsParser(settings -> {
      String[] modes = settings.getOptionValues("mode");
      List<RewriterEnum> rewrites = new ArrayList<>(modes.length);
      for (String mode : modes) {
        rewrites.add(parseMode(mode));
      }
      return new RewriterTransformer(rewrites);
    }).build();

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

  @Override
  public Transformer createTransformer(Environment environment) {
    return Transformers.fromFunction(LabelledFormula.class, input -> {
      LabelledFormula result = input;
      for (RewriterEnum rewrite : rewrites) {
        result = RewriterFactory.apply(rewrite, result);
      }
      return result;
    });
  }
}
