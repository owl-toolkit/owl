package owl.run.modules;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.run.modules.OwlModuleParser.TransformerParser;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.automaton.transformations.ParityUtil;
import owl.automaton.transformations.RabinDegeneralization;
import owl.game.GameUtil;
import owl.game.GameViews;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.ltl.robust.RobustLtlInputReader;
import owl.run.modules.OwlModuleParser.ReaderParser;
import owl.run.modules.OwlModuleParser.WriterParser;
import owl.run.parser.PipelineParser;
import owl.translations.ExternalTranslator;
import owl.translations.LTL2DAModule;
import owl.translations.delag.DelagBuilder;
import owl.translations.dra2dpa.IARBuilder;
import owl.translations.fgx2dpa.FGX2DPA;
import owl.translations.ltl2dpa.LTL2DPACliParser;
import owl.translations.ltl2dra.LTL2DRACliParser;
import owl.translations.ltl2ldba.LTL2LDBACliParser;
import owl.translations.nba2dpa.NBA2DPAFunction;
import owl.translations.nba2ldba.NBA2LDBA;
import owl.translations.ncsb.NCSBFunction;
import owl.translations.rabinizer.RabinizerCliParser;
import owl.translations.safra.SafraBuilder;

/**
 * A registry holding all modules used to parse the command line. These can be dynamically
 * registered to allow for flexible parsing of command lines.
 *
 * @see PipelineParser
 */
public class OwlModuleRegistry {
  /**
   * A preconfigured {@link OwlModuleRegistry registry}, holding commonly used utility modules.
   */
  public static final OwlModuleRegistry DEFAULT_REGISTRY;

  private final Table<Type, String, OwlModuleParser<?>> registeredModules = HashBasedTable.create();

  static {
    DEFAULT_REGISTRY = new OwlModuleRegistry();

    // I/O
    DEFAULT_REGISTRY.register(InputReaders.LTL_CLI, InputReaders.HOA_CLI, InputReaders.TLSF_CLI,
      OutputWriters.TO_STRING_CLI, OutputWriters.AUTOMATON_STATS_CLI, OutputWriters.NULL_CLI,
      OutputWriters.HOA_CLI, GameUtil.PG_SOLVER_CLI, RobustLtlInputReader.INSTANCE);

    // Transformer
    DEFAULT_REGISTRY.register(SimplifierTransformer.CLI, GameViews.AUTOMATON_TO_GAME_CLI,
      ImplicitMinimizeTransformer.CLI, RabinDegeneralization.CLI, Transformers.LTL_NEGATE_CLI);

    // Advanced constructions
    DEFAULT_REGISTRY.register(RabinizerCliParser.INSTANCE, FGX2DPA.CLI, IARBuilder.CLI,
      LTL2DAModule.CLI, NBA2LDBA.CLI, LTL2LDBACliParser.INSTANCE, LTL2DPACliParser.INSTANCE,
      DelagBuilder.CLI, NBA2DPAFunction.CLI, ExternalTranslator.CLI, ParityUtil.COMPLEMENT_CLI,
      ParityUtil.CONVERSION_CLI, LTL2DRACliParser.INSTANCE, SafraBuilder.CLI,
      NCSBFunction.CLI);

  }

  public ReaderParser reader(String name) throws OwlModuleNotFoundException {
    return (ReaderParser) getWithType(Type.READER, name);
  }

  public TransformerParser transformer(String name) throws OwlModuleNotFoundException {
    if (registeredModules.contains(Type.TRANSFORMER, name)) {
      return (TransformerParser) getWithType(Type.TRANSFORMER, name);
    }
    if (registeredModules.contains(Type.WRITER, name)) {
      WriterParser parser = (WriterParser) getWithType(Type.WRITER, name);
      return new AsTransformer(parser);
    }
    throw new OwlModuleNotFoundException(Type.TRANSFORMER, name);
  }

  public WriterParser writer(String name) throws OwlModuleNotFoundException {
    return (WriterParser) getWithType(Type.WRITER, name);
  }

  public Collection<OwlModuleParser<?>> getAllOfType(Type type) {
    return registeredModules.row(type).values();
  }

  public Map<Type, OwlModuleParser<?>> getAllWithName(String name) {
    return registeredModules.columnMap().get(name);
  }

  private OwlModuleParser<?> getWithType(Type type, String name) throws OwlModuleNotFoundException {
    @Nullable
    OwlModuleParser<?> owlModuleParser = registeredModules.get(type, name);
    if (owlModuleParser == null) {
      throw new OwlModuleNotFoundException(type, name);
    }
    assert type.typeClass.isInstance(owlModuleParser);
    return owlModuleParser;
  }

  public void register(OwlModuleParser<?>... parser) {
    Stream.of(parser).forEach(this::register);
  }

  public void register(OwlModuleParser<?> parser) {
    Type type = Type.of(parser);

    String name = parser.getKey();
    if (registeredModules.contains(type, name)) {
      throw new IllegalArgumentException(
        String.format("Some module with name %s and type %s is already registered", name, type));
    }

    registeredModules.put(type, name, parser);
  }

  public Set<OwlModuleParser<?>> remove(String name) {
    // Column map isn't really efficient but we have very few row keys anyway
    return new HashSet<>(registeredModules.columnMap().remove(name).values());
  }

  public enum Type {
    READER(ReaderParser.class, "reader"), WRITER(WriterParser.class, "writer"),
    TRANSFORMER(TransformerParser.class, "transformer");

    public final Class<?> typeClass;
    public final String name;

    Type(Class<?> typeClass, String name) {
      this.typeClass = typeClass;
      this.name = name;
    }

    public static Type of(OwlModuleParser<?> object) {
      List<Type> types = Arrays.stream(Type.values())
        .filter(type -> type.typeClass.isInstance(object))
        .collect(Collectors.toList());
      checkArgument(types.size() == 1);
      return Iterables.getOnlyElement(types);
    }
  }

  public static class OwlModuleNotFoundException extends Exception {
    public final OwlModuleRegistry.Type type;
    public final String name;

    OwlModuleNotFoundException(OwlModuleRegistry.Type type, String name) {
      this.type = type;
      this.name = name;
    }
  }

  private static class AsTransformer implements TransformerParser {
    private final WriterParser parser;

    public AsTransformer(WriterParser parser) {
      this.parser = parser;
    }

    @Override
    public String getKey() {
      return parser.getKey();
    }

    @Override
    public String getDescription() {
      return parser.getDescription();
    }

    @Override
    public Options getOptions() {
      return parser.getOptions();
    }

    @Override
    public Transformer parse(CommandLine commandLine) throws ParseException {
      return Transformers.fromWriter(parser.parse(commandLine));
    }
  }
}
