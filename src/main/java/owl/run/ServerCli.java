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

package owl.run;

import static owl.run.RunUtil.checkDefaultAnnotationOption;
import static owl.run.RunUtil.checkDefaultParallelOption;
import static owl.run.RunUtil.failWithMessage;
import static owl.run.RunUtil.getDefaultAnnotationOption;
import static owl.run.RunUtil.getDefaultParallelOption;

import com.google.common.base.Strings;
import java.util.concurrent.Callable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.run.modules.OwlModuleRegistry;
import owl.run.parser.OwlParser;

public final class ServerCli {
  private ServerCli() {}

  public static Options getOptions() {
    Option portOption = new Option(null, "port", true, "Port to listen on (default 5050)");

    return new Options()
      .addOption(portOption)
      .addOption(getDefaultAnnotationOption())
      .addOption(getDefaultParallelOption());
  }

  public static Callable<Void> build(CommandLine settings, Pipeline pipeline) {
    int port;

    if (Strings.isNullOrEmpty(settings.getOptionValue("port"))) {
      port = 5050;
    } else {
      try {
        port = Integer.parseInt(settings.getOptionValue("port"));
      } catch (NumberFormatException e) {
        throw failWithMessage("Invalid value for port", e);
      }
    }

    boolean annotations = checkDefaultAnnotationOption(settings);
    boolean parallel = checkDefaultParallelOption(settings);
    return new ServerRunner(pipeline, () -> DefaultEnvironment.of(annotations, parallel), port);
  }

  public static void main(String... args) {
    OwlParser parseResult =
      OwlParser.parse(args, new DefaultParser(), getOptions(), OwlModuleRegistry.DEFAULT_REGISTRY);
    if (parseResult == null) {
      System.exit(1);
      return;
    }

    RunUtil.execute(build(parseResult.globalSettings, parseResult.pipeline));
  }
}
