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

package owl.automaton.minimizations;

import static com.google.common.base.Preconditions.checkArgument;

import javax.annotation.Nullable;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.run.PipelineExecutionContext;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformers;

public class ImplicitMinimizeTransformer extends Transformers.SimpleTransformer {
  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("minimize-aut")
    .description("Tries to minimize the given automaton")
    .optionsDirect(new Options()
      .addOption("l", "level", true, "Level of minimization (light,medium,all)"))
    .parser(settings -> {
      String levelString = settings.getOptionValue("level");
      @Nullable
      MinimizationUtil.MinimizationLevel level = getLevel(levelString);

      if (level == null) {
        throw new ParseException("Invalid value for \"level\": " + levelString);
      }
      return environment -> new ImplicitMinimizeTransformer(level);
    }).build();

  private final MinimizationUtil.MinimizationLevel level;

  public ImplicitMinimizeTransformer() {
    this(MinimizationUtil.MinimizationLevel.ALL);
  }

  public ImplicitMinimizeTransformer(MinimizationUtil.MinimizationLevel level) {
    this.level = level;
  }

  @Nullable
  private static MinimizationUtil.MinimizationLevel getLevel(@Nullable String string) {
    if (string == null) {
      return MinimizationUtil.MinimizationLevel.ALL;
    }
    switch (string) {
      case "light":
        return MinimizationUtil.MinimizationLevel.LIGHT;
      case "medium":
        return MinimizationUtil.MinimizationLevel.MEDIUM;
      case "all":
        return MinimizationUtil.MinimizationLevel.ALL;
      default:
        return null;
    }
  }

  @Override
  public Object transform(Object object, PipelineExecutionContext context) {
    checkArgument(object instanceof Automaton, "Expected automaton, got %s", object.getClass());
    MutableAutomaton<?, ?> automaton = MutableAutomatonUtil.asMutable((Automaton<?, ?>) object);
    MinimizationUtil.minimizeDefault(automaton, level);
    automaton.trim();
    return automaton;
  }
}
