package owl.run.meta;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.consumer.HOAIntermediateStoreAndManipulate;
import jhoafparser.storage.StoredAutomatonManipulator;
import jhoafparser.transformations.ToStateAcceptance;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import owl.automaton.output.HoaPrintable;
import owl.automaton.output.HoaPrintable.HoaOption;
import owl.cli.ImmutableMetaSettings;
import owl.run.env.Environment;
import owl.run.output.OutputWriter;
import owl.run.transformer.Transformer;

/**
 * Converts any {@link HoaPrintable HOA printable} object to its corresponding <a
 * href="http://adl.github.io/hoaf/">HOA</a> representation.
 */
public class ToHoa implements OutputWriter.Factory, Transformer.Factory {
  public static final ImmutableMetaSettings<ToHoa> settings =
    ImmutableMetaSettings.<ToHoa>builder()
      .key("hoa")
      .description("Writes the HOA format representation of an automaton or an arena")
      .options(new Options()
        .addOption("s", "state-acceptance", false, "Convert to state-acceptance")
        .addOption(null, "simple-trans", false, "Force use of simple transition labels, resulting "
          + "in 2^AP edges per state)")
        .addOption("v", "validate", false, "Validate output"))
      .metaSettingsParser(ToHoa::parseSettings)
      .build();

  private final Set<Setting> hoaSettings;
  private final List<StoredAutomatonManipulator> manipulations;

  public ToHoa() {
    this(EnumSet.noneOf(Setting.class));
  }

  public ToHoa(EnumSet<Setting> hoaSettings, List<StoredAutomatonManipulator> manipulations) {
    this.hoaSettings = ImmutableSet.copyOf(hoaSettings);
    this.manipulations = ImmutableList.copyOf(manipulations);
  }

  public ToHoa(EnumSet<Setting> hoaSettings, StoredAutomatonManipulator... manipulations) {
    this.hoaSettings = ImmutableSet.copyOf(hoaSettings);
    this.manipulations = ImmutableList.copyOf(manipulations);
  }

  private EnumSet<HoaOption> getOptions(boolean annotations) {
    EnumSet<HoaOption> options = EnumSet.noneOf(HoaOption.class);
    if (annotations) {
      options.add(HoaOption.ANNOTATIONS);
    }
    if (hoaSettings.contains(Setting.SIMPLE_TRANSITION_LABELS)) {
      options.add(HoaOption.SIMPLE_TRANSITION_LABELS);
    }
    return options;
  }

  @Override
  public Transformer createTransformer(Environment environment) {
    //noinspection resource
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    HOAConsumer consumer = getConsumer(bos);
    return (input, context) -> {
      checkArgument(input instanceof HoaPrintable);
      ((HoaPrintable) input).toHoa(consumer, getOptions(true));
      context.addMetaInformation(new String(bos.toByteArray(), environment.charset()));
      bos.reset();
      return input;
    };
  }

  @Override
  public OutputWriter createWriter(OutputStream stream, Environment environment) {
    HOAConsumer consumer = getConsumer(stream);
    return object -> {
      checkArgument(object instanceof HoaPrintable);
      ((HoaPrintable) object).toHoa(consumer, getOptions(environment.annotations()));
    };
  }

  private HOAConsumer getConsumer(OutputStream stream) {
    HOAConsumerPrint printer = new HOAConsumerPrint(stream);

    HOAConsumer consumer;
    if (manipulations.isEmpty()) {
      consumer = printer;
    } else {
      StoredAutomatonManipulator[] manipulators =
        manipulations.toArray(new StoredAutomatonManipulator[manipulations.size()]);
      consumer = new HOAIntermediateStoreAndManipulate(printer, manipulators);
    }

    return hoaSettings.contains(Setting.CHECK_VALIDITY)
      ? new HOAIntermediateCheckValidity(consumer)
      : consumer;
  }

  private static ToHoa parseSettings(CommandLine settings) {
    ImmutableList.Builder<StoredAutomatonManipulator> builder = ImmutableList.builder();
    if (settings.hasOption("state-acceptance")) {
      builder.add(new ToStateAcceptance());
    }
    EnumSet<Setting> hoaSettings = EnumSet.noneOf(Setting.class);
    if (settings.hasOption("validate")) {
      hoaSettings.add(Setting.CHECK_VALIDITY);
    }
    if (settings.hasOption("simple-trans")) {
      hoaSettings.add(Setting.SIMPLE_TRANSITION_LABELS);
    }
    return new ToHoa(hoaSettings, builder.build());
  }

  public enum Setting {
    CHECK_VALIDITY, SIMPLE_TRANSITION_LABELS
  }
}