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
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
import owl.grammar.SPECTRAParser.CounterContext;
import owl.grammar.SPECTRAParser.DeclContext;
import owl.grammar.SPECTRAParser.DefineContext;
import owl.grammar.SPECTRAParser.LtlContext;
import owl.grammar.SPECTRAParser.ModelContext;
import owl.grammar.SPECTRAParser.MonitorContext;
import owl.grammar.SPECTRAParser.PatternContext;
import owl.grammar.SPECTRAParser.PredicateContext;
import owl.grammar.SPECTRAParser.TypeDefContext;
import owl.grammar.SPECTRAParser.VarDefContext;
import owl.grammar.SPECTRAParser.VarTypeContext;
import owl.grammar.SPECTRAParser.WeightContext;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.spectra.ImmutableSpectra;
import owl.ltl.spectra.ImmutableSpectra.Builder;
import owl.ltl.spectra.Spectra;
import owl.ltl.spectra.expressios.HigherOrderExpression;
import owl.ltl.spectra.expressios.variables.SpectraArrayVariable;
import owl.ltl.spectra.expressios.variables.SpectraBooleanVariable;
import owl.ltl.spectra.expressios.variables.SpectraEnumVariable;
import owl.ltl.spectra.expressios.variables.SpectraIntRangeVariable;
import owl.ltl.spectra.types.SpectraArray;
import owl.ltl.spectra.types.SpectraBoolean;
import owl.ltl.spectra.types.SpectraEnum;
import owl.ltl.spectra.types.SpectraIntRange;
import owl.ltl.spectra.types.SpectraType;
import owl.util.annotation.CEntryPoint;

public final class SpectraParser {
  private static Set<String> typeConstants = new HashSet<>();

  private SpectraParser() {
  }

  @CEntryPoint
  public static Spectra parse(String input) {
    return parse(CharStreams.fromString(input));
  }

  private static Spectra parse(CharStream stream) {
    SPECTRALexer lexer = new SPECTRALexer(stream);
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    SPECTRAParser parser = new SPECTRAParser(tokens);
    parser.setErrorHandler(new BailErrorStrategy());

    ModelContext tree = parser.model();

    Builder builder = ImmutableSpectra.builder();

    builder.title((tree.name == null) ? "" : tree.name.getText());

    //<editor-fold desc="Process Imports [TODO: Do something with imports]">
    ArrayList<String> imports = new ArrayList<>();
    for (Token imp : tree.importURIS) {
      imports.add(imp.getText());
    }
    //</editor-fold>

    //<editor-fold desc="Preprocess Weights [unsupported]">
    for (DeclContext decl : tree.elements) {
      WeightContext weightC = decl.weight();

      if (weightC != null) {
        throw new UnsupportedOperationException("Weights are not supported yet.");
      }
    }
    //</editor-fold>

    //<editor-fold desc="Preprocess type definitions">
    Map<String, SpectraType> types = new HashMap<>();
    Queue<TypeDefContext> unresolvedTypeDefs = new LinkedList<>();

    for (DeclContext decl : tree.elements) {
      TypeDefContext typeDefC = decl.typeDef();

      if (typeDefC == null) {
        continue;
      }

      if (typeDefC.type.type != null && !types.containsKey(typeDefC.type.type.getText())) {
        unresolvedTypeDefs.add(typeDefC);
        continue;
      }

      SpectraType type = constructType(typeDefC.type, types);

      int[] dims = getDimensions(typeDefC.type.dimensions);
      if (dims.length > 0) {
        type = new SpectraArray(type, dims);
      }

      String typeName = typeDefC.name.getText();
      types.put(typeName, type);
    }

    while (!unresolvedTypeDefs.isEmpty()) {
      TypeDefContext typeDefC = unresolvedTypeDefs.poll();
      String typeRefName = typeDefC.type.type.getText();

      if (types.containsKey(typeRefName)) {
        SpectraType type = constructType(typeDefC.type, types);

        int[] dims = getDimensions(typeDefC.type.dimensions);
        if (dims.length > 0) {
          type = new SpectraArray(type, dims);
        }

        String typeName = typeDefC.name.getText();

        types.put(typeName, type);
      } else {
        unresolvedTypeDefs.add(typeDefC);
      }
    }
    //</editor-fold>

    //<editor-fold desc="Process I/O-variables">
    HashMap<String, HigherOrderExpression> variables = new HashMap<>();
    List<String> inputNames = new ArrayList<>();
    List<String> outputNames = new ArrayList<>();

    int offset = 0;

    for (DeclContext decl : tree.elements) {
      VarDefContext varDefC = decl.varDef();

      if (varDefC == null) {
        continue;
      }

      SpectraType type = (varDefC.var.type.type == null)
        ? constructType(varDefC.var.type, types) :
        types.get(varDefC.var.type.type.getText());

      int[] dims = getDimensions(varDefC.var.type.dimensions);
      if (dims.length > 0) {
        type = new SpectraArray(type, dims);
      }

      String varName = varDefC.var.name.getText();

      variables.put(varName, constructVariables(type, offset));

      List<String> varLiteralNames = new ArrayList<>();

      if (type.width() > 1) {
        for (int i = offset; i < offset + type.width(); i++) {
          Literal.of(i);
          varLiteralNames.add(varName + "_" + i);
        }
      } else {
        Literal.of(offset);
        varLiteralNames.add(varName);
      }

      offset += type.width();

      if (varDefC.kind.getText().equals("in")) {
        inputNames.addAll(varLiteralNames);
      } else {
        outputNames.addAll(varLiteralNames);
      }
    }
    BitSet inputs = new BitSet();
    IntStream.range(0, inputNames.size()).forEach(inputs::set);

    List<String> variableNames = Stream.concat(
      inputNames.stream(),
      outputNames.stream()
    ).collect(Collectors.toList());

    BitSet outputs = new BitSet();
    IntStream.range(inputNames.size(), variableNames.size()).forEach(outputs::set);

    builder.inputs(inputs);
    builder.outputs(outputs);
    builder.variables(List.copyOf(variableNames));
    //</editor-fold>

    //<editor-fold desc="Preprocess Monitors [unsupported]">
    for (DeclContext decl : tree.elements) {
      MonitorContext monitorC = decl.monitor();

      if (monitorC != null) {
        throw new UnsupportedOperationException("Monitors are not supported yet.");
      }
    }
    //</editor-fold>

    //<editor-fold desc="Preprocess Predicates [unsupported]">
    for (DeclContext decl : tree.elements) {
      PredicateContext predicateC = decl.predicate();

      if (predicateC != null) {
        throw new UnsupportedOperationException("Predicates are not supported yet.");
      }
    }
    //</editor-fold>

    //<editor-fold desc="Preprocess Patterns [unsupported]">
    for (DeclContext decl : tree.elements) {
      PatternContext patternC = decl.pattern();

      if (patternC != null) {
        throw new UnsupportedOperationException("Patterns are not supported yet.");
      }
    }
    //</editor-fold>

    //<editor-fold desc="Preprocess Counters [unsupported]">
    for (DeclContext decl : tree.elements) {
      CounterContext counterC = decl.counter();

      if (counterC != null) {
        throw new UnsupportedOperationException("Counters are not supported yet.");
      }
    }
    //</editor-fold>

    //<editor-fold desc="Preprocess Defines [unsupported]">
    for (DeclContext decl : tree.elements) {
      DefineContext defineC = decl.define();

      if (defineC != null) {
        throw new UnsupportedOperationException("Defines are not supported yet.");
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
          formula = GOperator.of(FOperator.of(formula));
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

    builder.thetaE(initialEnv);
    builder.thetaS(initialSys);
    builder.psiE(safetyEnv);
    builder.psiS(safetySys);
    builder.phiE(livenessEnv);
    builder.phiS(livenessSys);
    //</editor-fold>


    return builder.build();
  }

  public static Spectra parse(InputStream input, Charset charset) throws IOException {
    return parse(CharStreams.fromStream(input, charset));
  }

  public static Spectra parse(Reader input) throws IOException {
    return parse(CharStreams.fromReader(input));
  }

  //<editor-fold desc="Type/Variable construction">
  private static int[] getDimensions(List<Token> dims) {
    return dims.stream().mapToInt(dim -> Integer.parseInt(dim.getText())).toArray();
  }

  private static SpectraType constructType(VarTypeContext varTypeC,
                                           Map<String, SpectraType> types) {
    SpectraType type;
    if (varTypeC.name != null && varTypeC.name.getText().equals("boolean")) {
      type = new SpectraBoolean();
    } else if (varTypeC.INT_S() != null) {
      int from = Integer.parseInt(varTypeC.subr.from.getText());
      int to = Integer.parseInt(varTypeC.subr.to.getText());
      type = new SpectraIntRange(from, to);
    } else if (varTypeC.LCPAR() != null) {
      ArrayList<String> values = varTypeC.consts.stream()
        .map(RuleContext::getText)
        .collect(Collectors.toCollection(ArrayList::new));
      type = new SpectraEnum(values);
      typeConstants.addAll(values);
    } else if (varTypeC.type != null) {
      type = constructTypeFromType(types.get(varTypeC.type.getText()));
    } else {
      throw new ParseCancellationException("Unrecognizable type: " + varTypeC.getText());
    }

    return type;
  }

  private static SpectraType constructTypeFromType(SpectraType origin) {
    SpectraType type;
    if (origin instanceof SpectraBoolean) {
      type = new SpectraBoolean();
    } else if (origin instanceof SpectraIntRange) {
      SpectraIntRange castOrigin = (SpectraIntRange) origin;
      type = new SpectraIntRange(castOrigin.getFrom(), castOrigin.getTo());
    } else if (origin instanceof SpectraEnum) {
      SpectraEnum castOrigin = (SpectraEnum) origin;
      type = new SpectraEnum(castOrigin.getValues());
    } else if (origin instanceof SpectraArray) {
      SpectraArray castOrigin = (SpectraArray) origin;
      type = new SpectraArray(castOrigin.getComponent(), castOrigin.getDimensions());
    } else {
      throw new ParseCancellationException("Unknown referenced type");
    }

    return type;
  }

  private static HigherOrderExpression constructVariables(SpectraType type, int offset) {
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

    return variable;
  }
  //</editor-fold>
}