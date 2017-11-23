package owl.run.modules;

import static owl.run.modules.ModuleSettings.TransformerSettings;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.arena.Views;
import owl.automaton.minimizations.ImplicitMinimizeTransformer;
import owl.automaton.transformations.RabinDegeneralization;
import owl.ltl.rewriter.RewriterTransformer;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.run.modules.ModuleSettings.ReaderSettings;
import owl.run.modules.ModuleSettings.WriterSettings;
import owl.run.parser.PipelineParser;
import owl.translations.ExternalTranslator;
import owl.translations.LTL2DA;
import owl.translations.delag.DelagBuilder;
import owl.translations.dra2dpa.IARBuilder;
import owl.translations.ltl2dpa.LTL2DPAModule;
import owl.translations.ltl2ldba.LTL2LDBAModule;
import owl.translations.nba2dpa.NBA2DPAFunction;
import owl.translations.nba2ldba.NBA2LDBA;
import owl.translations.rabinizer.RabinizerModule;

/**
 * A registry holding all modules used to parse the command line. These can be dynamically
 * registered to allow for flexible parsing of command lines.
 *
 * @see PipelineParser
 */
public class ModuleRegistry {
  /**
   * A preconfigured {@link ModuleRegistry registry}, holding commonly used utility modules.
   */
  public static final ModuleRegistry DEFAULT_REGISTRY;
  private final Table<Type, String, ModuleSettings<?>> registeredModules = HashBasedTable.create();

  static {
    DEFAULT_REGISTRY = new ModuleRegistry();

    // I/O
    DEFAULT_REGISTRY.register(InputReaders.LTL_SETTINGS, InputReaders.HOA_SETTINGS,
      InputReaders.TLSF_SETTINGS, OutputWriters.TO_STRING_SETTINGS,
      OutputWriters.AUTOMATON_STATS_SETTINGS, OutputWriters.NULL_SETTINGS,
      OutputWriters.HOA_SETTINGS);

    // Transformer
    DEFAULT_REGISTRY.register(RewriterTransformer.SETTINGS, ImplicitMinimizeTransformer.SETTINGS,
      RabinDegeneralization.SETTINGS, UnabbreviateVisitor.SETTINGS,
      Views.AUTOMATON_TO_ARENA_SETTINGS);

    // Advanced constructions
    DEFAULT_REGISTRY.register(new RabinizerModule(), IARBuilder.SETTINGS, DelagBuilder.SETTINGS,
      LTL2LDBAModule.INSTANCE, LTL2DA.SETTINGS, LTL2DPAModule.INSTANCE, NBA2DPAFunction.SETTINGS,
      NBA2LDBA.SETTINGS, ExternalTranslator.SETTINGS);
  }

  public Collection<ModuleSettings<?>> getSettings(Type type) {
    return registeredModules.row(type).values();
  }

  public ReaderSettings getReaderSettings(String name) throws ModuleNotFoundException {
    return (ReaderSettings) getWithType(Type.READER, name);
  }

  public WriterSettings getWriterSettings(String name) throws ModuleNotFoundException {
    return (WriterSettings) getWithType(Type.WRITER, name);
  }

  public TransformerSettings getTransformerSettings(String name) throws ModuleNotFoundException {
    return (TransformerSettings) getWithType(Type.TRANSFORMER, name);
  }

  private ModuleSettings<?> getWithType(Type type, String name) throws ModuleNotFoundException {
    @Nullable
    ModuleSettings<?> moduleSettings = registeredModules.get(type, name);
    if (moduleSettings == null) {
      throw new ModuleNotFoundException(type, name);
    }
    assert type.typeClass.isInstance(moduleSettings);
    return moduleSettings;
  }

  public void register(ModuleSettings<?>... settings) {
    register(List.of(settings));
  }

  public void register(ModuleSettings<?> description) {
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

  public void register(Collection<ModuleSettings<?>> settings) {
    settings.forEach(this::register);
  }

  public Set<ModuleSettings<?>> remove(String name) {
    // Column map isn't really efficient but we have very few row keys anyway
    return new HashSet<>(registeredModules.columnMap().remove(name).values());
  }

  public enum Type {
    READER(ReaderSettings.class, "reader"), WRITER(WriterSettings.class, "writer"),
    TRANSFORMER(TransformerSettings.class, "transformer");

    public final Class<?> typeClass;
    public final String name;

    Type(Class<?> typeClass, String name) {
      this.typeClass = typeClass;
      this.name = name;
    }

    public static Collection<Type> getTypes(ModuleSettings<?> object) {
      return Arrays.stream(Type.values())
        .filter(type -> type.typeClass.isInstance(object))
        .collect(Collectors.toList());
    }
  }

  public static class ModuleNotFoundException extends Exception {
    public final ModuleRegistry.Type type;
    public final String name;

    public ModuleNotFoundException(ModuleRegistry.Type type, String name) {
      this.type = type;
      this.name = name;
    }
  }
}
