package owl.run;

import static owl.run.ModuleSettings.TransformerSettings;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.arena.Views;
import owl.arena.algorithms.ParityGameSolver;
import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.automaton.transformations.RabinDegeneralization;
import owl.ltl.rewriter.RewriterTransformer;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.run.ModuleSettings.ReaderSettings;
import owl.run.ModuleSettings.WriterSettings;
import owl.run.env.EnvironmentSettings;
import owl.translations.ExternalTranslator;
import owl.translations.LTL2DA;
import owl.translations.delag.DelagBuilder;
import owl.translations.dra2dpa.IARBuilder;
import owl.translations.ltl2dpa.LTL2DPAModule;
import owl.translations.ltl2ldba.LTL2LDBAModule;
import owl.translations.nba2dpa.NBA2DPAModule;
import owl.translations.nba2ldba.NBA2LDBAModule;
import owl.translations.rabinizer.RabinizerModule;

/**
 * A registry holding all modules used to parse the command line. These can be dynamically
 * registered to allow for flexible parsing of command lines.
 *
 * @see owl.run.parser.CliParser
 */
public class CommandLineRegistry {
  /**
   * A preconfigured {@link CommandLineRegistry registry}, holding commonly used utility modules.
   */
  public static final CommandLineRegistry DEFAULT_REGISTRY;

  static {
    DEFAULT_REGISTRY = new CommandLineRegistry(new EnvironmentSettings());

    // I/O
    DEFAULT_REGISTRY.register(InputReaders.LTL, InputReaders.HOA, InputReaders.TLSF,
      OutputWriters.STRING, OutputWriters.AUTOMATON_STATS, OutputWriters.NULL, OutputWriters.HOA);

    // Transformer
    DEFAULT_REGISTRY.register(RewriterTransformer.settings, ImplicitMinimizeTransformer.settings,
      RabinDegeneralization.settings, UnabbreviateVisitor.settings, Views.SETTINGS,
      new RabinizerModule(), IARBuilder.settings, DelagBuilder.settings,
      LTL2LDBAModule.INSTANCE, LTL2DA.settings, LTL2DPAModule.INSTANCE, new NBA2DPAModule(),
      new NBA2LDBAModule(), ExternalTranslator.settings, ParityGameSolver.SETTINGS);
  }

  private final EnvironmentSettings environmentSettings;
  private final Table<Type, String, ModuleSettings> registeredModules = HashBasedTable.create();

  public CommandLineRegistry(EnvironmentSettings environmentSettings) {
    this.environmentSettings = environmentSettings;
  }

  public Collection<TransformerSettings> getAllTransformerSettings() {
    return registeredModules.row(Type.TRANSFORMER).values().stream()
      .map(TransformerSettings.class::cast)
      .collect(Collectors.toSet());
  }

  public EnvironmentSettings getEnvironmentSettings() {
    return environmentSettings;
  }

  public Collection<ReaderSettings> getReaderSettings() {
    return registeredModules.row(Type.READER).values().stream()
      .map(ReaderSettings.class::cast)
      .collect(Collectors.toSet());
  }

  @Nullable
  public ReaderSettings getReaderSettings(String name) {
    return (ReaderSettings) getType(Type.READER, name);
  }

  @Nullable
  public TransformerSettings getTransformerSettings(String name) {
    return (TransformerSettings) getType(Type.TRANSFORMER, name);
  }

  @Nullable
  private ModuleSettings getType(Type type, String name) {
    @Nullable
    ModuleSettings moduleSettings = registeredModules.get(type, name);
    assert moduleSettings == null || type.typeClass.isInstance(moduleSettings);
    return moduleSettings;
  }

  public Collection<WriterSettings> getWriterSettings() {
    return registeredModules.row(Type.WRITER).values().stream()
      .map(WriterSettings.class::cast)
      .collect(Collectors.toSet());
  }

  @Nullable
  public WriterSettings getWriterSettings(String name) {
    return (WriterSettings) getType(Type.WRITER, name);
  }

  public void register(ModuleSettings... settings) {
    register(List.of(settings));
  }

  public void register(ModuleSettings description) {
    Collection<Type> types = Type.getTypes(description);
    if (types.isEmpty()) {
      throw new IllegalArgumentException("Unknown settings type " + description.getClass());
    }

    String name = description.getKey();
    for (Type type : types) {
      if (registeredModules.contains(type, name)) {
        throw new IllegalArgumentException(
          String.format("Some module with name %s and type %s is already registered", name, type));
      }
    }

    types.forEach(type -> registeredModules.put(type, name, description));
  }

  public void register(Collection<ModuleSettings> settings) {
    settings.forEach(this::register);
  }

  private enum Type {
    READER(ReaderSettings.class), WRITER(WriterSettings.class),
    TRANSFORMER(TransformerSettings.class);

    public final Class<?> typeClass;

    Type(Class<?> typeClass) {
      this.typeClass = typeClass;
    }

    public static Collection<Type> getTypes(ModuleSettings object) {
      return Arrays.stream(Type.values())
        .filter(type -> type.typeClass.isInstance(object))
        .collect(Collectors.toList());
    }
  }
}
