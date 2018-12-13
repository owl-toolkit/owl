/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.ltl.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.grammar.SPECTRALexer;
import owl.grammar.SPECTRAParser;
import owl.grammar.SPECTRAParser.DeclContext;
import owl.grammar.SPECTRAParser.LtlContext;
import owl.grammar.SPECTRAParser.ModelContext;
import owl.grammar.SPECTRAParser.TypeDefContext;
import owl.grammar.SPECTRAParser.VarDefContext;
import owl.grammar.SPECTRAParser.VarTypeContext;
import owl.grammar.SPECTRAParser.WeightContext;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.util.annotation.CEntryPoint;

public final class SpectraParser {
  private static int offset = 0;
  private static Set<String> typeConstants = new HashSet<>();

  private SpectraParser() {}

  @CEntryPoint
  public static Formula parse(String input) {
    return parse(CharStreams.fromString(input));
  }

  private static Formula parse(CharStream stream) {
    SPECTRALexer lexer = new SPECTRALexer(stream);
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    SPECTRAParser parser = new SPECTRAParser(tokens);
    parser.setErrorHandler(new BailErrorStrategy());

    ModelContext tree = parser.model();

    //<editor-fold desc="Process Imports [TODO: Do something with imports]">
    ArrayList<String> imports = new ArrayList<>();
    for (Token imp : tree.importURIS) {
      imports.add(imp.getText());
    }
    //</editor-fold>

    //<editor-fold desc="Preprocess unsupported actions [Weights]">
    for (DeclContext decl : tree.elements) {
      WeightContext weightC = decl.weight();

      if (weightC != null) {
        throw new UnsupportedOperationException("Weights are not supported yet.");
      }
    }
    //</editor-fold>

    //<editor-fold desc="Preprocess type definitions">
    HashMap<String, SpectraType> types = new HashMap<>();
    Queue<TypeDefContext> unresolvedTypeDefs = new LinkedList<>();

    for (DeclContext decl : tree.elements) {
      TypeDefContext typeDefC = decl.typeDef();

      if (typeDefC != null) {
        String typeName = typeDefC.name.getText();
        if (typeDefC.type.type != null && !types.containsKey(typeDefC.type.type.getText())) {
          unresolvedTypeDefs.add(typeDefC);
          continue;
        }
        int[] dims = getDimensions(typeDefC.type.dimensions);
        SpectraType type = constructTypes(typeDefC.type);
        if (dims.length > 0) {
          type = new SpectraArray(type, dims);
        }
        types.put(typeName, type);
      }
    }

    while (!unresolvedTypeDefs.isEmpty()) {
      TypeDefContext typeDefC = unresolvedTypeDefs.poll();
      String typeRefName = typeDefC.type.type.getText();
      if (types.containsKey(typeRefName)) {
        String typeName = typeDefC.name.getText();
        int[] dims = getDimensions(typeDefC.type.dimensions);
        SpectraType type = constructTypeFromType(types.get(typeRefName));
        if (dims.length > 0) {
          type = new SpectraArray(type, dims);
        }
        types.put(typeName, type);
      } else {
        unresolvedTypeDefs.add(typeDefC);
      }
    }
    //</editor-fold>

    //<editor-fold desc="Process I/O-variables">
    HashMap<String, HigherOrderExpression> variables = new HashMap<>();
    List<String> inputs = new ArrayList<>();
    List<String> outputs = new ArrayList<>();

    for (DeclContext decl : tree.elements) {
      VarDefContext varDefC = decl.varDef();

      if (varDefC != null) {
        String varName = varDefC.var.name.getText();
        if (varDefC.kind.getText().equals("in")) {
          inputs.add(varName);
        } else {
          outputs.add(varName);
        }
        int[] dims = getDimensions(varDefC.var.type.dimensions);
        SpectraType type = (varDefC.var.type.type == null)
          ? constructTypes(varDefC.var.type) :
          (SpectraType) types.get(varDefC.var.type.type.getText());
        if (dims.length > 0) {
          type = new SpectraArray(type, dims);
        }
        variables.put(varName, constructVariables(type));
      }
    }
    //</editor-fold>

    //<editor-fold desc="Process LTL-Asm/Gar">
    List<Formula> initialEnv = new ArrayList<>();
    List<Formula> initialSys = new ArrayList<>();
    List<Formula> safetyEnv = new ArrayList<>();
    List<Formula> safetySys = new ArrayList<>();
    List<Formula> livenessEnv = new ArrayList<>();
    List<Formula> livenessSys = new ArrayList<>();

    for (DeclContext decl : tree.elements) {
      LtlContext ltlC = decl.ltl();

      if (ltlC != null) {
        SpectraParseTreeVisitor treeVisitor = new SpectraParseTreeVisitor(variables, typeConstants);

        Formula formula = treeVisitor.visit(ltlC);

        if (ltlC.safety != null || ltlC.stateInv != null) {
          if (ltlC.ASM() == null) {
            safetySys.add(formula);
          } else {
            safetyEnv.add(formula);
          }
        } else if (ltlC.justice != null) {
          if (ltlC.ASM() == null) {
            livenessSys.add(formula);
          } else {
            livenessEnv.add(formula);
          }
        } else {
          if (ltlC.ASM() == null) {
            initialSys.add(formula);
          } else {
            initialEnv.add(formula);
          }
        }
      }
    }
    //</editor-fold>

    //TODO: return one formula
    Formula initialE = Conjunction.of(initialEnv);
    Formula initialS = Conjunction.of(initialSys);
    Formula safetyE = Conjunction.of(safetyEnv);
    Formula safetyS = Conjunction.of(safetySys);
    Formula livenessE = Conjunction.of(livenessEnv);
    Formula livenessS = Conjunction.of(livenessSys);

    Formula part1 = Disjunction.of(livenessE.not(), livenessS);
    Formula part2 = Conjunction.of(GOperator.of(safetyE), part1);
    Formula part3 = WOperator.of(safetyS, safetyE.not());
    Formula part4 = Conjunction.of(initialS, part3, part2);

    return Disjunction.of(initialE.not(), part4);
  }

  public static Formula parse(InputStream input, Charset charset) throws IOException {
    return parse(CharStreams.fromStream(input, charset));
  }

  public static Formula parse(Reader input) throws IOException {
    return parse(CharStreams.fromReader(input));
  }

  //<editor-fold desc="Type/Variable construction">
  private static int[] getDimensions(List<Token> dims) {
    return dims.stream().mapToInt(dim -> Integer.parseInt(dim.getText())).toArray();
  }

  private static SpectraType constructTypes(VarTypeContext varTypeC) {
    SpectraType type;
    if (varTypeC.name != null && varTypeC.name.getText().equals("boolean")) {
      type = new SpectraBoolean();
    } else if (varTypeC.INT_S() != null) {
      int from = Integer.parseInt(varTypeC.subr.from.getText());
      int to = Integer.parseInt(varTypeC.subr.to.getText());
      type = new SpectraIntRange(from, to, offset);
    } else if (varTypeC.LCPAR() != null) {
      ArrayList<String> values = varTypeC.consts.stream()
        .map(RuleContext::getText)
        .collect(Collectors.toCollection(ArrayList::new));
      type = new SpectraEnum(values, offset);
      typeConstants.addAll(values);
    } else {
      throw new ParseCancellationException("Unrecognizable type: " + varTypeC.getText());
    }

    for (int i = offset; i < offset + type.width(); i++) {
      Literal.of(i);
    }
    offset += type.width();

    return type;
  }

  private static SpectraType constructTypeFromType(SpectraType origin) {
    SpectraType type;
    if (origin instanceof SpectraBoolean) {
      type = new SpectraBoolean();
    } else if (origin instanceof  SpectraIntRange) {
      SpectraIntRange castOrigin = (SpectraIntRange) origin;
      type = new SpectraIntRange(castOrigin.from, castOrigin.to, offset);
    } else if (origin instanceof SpectraEnum) {
      SpectraEnum castOrigin = (SpectraEnum) origin;
      type = new SpectraEnum(castOrigin.values, offset);
    } else if (origin instanceof SpectraArray) {
      SpectraArray castOrigin = (SpectraArray) origin;
      type = new SpectraArray(castOrigin.component, castOrigin.dimensions);
    } else {
      throw new ParseCancellationException("Unknown type of referenced type");
    }

    int width = (type instanceof SpectraArray)
      ? ((SpectraArray) type).component.width() : type.width();
    for (int i = offset; i < offset + width; i++) {
      Literal.of(i);
    }
    offset += width;

    return type;
  }

  private static HigherOrderExpression constructVariables(SpectraType type) {
    HigherOrderExpression variable;
    if (type instanceof SpectraEnum) {
      variable = new SpectraEnumVariable((SpectraEnum) type, offset);
    } else if (type instanceof SpectraIntRange) {
      variable = new SpectraIntRangeVariable((SpectraIntRange) type, offset);
    } else if (type instanceof SpectraBoolean) {
      variable = new SpectraBooleanVariable((SpectraBoolean) type, offset);
    } else if (type instanceof SpectraArray) {
      variable = new SpectraArrayVariable((SpectraArray) type, offset);
    } else {
      throw new ParseCancellationException("Unrecognizable variable type");
    }

    for (int i = offset; i < offset + type.width(); i++) {
      Literal.of(i);
    }
    offset += type.width();

    return variable;
  }
  //</editor-fold>

  //<editor-fold desc="Higher Order Types/Expressions">

  //<editor-fold desc="Types">
  interface SpectraType {
    HigherOrderExpression of(String value);

    int width();
  }

  static class SpectraEnum implements SpectraType {
    private final int width;
    private final int offset;
    private final List<String> values;

    private SpectraEnum(List<String> values, int offset) {
      this.width = (int) Math.ceil(Math.log(values.size()) / Math.log(2));
      this.values = new ArrayList<>(values);
      this.offset = offset;
    }

    @Override
    public SpectraEnumConstant of(String value) {
      int index = values.indexOf(value);
      if (index == -1) {
        throw new ParseCancellationException(value + " is not a valid value for this enum");
      } else {
        return new SpectraEnumConstant(this, index);
      }
    }

    @Override
    public int width() {
      return width;
    }
  }

  static class SpectraIntRange implements SpectraType {
    private final int from;
    private final int to;
    private final int width;
    private final int offset;

    private SpectraIntRange(int from, int to, int offset) {
      this.from = from;
      this.to = to;
      this.width = (int) Math.ceil(Math.log(to - from + 1) / Math.log(2));
      this. offset = offset;
    }

    @Override
    public SpectraIntRangeConstant of(String value) {
      int val = Integer.parseInt(value);
      if (val > to || val < 0) {
        throw new ParseCancellationException(value + " is not in this integer range");
      } else {
        return new SpectraIntRangeConstant(this,val - from);
      }
    }

    @Override
    public int width() {
      return width;
    }
  }

  static class SpectraBoolean implements SpectraType {
    private final int width;

    private SpectraBoolean() {
      this.width = 1;
    }

    @Override
    public SpectraBooleanConstant of(String value) {
      boolean val = !"false".equals(value);
      return new SpectraBooleanConstant(this, val);
    }

    @Override
    public int width() {
      return width;
    }
  }

  static class SpectraArray implements SpectraType {
    private final int width;
    private final int[] dimensions;
    private final int[] dimEnc;
    private final SpectraType component;

    private SpectraArray(SpectraType component, int[] dims) {
      if (component instanceof SpectraArray) {
        SpectraArray origin = (SpectraArray) component;
        this.dimEnc = Arrays.copyOf(origin.dimEnc, origin.dimEnc.length + dims.length);
        constructDimEnc(origin.dimEnc.length, dims);
        this.dimensions = IntStream.concat(Arrays.stream(origin.dimensions),
          Arrays.stream(dims)).toArray();
        this.width = dimEnc[origin.dimensions.length] * origin.width();
        this.component = origin.component;
      } else {
        this.dimEnc = new int[dims.length];
        constructDimEnc(0, dims);
        this.dimensions = Arrays.copyOf(dims, dims.length);
        this.width = dimEnc[0] * component.width();
        this.component = component;
      }
    }

    private void constructDimEnc(int start, int[] dims) {
      Arrays.fill(dimEnc, start, dimEnc.length, 1);
      for (int i = dims.length - 1; i >= start; i--) {
        int dim = dims[i];
        for (int j = 0; j < i; j++) {
          dimEnc[j] *= dim;
        }
      }
    }

    private int getEnc(int[] indices) {
      int index = 0;
      for (int i = 0; i < indices.length; i++) {
        index += (dimEnc[i] * indices[i]);
      }
      return index;
    }

    @Override
    public HigherOrderExpression of(String value) {
      return component.of(value);
    }

    @Override
    public int width() {
      return width;
    }
  }
  //</editor-fold>

  interface HigherOrderExpression {
    Formula toFormula();

    Formula getBit(int i);

    SpectraType getType();

    int width();
  }

  //<editor-fold desc="Constants">
  static class SpectraEnumConstant implements HigherOrderExpression {
    private final SpectraEnum type;
    private final int element;

    private SpectraEnumConstant(SpectraEnum type, int element) {
      this.type = type;
      this.element = element;
    }

    @Override
    public Formula toFormula() {
      throw new ParseCancellationException("toFormula shouldn't be called from a constant");
    }

    @Override
    public Formula getBit(int i) {
      Literal literal = Literal.of(type.offset + i);
      if ((element & (1 << i)) == 0) {
        return literal.not();
      } else {
        return literal;
      }
    }

    @Override
    public SpectraType getType() {
      return this.type;
    }

    @Override
    public int width() {
      return type.width();
    }
  }

  static class SpectraIntRangeConstant implements HigherOrderExpression {
    private final SpectraIntRange type;
    private final int element;

    private SpectraIntRangeConstant(SpectraIntRange type, int element) {
      this.type = type;
      this.element = element;
    }

    @Override
    public Formula toFormula() {
      throw new ParseCancellationException("toFormula shouldn't be called from a constant");
    }

    @Override
    public Formula getBit(int i) {
      Literal literal = Literal.of(type.offset + i);
      if ((element & (1 << i)) == 0) {
        return literal.not();
      } else {
        return literal;
      }
    }

    @Override
    public SpectraType getType() {
      return this.type;
    }

    @Override
    public int width() {
      return type.width();
    }
  }

  static class SpectraBooleanConstant implements HigherOrderExpression {
    private final SpectraBoolean type;
    private final boolean value;

    private SpectraBooleanConstant(SpectraBoolean type, boolean value) {
      this.type = type;
      this.value = value;
    }

    @Override
    public Formula toFormula() {
      throw new ParseCancellationException("toFormula shouldn't be called from a constant");
    }

    @Override
    public Formula getBit(int i) {
      assert i == 0;
      if (value) {
        return BooleanConstant.FALSE;
      } else {
        return BooleanConstant.TRUE;
      }
    }

    @Override
    public SpectraType getType() {
      return this.type;
    }

    @Override
    public int width() {
      return type.width();
    }
  }
  //</editor-fold>

  //<editor-fold desc="Variables">
  static class SpectraEnumVariable implements HigherOrderExpression {
    private final SpectraEnum type;
    private final int offset;

    private SpectraEnumVariable(SpectraEnum type, int offset) {
      this.type = type;
      this.offset = offset;
    }

    @Override
    public Formula toFormula() {
      throw new ParseCancellationException("toFormula shouldn't be called from a variable");
    }

    @Override
    public Formula getBit(int i) {
      return Literal.of(offset + i);
    }

    @Override
    public SpectraType getType() {
      return type;
    }

    @Override
    public int width() {
      return type.width();
    }
  }

  static class SpectraIntRangeVariable implements HigherOrderExpression {
    private final SpectraIntRange type;
    private final int offset;

    private SpectraIntRangeVariable(SpectraIntRange type, int offset) {
      this.type = type;
      this.offset = offset;
    }

    @Override
    public Formula toFormula() {
      throw new ParseCancellationException("toFormula shouldn't be called from a variable");
    }

    @Override
    public Formula getBit(int i) {
      return Literal.of(offset + i);
    }

    @Override
    public SpectraType getType() {
      return type;
    }

    @Override
    public int width() {
      return type.width();
    }
  }

  static class SpectraBooleanVariable implements HigherOrderExpression {
    private final SpectraBoolean type;
    private final int offset;

    private SpectraBooleanVariable(SpectraBoolean type, int offset) {
      this.type = type;
      this.offset = offset;
    }

    @Override
    public Formula toFormula() {
      throw new ParseCancellationException("toFormula shouldn't be called from a variable");
    }

    @Override
    public Formula getBit(int i) {
      assert i == 0;
      return Literal.of(offset);
    }

    @Override
    public SpectraType getType() {
      return type;
    }

    @Override
    public int width() {
      return type.width();
    }
  }

  static class SpectraArrayVariable implements HigherOrderExpression {
    private final SpectraArray type;
    private final int offset;

    private SpectraArrayVariable(SpectraArray type, int offset) {
      this.type = type;
      this.offset = offset;
    }

    @Override
    public Formula toFormula() {
      throw new ParseCancellationException("toFormula shouldn't be called from a variable");
    }

    public HigherOrderExpression of(int[] indices) {
      int compOffset = this.offset + type.getEnc(indices);
      SpectraType compType = getType();
      if (compType instanceof SpectraEnum) {
        return new SpectraEnumVariable((SpectraEnum) compType, compOffset);
      } else if (compType instanceof  SpectraIntRange) {
        return new SpectraIntRangeVariable((SpectraIntRange) compType, compOffset);
      } else if (compType instanceof SpectraBoolean) {
        return new SpectraBooleanVariable((SpectraBoolean) compType, compOffset);
      } else {
        throw new ParseCancellationException("Unknown component type");
      }
    }

    @Override
    public SpectraType getType() {
      return type.component;
    }

    @Override
    public Formula getBit(int i) {
      throw new ParseCancellationException("getBit() shouldn't bew called on an array");
    }

    @Override
    public int width() {
      return type.width();
    }
  }
  //</editor-fold>

  //<editor-fold desc="Expressions">
  static class EqualsExpression implements HigherOrderExpression {
    private final HigherOrderExpression left;
    private final HigherOrderExpression right;
    private final int width;

    public EqualsExpression(HigherOrderExpression left, HigherOrderExpression right) {
      this.left = left;
      this.right = right;
      width = left.width();
    }

    @Override
    public Formula toFormula() {
      List<Formula> conjuncts = new ArrayList<>();
      for (int i = 0; i < width; i++) {
        conjuncts.add(getBit(i));
      }
      return Conjunction.of(conjuncts);
    }

    @Override
    public Formula getBit(int i) {
      return Biconditional.of(left.getBit(i), right.getBit(i));
    }

    @Override
    public SpectraType getType() {
      return left.getType();
    }

    @Override
    public int width() {
      return width;
    }
  }

  static class NotEqualsExpression implements HigherOrderExpression {
    private final HigherOrderExpression left;
    private final HigherOrderExpression right;
    private final int width;

    public NotEqualsExpression(HigherOrderExpression left, HigherOrderExpression right) {
      this.left = left;
      this.right = right;
      width = left.width();
    }

    @Override
    public Formula toFormula() {
      List<Formula> disjuncts = new ArrayList<>();
      for (int i = 0; i < width; i++) {
        disjuncts.add(getBit(i));
      }
      return Disjunction.of(disjuncts);
    }

    @Override
    public Formula getBit(int i) {
      return Biconditional.of(left.getBit(i), right.getBit(i)).not();
    }

    @Override
    public SpectraType getType() {
      return left.getType();
    }

    @Override
    public int width() {
      return width;
    }
  }

  static class LessThanExpression implements HigherOrderExpression {
    private final HigherOrderExpression left;
    private final HigherOrderExpression right;
    private final int width;

    public LessThanExpression(HigherOrderExpression left, HigherOrderExpression right) {
      this.left = left;
      this.right = right;
      width = left.width();
    }

    @Override
    public Formula toFormula() {
      List<Formula> disjuncts = new ArrayList<>();
      for (int i = 0; i < width; i++) {
        disjuncts.add(getBit(i));
      }
      return Disjunction.of(disjuncts);
    }

    @Override
    public Formula getBit(int i) {
      return Biconditional.of(left.getBit(i).not(), right.getBit(i));
    }

    @Override
    public SpectraType getType() {
      return left.getType();
    }

    @Override
    public int width() {
      return width;
    }
  }

  static class LessThanOrEqualsExpression implements HigherOrderExpression {
    private final EqualsExpression eq;
    private final LessThanExpression lt;
    private final int width;

    public LessThanOrEqualsExpression(HigherOrderExpression left, HigherOrderExpression right) {
      this.eq = new EqualsExpression(left, right);
      this.lt = new LessThanExpression(left, right);
      width = left.width();
    }

    @Override
    public Formula toFormula() {
      return Disjunction.of(lt.toFormula(), eq.toFormula());
    }

    @Override
    public Formula getBit(int i) {
      throw new ParseCancellationException(
        "getBit() shouldn't be called on LessThanOrEqualsExpression objects");
    }

    @Override
    public SpectraType getType() {
      return eq.left.getType();
    }

    @Override
    public int width() {
      return width;
    }
  }

  static class SpecialNextExpression implements HigherOrderExpression {
    private final HigherOrderExpression inner;
    private final int width;

    public SpecialNextExpression(HigherOrderExpression inner) {
      this.inner = inner;
      width = inner.width();
    }

    @Override
    public Formula toFormula() {
      throw new ParseCancellationException(
        "toFormula() shouldn't be called on SpecialNextExpression objects");
    }

    @Override
    public Formula getBit(int i) {
      return XOperator.of(inner.getBit(i));
    }

    @Override
    public SpectraType getType() {
      return inner.getType();
    }

    @Override
    public int width() {
      return width;
    }
  }
  //</editor-fold>

  //</editor-fold>
}
