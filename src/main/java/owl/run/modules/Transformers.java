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

import static com.google.common.base.Preconditions.checkArgument;
import static owl.ltl.rewriter.SimplifierFactory.Mode;
import static owl.ltl.rewriter.SimplifierFactory.apply;

import java.util.function.Function;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimizations.AcceptanceOptimizations;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.translations.dra2dpa.IARBuilder;

public final class Transformers {
  public static final Transformer LTL_SIMPLIFIER = Transformers.fromFunction(
    LabelledFormula.class, x -> apply(x, Mode.SYNTACTIC_FIXPOINT));
  public static final Transformer ACCEPTANCE_OPTIMIZATION_TRANSFORMER =
    new AcceptanceOptimizations.AcceptanceOptimizationTransformer();
  public static final Transformer RABIN_TO_PARITY = environment -> (input) ->
    new IARBuilder<>(AutomatonUtil.cast(input, RabinAcceptance.class)).build();

  private Transformers() {
  }

  public static <K, V> Transformer fromFunction(Class<K> inputClass, Function<K, V> function) {
    return environment -> instanceFromFunction(inputClass, function);
  }

  public static <K, V> Transformer.Instance instanceFromFunction(Class<K> inputClass,
    Function<K, V> function) {
    return (object) -> {
      //noinspection ConstantConditions
      checkArgument(inputClass.isInstance(object), "Expected type %s, got type %s", inputClass,
        object == null ? null : object.getClass());
      return function.apply(inputClass.cast(object));
    };
  }

  public abstract static class SimpleTransformer implements Transformer.Instance, Transformer {
    @Override
    public Instance create(Environment environment) {
      return this;
    }
  }
}
