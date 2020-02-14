/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.run.modules;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import owl.automaton.ParityUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.degeneralization.BuchiDegeneralization;
import owl.automaton.acceptance.degeneralization.RabinDegeneralization;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.game.GameUtil;
import owl.game.GameViews;
import owl.game.algorithms.ParityGameSolver;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.ltl.robust.RobustLtlInputReader;
import owl.run.parser.PipelineParser;
import owl.translations.ExternalTranslator;
import owl.translations.delag.DelagBuilder;
import owl.translations.dra2dpa.IARBuilder;
import owl.translations.modules.LTL2DAModule;
import owl.translations.modules.LTL2DGRAModule;
import owl.translations.modules.LTL2DPAModule;
import owl.translations.modules.LTL2DRAModule;
import owl.translations.modules.LTL2LDBAModule;
import owl.translations.modules.LTL2LDGBAModule;
import owl.translations.modules.LTL2NAModule;
import owl.translations.modules.LTL2NBAModule;
import owl.translations.modules.LTL2NGBAModule;
import owl.translations.modules.LTL2NormalFormModule;
import owl.translations.nba2dpa.NBA2DPA;
import owl.translations.nba2ldba.NBA2LDBA;

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

  private final Map<String, OwlModule<OwlModule.InputReader>> readers = new HashMap<>();
  private final Map<String, OwlModule<OwlModule.Transformer>> transformers = new HashMap<>();
  private final Map<String, OwlModule<OwlModule.OutputWriter>> writers = new HashMap<>();

  static {
    DEFAULT_REGISTRY = new OwlModuleRegistry();

    // Input Modules
    DEFAULT_REGISTRY.putReaders(List.of(
      InputReaders.LTL_INPUT_MODULE,
      InputReaders.HOA_INPUT_MODULE,
      RobustLtlInputReader.RLTL_INPUT_MODULE));

    // Output Modules
    DEFAULT_REGISTRY.registerWriter(List.of(
      OutputWriters.TO_STRING_MODULE,
      OutputWriters.AUTOMATON_STATS_MODULE,
      OutputWriters.NULL_MODULE,
      OutputWriters.HOA_OUTPUT_MODULE,
      GameUtil.PG_SOLVER_OUTPUT_MODULE));

    // Transformers
    DEFAULT_REGISTRY.putTransformers(List.of(
      SimplifierTransformer.MODULE,
      GameViews.AUTOMATON_TO_GAME_MODULE,
      AcceptanceOptimizations.MODULE,
      BuchiDegeneralization.MODULE,
      RabinDegeneralization.MODULE,
      Views.COMPLETE_MODULE));

    // LTL translations
    DEFAULT_REGISTRY.putTransformers(List.of(
      // -> N(G)BA
      LTL2NBAModule.MODULE, LTL2NGBAModule.MODULE,
      // -> LD(G)BA
      LTL2LDBAModule.MODULE, LTL2LDGBAModule.MODULE,
      // -> D(G)RA
      LTL2DRAModule.MODULE, LTL2DGRAModule.MODULE,
      // -> DPA
      LTL2DPAModule.MODULE,
      // -> DELA
      LTL2DAModule.MODULE, DelagBuilder.MODULE,
      // -> NELA
      LTL2NAModule.MODULE,
      // external
      ExternalTranslator.MODULE,
      // -> Delta_2 normal form
      LTL2NormalFormModule.MODULE));

    // Automaton translations
    DEFAULT_REGISTRY.putTransformers(List.of(
      IARBuilder.MODULE,
      NBA2LDBA.MODULE,
      NBA2DPA.MODULE,
      ParityUtil.COMPLEMENT_MODULE,
      ParityUtil.CONVERSION_MODULE,
      ParityGameSolver.ZIELONKA_SOLVER));
  }

  public OwlModule<OwlModule.InputReader> getReader(String name)
    throws OwlModuleNotFoundException {
    @Nullable
    var module = readers.get(name);

    if (module == null) {
      throw new OwlModuleNotFoundException(Type.READER, name);
    }

    return module;
  }

  public OwlModule<OwlModule.Transformer> getTransformer(String name)
    throws OwlModuleNotFoundException {
    @Nullable
    var module = transformers.get(name);

    if (module == null) {
      throw new OwlModuleNotFoundException(Type.TRANSFORMER, name);
    }

    return module;
  }

  public OwlModule<OwlModule.OutputWriter> getWriter(String name)
    throws OwlModuleNotFoundException {
    @Nullable
    var module = writers.get(name);

    if (module == null) {
      throw new OwlModuleNotFoundException(Type.WRITER, name);
    }

    return module;
  }

  public Collection<OwlModule<?>> get(Type type) {
    switch (type) {
      case READER:
        return (Collection) readers.values();

      case WRITER:
        return (Collection) writers.values();

      case TRANSFORMER:
        return (Collection) transformers.values();

      default:
        throw new AssertionError("Unreachable.");
    }
  }

  public Map<Type, OwlModule<?>> get(String name) {
    Map<Type, OwlModule<?>> types = new HashMap<>();

    var reader = readers.get(name);

    if (reader != null) {
      types.put(Type.READER, reader);
    }

    var transformer = transformers.get(name);

    if (transformer != null) {
      types.put(Type.TRANSFORMER, reader);
    }

    var writer = transformers.get(name);

    if (writer != null) {
      types.put(Type.WRITER, reader);
    }

    return types;
  }

  public Type type(OwlModule<?> object) {
    var typeMap = get(object.key());
    return typeMap.keySet().iterator().next();
  }

  public void putReaders(List<OwlModule<OwlModule.InputReader>> modules) {
    modules.forEach(module -> put(readers, module));
  }

  public void putTransformers(List<OwlModule<OwlModule.Transformer>> modules) {
    modules.forEach(module -> put(transformers, module));
  }

  public void registerWriter(List<OwlModule<OwlModule.OutputWriter>> modules) {
    modules.forEach(module -> put(writers, module));
  }

  private <M extends OwlModule.Instance> void put(
    Map<String, OwlModule<M>> map, OwlModule<M> module) {
    String key = module.key();

    if (map.containsKey(key)) {
      throw new IllegalArgumentException(
        String.format("Module with name %s already registered", module.key()));
    }

    map.put(key, module);
  }

  public enum Type {
    READER, TRANSFORMER, WRITER;
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
