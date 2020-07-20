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

package owl.translations.nbadet;

import com.google.auto.value.AutoValue;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.collections.Pair;
import owl.run.RunUtil;

@AutoValue
public abstract class NbaDetArgs {

  private static final Option optVerbosity = Option.builder("v").longOpt("verbosity")
      .desc("Set nbadet log verbosity level (e.g. INFO, WARNING, FINE, FINER, FINEST)")
      .hasArg().argName("level").type(String.class).build();


  private static final Option optMergeMode = Option.builder("m").longOpt("merge-mode")
      .desc("Which merge method to use in construction (0=Muller-Schupp, 1=Safra, 2=Maximal merge)")
      .hasArg().argName("mode").type(Number.class).build();

  private static final Option optComputeSims = Option.builder("l")
      .longOpt("compute-lang-inclusions")
      .desc("List of algorithms to use on NBA to obtain language inclusions.")
      .hasArgs().argName("sims").valueSeparator(',').type(String.class).build();

  private static final Option optSimExt = Option.builder("e").longOpt("use-sim-external")
      .desc("Use results of simulation calculation for preprocessing and optimization.").build();

  private static final Option optSimInt = Option.builder("j").longOpt("use-sim-internal")
      .desc("Use results of simulation calculation to prune the deterministic states.").build();

  private static final Option optUsePowersets = Option.builder("t").longOpt("use-powersets")
      .desc("Use powerset structure of NBA to guide determinization").build();

  private static final Option optUseSmartSucc = Option.builder("s").longOpt("use-smart-succ")
      .desc("Try to redirect edges to suitable already existing states on-the-fly").build();

  private static final Option optSepRej = Option.builder("r").longOpt("sep-rej")
      .desc("Separate simplified handling for states in rejecting SCCs").build();

  private static final Option optSepAcc = Option.builder("A").longOpt("sep-acc")
      .desc("Separate simplified handling for states in accepting SCCs").build();

  private static final Option optSepAccCyc = Option.builder("b").longOpt("sep-acc-cycle")
      .desc("Cycle breakpoint construction for accepting SCCs").build();

  private static final Option optSepDet = Option.builder("d").longOpt("sep-det")
      .desc("Separate simplified handling for deterministic SCCs").build();

  private static final Option optSepMix = Option.builder("c").longOpt("sep-sccs")
      .desc("Separate handling of all SCCs (that are not already specially handled)").build();

  private static final Option optDefaultConfig = Option.builder("z").longOpt("default-config")
      .desc("Use a suggested configuration that works well most of the time, which is:\n"
          + getDefault().toString()).build();


  // exposed option structure to be included in the owl pipeline argument parser
  public static final Options options = new Options()
      .addOption(optVerbosity)
      .addOption(optMergeMode)
      .addOption(optComputeSims)
      .addOption(optSimExt)
      .addOption(optSimInt)
      .addOption(optUsePowersets)
      .addOption(optUseSmartSucc)
      .addOption(optSepRej)
      .addOption(optSepAcc)
      .addOption(optSepAccCyc)
      .addOption(optSepDet)
      .addOption(optSepMix)
      .addOption(optDefaultConfig);

  // --------------------------------

  public abstract NbaDetConf.UpdateMode mergeMode();

  public abstract Level verbosity();

  public abstract Set<Pair<NbaLangInclusions.SimType,String>> computeSims();

  public abstract boolean simExt();

  public abstract boolean simInt();

  public abstract boolean usePowersets();

  public abstract boolean useSmartSucc();

  public abstract boolean sepRej();

  public abstract boolean sepAcc();

  public abstract boolean sepAccCyc();

  public abstract boolean sepDet();

  public abstract boolean sepMix();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setVerbosity(Level l);

    public abstract Builder setMergeMode(NbaDetConf.UpdateMode m);

    public abstract Builder setComputeSims(Set<Pair<NbaLangInclusions.SimType,String>> a);

    public abstract Builder setSimExt(boolean b);

    public abstract Builder setSimInt(boolean b);

    public abstract Builder setUsePowersets(boolean b);

    public abstract Builder setUseSmartSucc(boolean b);

    public abstract Builder setSepRej(boolean b);

    public abstract Builder setSepAcc(boolean b);

    public abstract Builder setSepAccCyc(boolean b);

    public abstract Builder setSepDet(boolean b);

    public abstract Builder setSepMix(boolean b);


    public abstract NbaDetArgs build();
  }

  public static Builder builder() {
    //every opt set to false, default update mode is Safra
    return new AutoValue_NbaDetArgs.Builder()
        .setVerbosity(Level.WARNING)
        .setMergeMode(NbaDetConf.UpdateMode.SAFRA)
        .setComputeSims(Set.of());
  }

  //allows to modify based on some config
  public abstract Builder toBuilder();

  // --------------------------------

  /** A configuration with the suggested default optimizations. */
  public static NbaDetArgs getDefault() {
    return builder().setMergeMode(NbaDetConf.UpdateMode.SAFRA)
                    .setComputeSims(Set.of(
                      Pair.of(NbaLangInclusions.SimType.DIRECT_REFINEMENT_SIM, "") //cheap, builtin
                    ))
                    .setSimExt(true)
                    .setSimInt(true)
                    .setUsePowersets(true)
                    .setUseSmartSucc(true)
                    //these at least seem to do no harm and sometimes do help
                    .setSepDet(true)
                    .setSepMix(true)
                    .setSepRej(true)
                    //these optimizations don't look as a good default "on average"
                    .setSepAcc(false)    // <- good-ish on rnd. NBA, but much worse on rnd. LTL
                    .setSepAccCyc(false) // <- usually better variant of previous option
                    .build();
  }

  public static NbaDetConf.UpdateMode extractMergeMode(CommandLine cmdLine, String arg) {
    try {
      final int mode = ((Number) cmdLine.getParsedOptionValue(arg)).intValue();
      if (mode < 0 || mode >= NbaDetConf.UpdateMode.values().length) {
        throw new IllegalArgumentException();
      }
      return NbaDetConf.UpdateMode.values()[mode];
    } catch (ParseException | IllegalArgumentException e) {
      RunUtil.failWithMessage(
        "failed to parse provided merge mode value! "
          + "Merge mode must be a value in the range [0,"
          + (NbaDetConf.UpdateMode.values().length - 1) + "].");
    }

    //should not be reached
    return getDefault().mergeMode();
  }

  public static Set<Pair<NbaLangInclusions.SimType,String>> extractSims(
      CommandLine cmdLine, String arg) {
    String[] simlist = cmdLine.getOptionValues(NbaDetArgs.optComputeSims.getOpt());
    Set<Pair<NbaLangInclusions.SimType, String>> ret = Set.of();

    var simArgs = NbaLangInclusions.getAlgoArgs();
    try {
      ret = Arrays.stream(simlist).map(str -> {
        String[] kv = str.split("@");
        if (!simArgs.containsKey(kv[0])) {
          throw new IllegalArgumentException();
        }

        final var simType = simArgs.get(kv[0]);
        //arg = e.g. number of pebbles to use for multi-pebble approach
        final var simArg  = kv.length == 1 ? "" : kv[1];
        return Pair.of(simType, simArg);
      }).collect(Collectors.toUnmodifiableSet());
    } catch (IllegalArgumentException e) {
      RunUtil.failWithMessage(
          "failed to parse provided list of simulations to compute! "
          + "Must be a comma-separated list of algorithms from " + simArgs.keySet().toString());
    }

    //TODO: maybe filter out redundant? i.e. like directed < delayed < fair sim

    return ret;
  }

  /**
   * Returns a configuration that is configured by flags provided by the user.
   * @param cmdLine commandline object with arguments from the environment
   */
  public static NbaDetArgs getFromCli(CommandLine cmdLine) {
    //shortcut: user wants to use default config
    if (cmdLine.hasOption(optDefaultConfig.getOpt())) {
      return getDefault();
    }

    //proceed to read arguments to build a configuration
    var bldr = builder();

    if (cmdLine.hasOption(optVerbosity.getOpt())) {
      bldr.setVerbosity(Level.parse(cmdLine.getOptionValue(optVerbosity.getOpt())));
    }

    if (cmdLine.hasOption(optMergeMode.getOpt())) {
      bldr.setMergeMode(extractMergeMode(cmdLine, optMergeMode.getOpt()));
    }

    if (cmdLine.hasOption(optComputeSims.getOpt())) {
      bldr.setComputeSims(extractSims(cmdLine, optComputeSims.getOpt()));
    }

    bldr.setSimInt(cmdLine.hasOption(optSimInt.getOpt()));
    bldr.setSimExt(cmdLine.hasOption(optSimExt.getOpt()));

    bldr.setUsePowersets(cmdLine.hasOption(optUsePowersets.getOpt()));
    bldr.setUseSmartSucc(cmdLine.hasOption(optUseSmartSucc.getOpt()));

    bldr.setSepAcc(cmdLine.hasOption(optSepAcc.getOpt()));
    bldr.setSepAccCyc(cmdLine.hasOption(optSepAccCyc.getOpt()));
    bldr.setSepRej(cmdLine.hasOption(optSepRej.getOpt()));
    bldr.setSepDet(cmdLine.hasOption(optSepDet.getOpt()));
    bldr.setSepMix(cmdLine.hasOption(optSepMix.getOpt()));

    return bldr.build();
  }
}
