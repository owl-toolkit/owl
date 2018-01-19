package owl.run.modules;

import static owl.run.modules.OwlModuleParser.TransformerParser;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.arena.Views;
import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.automaton.transformations.RabinDegeneralization;
import owl.ltl.rewriter.RewriterTransformer;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.run.modules.OwlModuleParser.ReaderParser;
import owl.run.modules.OwlModuleParser.WriterParser;
import owl.run.parser.PipelineParser;
import owl.translations.ExternalTranslator;
import owl.translations.LTL2DA;
import owl.translations.delag.DelagBuilder;
import owl.translations.dra2dpa.IARBuilder;
import owl.translations.ltl2dpa.LTL2DPACliParser;
import owl.translations.ltl2ldba.LTL2LDBACliParser;
import owl.translations.nba2dpa.NBA2DPAFunction;
import owl.translations.nba2ldba.NBA2LDBA;
import owl.translations.rabinizer.RabinizerCliParser;

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

  static {
    DEFAULT_REGISTRY = new OwlModuleRegistry();

    // I/O
    DEFAULT_REGISTRY.register(
      InputReaders.LTL_CLI_PARSER, InputReaders.HOA_CLI_PARSER, InputReaders.TLSF_CLI_PARSER,
      OutputWriters.TO_STRING_CLI_PARSER, OutputWriters.AUTOMATON_STATS_CLI_PARSER,
      OutputWriters.NULL_CLI_PARSER, OutputWriters.HOA_CLI_PARSER);

    // Transformer
    DEFAULT_REGISTRY.register(RewriterTransformer.CLI_PARSER, Views.CLI_PARSER,
      ImplicitMinimizeTransformer.CLI_PARSER, RabinDegeneralization.CLI_PARSER,
      UnabbreviateVisitor.CLI_PARSER);

    // Advanced constructions
    DEFAULT_REGISTRY.register(RabinizerCliParser.INSTANCE, IARBuilder.CLI_PARSER,
      DelagBuilder.CLI_PARSER, LTL2LDBACliParser.INSTANCE, LTL2DA.CLI_PARSER, NBA2LDBA.CLI_PARSER,
      LTL2DPACliParser.INSTANCE, NBA2DPAFunction.CLI_PARSER, ExternalTranslator.CLI_PARSER);
  }

  private final Table<Type, String, OwlModuleParser<?>> registeredModules = HashBasedTable.create();

  public ReaderParser getReaderParser(String name) throws OwlModuleNotFoundException {
    return (ReaderParser) getWithType(Type.READER, name);
  }

  public Collection<OwlModuleParser<?>> getSettings(Type type) {
    return registeredModules.row(type).values();
  }

  public TransformerParser getTransformerParser(String name) throws OwlModuleNotFoundException {
    return (TransformerParser) getWithType(Type.TRANSFORMER, name);
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

  public WriterParser getWriterParser(String name) throws OwlModuleNotFoundException {
    return (WriterParser) getWithType(Type.WRITER, name);
  }

  public void register(OwlModuleParser<?>... parser) {
    Stream.of(parser).forEach(this::register);
  }

  public void register(OwlModuleParser<?> parser) {
    Collection<Type> types = Type.getTypes(parser);
    if (types.isEmpty()) {
      throw new IllegalArgumentException("Unknown settings type " + parser.getClass());
    }

    String name = parser.getKey();
    for (Type type : types) {
      if (registeredModules.contains(type, name)) {
        throw new IllegalArgumentException(
          String.format("Some module with name %s and type %s is already registered", name, type));
      }
    }

    types.forEach(type -> registeredModules.put(type, name, parser));
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

    public static Collection<Type> getTypes(OwlModuleParser<?> object) {
      return Arrays.stream(Type.values())
        .filter(type -> type.typeClass.isInstance(object))
        .collect(Collectors.toList());
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
}
