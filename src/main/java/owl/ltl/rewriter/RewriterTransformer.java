package owl.ltl.rewriter;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.run.PipelineExecutionContext;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformers;

public class RewriterTransformer extends Transformers.SimpleTransformer {
  public static final TransformerParser CLI_PARSER = ImmutableTransformerParser.builder()
    .key("rewrite")
    .optionsBuilder(() -> {
      Option modeOption = new Option("m", "mode", true, "Specify the rewrites to be applied by a "
        + "comma separated list. Possible values are: modal, modal-iter, pullup, pushdown, "
        + "fairness");
      modeOption.setRequired(true);
      modeOption.setArgs(Option.UNLIMITED_VALUES);
      modeOption.setValueSeparator(',');
      return new Options().addOption(modeOption);
    }).parser(settings -> {
      List<RewriterEnum> rewrites = new ArrayList<>();
      String[] modes = settings.getOptionValues("mode");

      for (String mode : modes) {
        rewrites.add(parseMode(mode));
      }

      return new RewriterTransformer(rewrites);
    })
    .build();

  private final List<RewriterEnum> rewrites;

  public RewriterTransformer(RewriterEnum... rewrites) {
    this.rewrites = ImmutableList.copyOf(rewrites);
  }

  public RewriterTransformer(List<RewriterEnum> rewrites) {
    this.rewrites = ImmutableList.copyOf(rewrites);
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
  public Object transform(Object object, PipelineExecutionContext context) {
    checkArgument(object instanceof LabelledFormula);
    LabelledFormula result = (LabelledFormula) object;
    for (RewriterEnum rewrite : rewrites) {
      result = RewriterFactory.apply(rewrite, result);
    }
    return result;
  }
}
