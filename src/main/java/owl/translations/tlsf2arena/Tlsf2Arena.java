/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.translations.tlsf2arena;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;
import owl.ltl.parser.ParseException;
import owl.ltl.parser.TlsfParser;
import owl.ltl.tlsf.Tlsf;
import owl.translations.Optimisation;
import owl.translations.ltl2dpa.Ltl2Dpa;
import owl.translations.ltl2dpa.ParityAutomaton;

public final class Tlsf2Arena {
  private Tlsf2Arena() {
  }

  public static void main(String... args) throws ParseException, IOException {
    String[] arguments = args;
    // TODO
    if (arguments.length == 0) {
      arguments = new String[] {
        "/Users/sickert/Documents/workspace/syntcomp/Benchmarks2016/Tlsf/acaciaplus/easy.tlsf"};
    }

    Tlsf Tlsf = TlsfParser.parse(new FileInputStream(arguments[0]));

    EnumSet<Optimisation> optimisations = EnumSet.allOf(Optimisation.class);
    optimisations.remove(Optimisation.REMOVE_EPSILON_TRANSITIONS);

    Any2BitArena bit = new Any2BitArena();
    Any2BitArena.Player fstPlayer = Tlsf.target().isMealy()
      ? Any2BitArena.Player.ENVIRONMENT : Any2BitArena.Player.SYSTEM;

    File nodeFile = new File(arguments[0] + ".arena.nodes");
    File edgeFile = new File(arguments[0] + ".arena.edges");

    Ltl2Dpa translation = new Ltl2Dpa();
    ParityAutomaton<?> parity = translation.apply(Tlsf.toFormula());
    System.out.print(parity);
    bit.writeBinary(parity, fstPlayer, Tlsf.inputs(), nodeFile, edgeFile);
    bit.readBinary(nodeFile, edgeFile);
  }
}
