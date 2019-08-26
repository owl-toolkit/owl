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

// CSOFF: JavadocParagraphCheck

/**
 * This package (and it's sub-packages) contains a flexible infrastructure for executing various
 * translation chains and obtaining these translation chains from the command line. Executions are
 * modeled as a pipeline, starting with an input parser (e.g., an
 * {@link owl.run.modules.InputReaders#LTL_INPUT_MODULE LTL parser}), followed by multiple
 * transformers (e.g., an
 * {@link owl.translations.ltl2dpa.LTL2DPAFunction LTL to DPA translation}), and an output writer
 * at the end (e.g., a {@link owl.run.modules.OutputWriters#HOA_OUTPUT_MODULE HOA printer}).
 * To allow for high flexibility, various other concepts accompany this central model. In general,
 * the structure is as follows:
 *
 * <ul>
 * <li>A central coordinator (e.g., {@link owl.run.DefaultCli stream}) takes care of orchestrating
 * the whole execution. It receives an {@link owl.run.Pipeline abstract specification}
 * of the involved modules. After setting up relevant I/O operations and instantiating the pipeline
 * from the specification, the coordinator takes care of executing the process. Since input may
 * arrive asynchronously, a coordinator might decide to delegate one thread to input parsing and
 * another to processing the input. Similarly, it might delegate each task to a separate thread (or
 * even machine) to transparently achieve parallelism even for translations that don't inherently
 * make use of it. For example, a non-trivial coordinator would be a server that is waiting for
 * connections and starts a pipeline for each accepted connection, or processes multiple file input
 * output pairs.
 *
 * <p>N.B.: If some parts of the pipeline access a globally shared resource, e.g., some static
 * variable, these accesses have to be synchronized or the coordinators have to take care of only
 * running one process in total.</p>
 * </li>
 * <li>To obtain input, the coordinator is given a {@link owl.run.modules.OwlModule.InputReader
 * reader} which
 * will submit all parsed inputs to a callback.
 * </li>
 * <li>For each received input, all {@link owl.run.modules.OwlModule.Transformer transformers}
 * are called in
 * order, mutating the input to the desired output. This is usually done by translation or
 * optimization constructions, but another possible instances are debugging objects which return
 * the given object untouched and just output various statistics.</li>
 * <li>Eventually, once all transformers have been called for a particular input, the resulting
 * object is given to the {@link owl.run.modules.OwlModule.OutputWriter output writer}. This object
 * takes care of serializing the final result on the provided output stream.</li>
 * </ul>
 * For convenience, several default implementations are also provided. These should be suitable for
 * most cases and may serve as starting point for custom implementations. Note that the provided
 * implementations may satisfy stricter conditions than the ones imposed by the general design.
 * Refer to each interface for the actual method contracts.
 *
 * The command-line parser is completely pluggable and written without explicitly referencing any
 * of our implementations. New modules can be added by simply specifying a name, the set of options,
 * and a way to obtain a configured instance based on some options. For example, the module
 * {@literal ltl2nba} with a {@literal --fast} flag can be specified as follows
 * <pre>
 * {@code
 * OwlModule<OwlModule.Transformer> settings = OwlModule.of(
 *   "ltl2nba",
 *   "LTL to NBA translation",
 *   new Options()
 *   .addOption("f", "fast", false, "Turn on ludicrous speed!")),
 *   (commandLine, environment) -> {
 *     boolean fast = settings.hasOption("fast");
 *     return environment -> (input, context) ->
 *       LTL2NBA.apply((LabelledFormula) input, fast, environment);
 *   });
 * }</pre>
 * These settings now only have to be added to the
 * {@link owl.run.modules.OwlModuleRegistry registry} to be usable with the extended command line
 * syntax. Also, a dedicated {@code main} method can be created by delegating to the
 * {@link owl.run.parser.PartialConfigurationParser partial configuration parser}.
 */
@EverythingIsNonnullByDefault
package owl.run;
// CSON: JavadocParagraphCheck

import owl.util.annotation.EverythingIsNonnullByDefault;