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

package translations.ltl2ldba;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import ltl.parser.Parser;
import ltl.tlsf.TLSF;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import org.junit.Test;
import translations.Optimisation;
import translations.ldba.LimitDeterministicAutomaton;

import java.io.*;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

public class BenchmarkTest {

    public void tlsf() throws Exception {
        File benchmarkFolder = new File("/Users/sickert/Documents/workspace/syntcomp/Benchmarks2016/TLSF/acaciaplus");
        File log = new File("test.log");

        try (PrintStream stream = new PrintStream(new FileOutputStream(log))) {

            for (File file : benchmarkFolder.listFiles(f -> f.getName().endsWith(".tlsf"))) {
                stream.println("File: " + file.getName());

                if (file.getName().contains("genbuf") || file.getName().contains("ltl2dba01.tlsf") || file.getName().contains("load")) {
                    continue;
                }

                Parser parser = new Parser(new FileInputStream(file));
                TLSF tlfs = parser.tlsf();

                stream.println("+ Formula: " + tlfs.toFormula());

                EnumSet<Optimisation> opts = EnumSet.allOf(Optimisation.class);
                opts.remove(Optimisation.BREAKPOINT_FUSION);
                LTL2LDBA translation = new LTL2LDBA(opts);
                LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent, AcceptingComponent> automaton = translation.apply(tlfs.toFormula());

                stream.println("+ Size of Accepting Component: " + automaton.getAcceptingComponent().size());
                stream.println("+ Number of Compontents: " + automaton.getAcceptingComponent().getNumberOfComponents());
                stream.println();

                automaton.free();
            }
        }
    }
}