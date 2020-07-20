/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.automaton.algorithm.simulations;

import com.google.auto.value.AutoValue;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

@AutoValue
public abstract class BuchiSimulationArguments {
  private static final Option optDirect = Option
    .builder("di")
    .longOpt("direct")
    .desc("Compute direct simulation relation")
    .build();

  private static final Option optDirectRefinement = Option
    .builder("diref")
    .longOpt("direct-refinement")
    .desc("Compute direct simulation relation using color refinement (fast)")
    .build();

  private static final Option optDelayed = Option
    .builder("de")
    .longOpt("delayed")
    .desc("Compute delayed simulation relation")
    .build();

  private static final Option optFair = Option
    .builder("f")
    .longOpt("fair")
    .desc("Compute fair simulation relation")
    .build();

  private static final Option optBackward = Option
    .builder("bw")
    .longOpt("backward")
    .desc("Compute backwards simulation relation")
    .build();

  private static final Option optLookaheadDirect = Option
    .builder("diL")
    .longOpt("lookahead-direct")
    .desc("Compute direct simulation with lookahead")
    .build();

  private static final Option optMaxLookahead = Option
    .builder("l")
    .longOpt("lookahead")
    .desc("Make this many moves of Spoiler available to Duplicator")
    .hasArg()
    .argName("lookahead")
    .type(Integer.class)
    .build();

  private static final Option optSanity = Option
    .builder("S")
    .longOpt("sanity")
    .desc("Sanity check different relation inclusions for input automaton.")
    .build();

  private static final Option optPebbleCount = Option
    .builder("c")
    .longOpt("pebbles")
    .desc("Allow duplicator to have the set amount of pebbles")
    .hasArg()
    .argName("pebbles")
    .type(Integer.class)
    .build();

  private static final Option optVerboseFine = Option
    .builder("V")
    .longOpt("verbose")
    .desc("Use logging level FINE for more output")
    .build();

  public static final Options options = new Options()
    .addOption(optDirect)
    .addOption(optVerboseFine)
    .addOption(optDelayed)
    .addOption(optBackward)
    .addOption(optDirectRefinement)
    .addOption(optMaxLookahead)
    .addOption(optLookaheadDirect)
    .addOption(optSanity)
    .addOption(optFair)
    .addOption(optPebbleCount);

  public static Builder builder() {
    return new AutoValue_BuchiSimulationArguments.Builder()
      .setPebbleCount(1)
      .setVerboseFine(false)
      .setComputeFair(false)
      .setComputeLookaheadDirect(false)
      .setMaxLookahead(1)
      .setSanity(false)
      .setComputeBackward(false)
      .setComputeDelayed(false)
      .setComputeDirect(true)
      .setComputeDirectRefinement(false);
  }

  public static BuchiSimulationArguments getFromCli(CommandLine cmdLine) {
    var builder = builder();

    builder.setComputeDirect(cmdLine.hasOption(optDirect.getOpt()));

    if (cmdLine.hasOption(optDelayed.getOpt())) {
      builder.setComputeDelayed(true);
      // since direct is contained in delayed, we can switch this off
      builder.setComputeDirect(false);
    }

    if (cmdLine.hasOption(optFair.getOpt())) {
      builder.setComputeFair(true);
      // since both are contained in fair simulation we switch them off
      builder.setComputeDelayed(false);
      builder.setComputeDirect(false);
    }

    if (cmdLine.hasOption(optBackward.getOpt())) {
      builder.setComputeBackward(true);
      builder.setComputeDirect(false);
      builder.setComputeDelayed(false);
      builder.setComputeFair(false);
    }

    if (cmdLine.hasOption(optLookaheadDirect.getOpt())) {
      builder.setComputeBackward(false);
      builder.setComputeDirect(false);
      builder.setComputeDelayed(false);
      builder.setComputeFair(false);
      builder.setComputeLookaheadDirect(true);
    }

    if (cmdLine.hasOption(optDirectRefinement.getOpt())) {
      builder.setComputeBackward(false);
      builder.setComputeDirect(false);
      builder.setComputeDelayed(false);
      builder.setComputeFair(false);
      builder.setComputeLookaheadDirect(false);
      builder.setComputeDirectRefinement(true);
    }

    builder.setSanity(cmdLine.hasOption(optSanity.getOpt()));

    builder.setVerboseFine(cmdLine.hasOption(optVerboseFine.getOpt()));

    if (cmdLine.hasOption(optPebbleCount.getOpt())) {
      builder.setPebbleCount(Integer.parseInt(cmdLine.getOptionValue(optPebbleCount.getOpt())));
    }

    if (cmdLine.hasOption(optMaxLookahead.getOpt())) {
      builder.setMaxLookahead(Integer.parseInt(cmdLine.getOptionValue(optMaxLookahead.getOpt())));
    }

    return builder.build();
  }

  public abstract boolean computeDirect();

  public abstract boolean computeDelayed();

  public abstract boolean computeFair();

  public abstract boolean computeBackward();

  public abstract boolean computeLookaheadDirect();

  public abstract boolean computeDirectRefinement();

  public abstract boolean sanity();

  public abstract int pebbleCount();

  public abstract int maxLookahead();

  public abstract boolean verboseFine();

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setComputeDirect(boolean b);

    abstract Builder setComputeDelayed(boolean b);

    abstract Builder setComputeFair(boolean b);

    abstract Builder setComputeBackward(boolean b);

    abstract Builder setComputeLookaheadDirect(boolean b);

    abstract Builder setComputeDirectRefinement(boolean b);

    abstract Builder setSanity(boolean b);

    abstract Builder setPebbleCount(int count);

    abstract Builder setMaxLookahead(int lookahead);

    abstract Builder setVerboseFine(boolean b);

    abstract BuchiSimulationArguments build();
  }
}