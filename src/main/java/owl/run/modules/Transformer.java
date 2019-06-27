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

import owl.run.Environment;
import owl.run.PipelineExecutionContext;

/**
 * Transformers are the central pieces of the pipeline concept. They should be used for any
 * non-trivial mutation of objects. Typical instantiations are, for example, LTL to Automaton
 * translators, optimization steps, etc. Aside from these tasks, pseudo-transformers can be used to
 * provide insight into the progress of the pipeline by appending meta information to the context.
 *
 * <p>Implementation notes: It is strongly encouraged to design transformers in a stateless
 * fashion, since it allows for easy parallelism. As parallel processing is a central design
 * concept, the {@link Transformer.Instance#transform(Object, PipelineExecutionContext) transform}
 * method must support parallel calls, even if there is some state involved. Should synchronization
 * be costly, the implementation can enable it based on {@link Environment#parallel()}.</p>
 */
@FunctionalInterface
public interface Transformer extends OwlModule {
  Instance create(Environment environment);

  @FunctionalInterface
  interface Instance {
    /**
     * Applies the transformation represented by this transformer to the given object.
     */
    @SuppressWarnings({"PMD.SignatureDeclareThrowsException", "ProhibitedExceptionDeclared"})
    Object transform(Object object, PipelineExecutionContext context) throws Exception;
  }
}
