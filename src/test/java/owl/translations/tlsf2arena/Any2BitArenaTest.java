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
import java.io.IOException;
import java.util.BitSet;
import owl.ltl.Formula;
import owl.ltl.parser.LtlParser;
import owl.translations.ltl2dpa.LTL2DPAFunction;

public class Any2BitArenaTest {
  private static final LTL2DPAFunction translation = new LTL2DPAFunction();

  public void testWriteBinaryEnv() throws IOException {
    Formula ltl = LtlParser.formula("G ((a & b) | (!a & (!b)))");
    BitSet bs = new BitSet();
    bs.set(0);

    Any2BitArena writer = new Any2BitArena();
    writer.writeBinary(translation.apply(ltl), Any2BitArena.Player.ENVIRONMENT, bs,
      new File("G(a<->b)_env.nodes"), new File("G(a<->b)_env.edges"));
  }

  public void testWriteBinarySimpEnv() throws IOException {
    Formula ltl = LtlParser.formula("G (!a | b)");
    BitSet bs = new BitSet();
    bs.set(0);

    Any2BitArena writer = new Any2BitArena();
    writer.writeBinary(translation.apply(ltl), Any2BitArena.Player.ENVIRONMENT, bs,
      new File("G(a->b)_env.nodes"), new File("G(a->b)_env.edges"));
  }

  public void testWriteBinarySimpSystem() throws IOException {
    Formula ltl = LtlParser.formula("G (!a | b)");
    BitSet bs = new BitSet();
    bs.set(0);

    Any2BitArena writer = new Any2BitArena();
    writer.writeBinary(translation.apply(ltl), Any2BitArena.Player.SYSTEM, bs,
      new File("G(a->b)_sys.nodes"), new File("G(a->b)_sys.edges"));
  }

  public void testWriteBinarySystem() throws IOException {
    Formula ltl = LtlParser.formula("G ((a & b) | (!a & !b))");
    BitSet bs = new BitSet();
    bs.set(0);

    Any2BitArena writer = new Any2BitArena();
    writer.writeBinary(translation.apply(ltl), Any2BitArena.Player.SYSTEM, bs,
      new File("G(a<->b)_sys.nodes"), new File("G(a<->b)_sys.edges"));
  }

  public void testWriteBinaryXEnv() throws IOException {
    Formula ltl = LtlParser.formula("G ((a & X b) | (!a & (X!b)))");
    BitSet bs = new BitSet();
    bs.set(0);

    Any2BitArena writer = new Any2BitArena();
    writer.writeBinary(translation.apply(ltl), Any2BitArena.Player.ENVIRONMENT, bs,
      new File("G(a<->Xb)_env.nodes"), new File("G(a<->Xb)_env.edges"));
  }

  public void testWriteBinaryXSystem() throws IOException {
    Formula ltl = LtlParser.formula("G ((a & X b) | (!a & X!b))");
    BitSet bs = new BitSet();
    bs.set(0);

    Any2BitArena writer = new Any2BitArena();
    writer.writeBinary(translation.apply(ltl), Any2BitArena.Player.SYSTEM, bs,
      new File("G(a<->Xb)_sys.nodes"), new File("G(a<->Xb)_sys.edges"));
  }
}
