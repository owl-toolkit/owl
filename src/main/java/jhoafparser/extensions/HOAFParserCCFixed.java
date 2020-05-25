/*
 * This file is from jhoafparser.
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

/* Generated By:JavaCC: Do not edit this line. HOAFParserCC.java */
package jhoafparser.extensions;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerFactory;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.consumer.HOAIntermediateResolveAliases;
import jhoafparser.parser.HOAFParserSettings;
import jhoafparser.parser.generated.HOAFParserCCConstants;
import jhoafparser.parser.generated.HOAFParserCCTokenManager;
import jhoafparser.parser.generated.ParseException;
import jhoafparser.parser.generated.SimpleCharStream;
import jhoafparser.parser.generated.Token;

@SuppressWarnings("PMD")
/**
 * The generated parser.
 */
public class HOAFParserCCFixed implements HOAFParserCCConstants {

        private static class AbortedException extends RuntimeException {
        }

        //-----------------------------------------------------------------------------------
        // static member variables
        //-----------------------------------------------------------------------------------

        private static HOAFParserCCFixed theParser = null;
        private static HOAConsumerFactory consumerFactory = null;
        private static HOAConsumer consumer = null;
        private static HOAFParserSettings settings;
        private static Integer currentState;
        private static boolean currentStateHasStateLabel;


        //-----------------------------------------------------------------------------------
        // Initialization
        //-----------------------------------------------------------------------------------

        // Parse HOA automaton

        private static void initializeParser(Reader str)
        {
                if (theParser == null) {
                        theParser = new HOAFParserCCFixed(str);
                } else {
                        ReInit(str);
                }
        }


        //-----------------------------------------------------------------------------------
        // Methods for invokation of the parser
        //-----------------------------------------------------------------------------------

        /**
	 * Entry point for parsing a single automaton in HOA format (with default settings).
	 * <br> Note: this parser is non-reentrant, i.e., it is
	 * not possible to parse two streams at the same time!
	 *
	 * @param str The input stream with the automaton description
	 * @param userConsumer The consumer that receives the notifications about the parsed elements from the parser
	 */
        public static void parseHOA(Reader str, HOAConsumer userConsumer) throws ParseException {
                parseHOA(str, userConsumer, null);
        }

        /**
	 * Entry point for parsing a single automaton in HOA format.
	 * <br> Note: this parser is non-reentrant, i.e., it is
	 * not possible to parse two streams at the same time!
	 *
	 * @param str The input stream with the automaton description
	 * @param userConsumer The consumer that receives the notifications about the parsed elements from the parser
	 * @param settings Settings for the parser (may be {@code null})
	 */
        public static void parseHOA(Reader str, final HOAConsumer userConsumer, HOAFParserSettings settings) throws ParseException
        {
                // (Re)start parser
                initializeParser(str);

                if (settings == null) {
                        // default settings
                        settings = new HOAFParserSettings();
                }
                HOAFParserCCFixed.settings = settings;

                consumerFactory = factoryFromSettings(new HOAConsumerFactory() {
                        @Override
                        public HOAConsumer getNewHOAConsumer() {
                                return userConsumer;
                        }
                });

                consumer = consumerFactory.getNewHOAConsumer();
                newAutomaton();

                // Parse
                try {
                        SingleAutomaton();
                }
                finally {
                        consumer = null;
                }
        }

        /**
	 * Entry point for parsing a stream of automata in HOA format.
	 * <br> Note: this parser is non-reentrant, i.e., it is
	 * not possible to parse two streams at the same time!
	 *
	 * @param str The input stream with the automaton description
	 * @param userFactory A factory that produces HOAConsumers, one for each automaton encountered,
	 *                      that receive the notifications about the parsed elements from the parser
	 * @param settings Settings for the parser (may be {@code null})
	 */
        public static void parseHOA(Reader str, final HOAConsumerFactory userFactory, HOAFParserSettings settings) throws ParseException
        {
                // (Re)start parser
                initializeParser(str);

                if (settings == null) {
                        // default settings
                        settings = new HOAFParserSettings();
                }
                HOAFParserCCFixed.settings = settings;

                consumerFactory = factoryFromSettings(userFactory);

                // Parse
                try {
                        while (true) {
                                try {
                                        Automata();
                                        if (consumer != null) {
                                                // the file/stream ended early
                                                consumer.notifyAbort();
                                                consumer = null;
                                        }
                                        break;
                                } catch (AbortedException e) {
                                        if (consumer == null) {
                                                // special case: --ABORT-- directly at the beginning,
                                                // construct consumer...
                                                consumer = consumerFactory.getNewHOAConsumer();
                                        }
                                        consumer.notifyAbort();
                                        consumer = null;
                                }
                        }
                } finally {
                        consumer = null;
                        consumerFactory = null;
                }
        }

        public static void notifyAbort() {
                throw new AbortedException();
        }

        private static HOAConsumerFactory factoryFromSettings(final HOAConsumerFactory userFactory) {
                return new HOAConsumerFactory() {
                        public HOAConsumer getNewHOAConsumer() {
                                HOAConsumer consumer = userFactory.getNewHOAConsumer();

                                if (consumer.parserResolvesAliases()) {
                                        consumer = new HOAIntermediateResolveAliases(consumer);
                                }

                                if (HOAFParserCCFixed.settings.getFlagValidate()) {
                                        consumer = new HOAIntermediateCheckValidity(consumer);
                                        ((HOAIntermediateCheckValidity)consumer).setFlagRejectSemanticMiscHeaders(
                                          HOAFParserCCFixed.settings.getFlagRejectSemanticMiscHeaders());
                                }

                                return consumer;
                        }
                };
        }

        private static void newAutomaton() {
                currentState = null;
                currentStateHasStateLabel = false;
        }

//-----------------------------------------------------------------------------------
// Top-level production
//-----------------------------------------------------------------------------------
  static final public void Automata() throws ParseException {
    label_1:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case 0:
        jj_consume_token(0);
                 {if (true) return;}
        break;
      case HOA:
                consumer=consumerFactory.getNewHOAConsumer();
                newAutomaton();
        Automaton();
        break;
      default:
        jj_la1[0] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case 0:
      case HOA:
        ;
        break;
      default:
        jj_la1[1] = jj_gen;
        break label_1;
      }
    }
  }

  static final public void SingleAutomaton() throws ParseException {
    Automaton();
    jj_consume_token(0);
  }

  static final public void Automaton() throws ParseException {
    Header();
    jj_consume_token(BODY);
                consumer.notifyBodyStart();
    Body();
    jj_consume_token(END);
                if (currentState != null) {
                        consumer.notifyEndOfState(currentState);
                }
                consumer.notifyEnd();
                consumer = null;
  }

  static final public void Header() throws ParseException {
    Format();
    label_2:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STATES:
      case START:
      case AP:
      case ALIAS:
      case ACCEPTANCE:
      case ACCNAME:
      case TOOL:
      case NAME:
      case PROPERTIES:
      case REG_HEADERNAME:
        ;
        break;
      default:
        jj_la1[2] = jj_gen;
        break label_2;
      }
      HeaderItem();
    }
  }

  static final public void Format() throws ParseException {
        String version;
    jj_consume_token(HOA);
    version = Identifier();
                // TODO: Check version
                consumer.notifyHeaderStart(version);
  }

  static final public void HeaderItem() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case STATES:
      HeaderItemStates();
      break;
    case START:
      HeaderItemStart();
      break;
    case AP:
      HeaderItemAP();
      break;
    case ALIAS:
      HeaderItemAlias();
      break;
    case ACCEPTANCE:
      HeaderItemAcceptance();
      break;
    case ACCNAME:
      HeaderItemAccName();
      break;
    case TOOL:
      HeaderItemTool();
      break;
    case NAME:
      HeaderItemName();
      break;
    case PROPERTIES:
      HeaderItemProperties();
      break;
    case REG_HEADERNAME:
      HeaderItemMisc();
      break;
    default:
      jj_la1[3] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  static final public void HeaderItemStates() throws ParseException {
        Integer numberOfStates;
    jj_consume_token(STATES);
    numberOfStates = Integer();
                consumer.setNumberOfStates(numberOfStates);
  }

  static final public void HeaderItemStart() throws ParseException {
        List<Integer> startStates = new ArrayList<Integer>();
        int startState;
    jj_consume_token(START);
    startState = Integer();
                                 startStates.add(startState);
    label_3:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AND:
        ;
        break;
      default:
        jj_la1[4] = jj_gen;
        break label_3;
      }
      jj_consume_token(AND);
      startState = Integer();
                                  startStates.add(startState);
    }
                consumer.addStartStates(startStates);
  }

  static final public void HeaderItemAP() throws ParseException {
        int apCount;
        List<String> aps = new ArrayList<String>();
        Set<String> apSet = new HashSet<String>();
        String ap;
    jj_consume_token(AP);
    apCount = Integer();
    label_4:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case REG_STRING:
        ;
        break;
      default:
        jj_la1[5] = jj_gen;
        break label_4;
      }
      ap = QuotedString();
              if (apSet.contains(ap)) {
                 {if (true) throw new ParseException("Atomic proposition \u005c""+ap+"\u005c" is a duplicate!");}
              }
              aps.add(ap);
              apSet.add(ap);
    }
                if (aps.size() != apCount) {
                        {if (true) throw new ParseException("Number of provided APs ("+aps.size()+") "+aps.toString()+" does not match number of APs that was specified ("+apCount+")");}
                }
                consumer.setAPs(aps);
  }

  static final public void HeaderItemAlias() throws ParseException {
        String alias;
        BooleanExpression<AtomLabel> labelExpression;
    jj_consume_token(ALIAS);
    alias = AliasName();
    labelExpression = LabelExpr();
                consumer.addAlias(alias, labelExpression);
  }

  static final public void HeaderItemAcceptance() throws ParseException {
        int accSetCount;
        BooleanExpression<AtomAcceptance> accExpr;
    jj_consume_token(ACCEPTANCE);
    accSetCount = Integer();
    accExpr = AcceptanceCondition();
                consumer.setAcceptanceCondition(accSetCount, accExpr);
  }

  static final public BooleanExpression<AtomAcceptance> AcceptanceCondition() throws ParseException {
        BooleanExpression<AtomAcceptance> left, right;
    left = AcceptanceConditionAnd();
    label_5:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case OR:
        ;
        break;
      default:
        jj_la1[6] = jj_gen;
        break label_5;
      }
      jj_consume_token(OR);
      right = AcceptanceConditionAnd();
                                                 left = left.or(right);
    }
          {if (true) return left;}
    throw new Error("Missing return statement in function");
  }

  static final public BooleanExpression<AtomAcceptance> AcceptanceConditionAnd() throws ParseException {
        BooleanExpression<AtomAcceptance> left, right;
    left = AcceptanceConditionAtom();
    label_6:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AND:
        ;
        break;
      default:
        jj_la1[7] = jj_gen;
        break label_6;
      }
      jj_consume_token(AND);
      right = AcceptanceConditionAtom();
                                                   left = left.and(right);
    }
                {if (true) return left;}
    throw new Error("Missing return statement in function");
  }

  static final public BooleanExpression<AtomAcceptance> AcceptanceConditionAtom() throws ParseException {
        BooleanExpression<AtomAcceptance> expression = null;
        boolean negated = false;
        int accSet;
        AtomAcceptance acc;
        AtomAcceptance.Type temporalOperator;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LPARENTH:
      jj_consume_token(LPARENTH);
      expression = AcceptanceCondition();
      jj_consume_token(RPARENTH);
      break;
    case REG_IDENT:
      temporalOperator = AcceptanceConditionTemporalOperator();
      jj_consume_token(LPARENTH);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case NOT:
        jj_consume_token(NOT);
                   negated = true;
        break;
      default:
        jj_la1[8] = jj_gen;
        ;
      }
      accSet = Integer();
      jj_consume_token(RPARENTH);
               acc = new AtomAcceptance(temporalOperator, accSet, negated);
               expression = new BooleanExpression<AtomAcceptance>(acc);
      break;
    case TRUE:
      jj_consume_token(TRUE);
                   expression = new BooleanExpression<AtomAcceptance>(true);
      break;
    case FALSE:
      jj_consume_token(FALSE);
                   expression = new BooleanExpression<AtomAcceptance>(false);
      break;
    default:
      jj_la1[9] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
      {if (true) return expression;}
    throw new Error("Missing return statement in function");
  }

  static final public AtomAcceptance.Type AcceptanceConditionTemporalOperator() throws ParseException {
  String temporalOperator;
    temporalOperator = Identifier();
      if (temporalOperator.equals("Fin")) {
         {if (true) return AtomAcceptance.Type.TEMPORAL_FIN;}
      } else if (temporalOperator.equals("Inf")) {
         {if (true) return AtomAcceptance.Type.TEMPORAL_INF;}
      } else {
         {if (true) throw new ParseException("Illegal operator '"+temporalOperator+"' in acceptance condition, expected either 'Fin' or 'Inf'");}
      }
    throw new Error("Missing return statement in function");
  }

  static final public void HeaderItemAccName() throws ParseException {
        String accName;
        List<Object> extraInfo = new ArrayList<Object>();
        String identifier;
        Integer integer;
    jj_consume_token(ACCNAME);
    accName = Identifier();
    label_7:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case REG_INT:
      case REG_IDENT:
        ;
        break;
      default:
        jj_la1[10] = jj_gen;
        break label_7;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case REG_IDENT:
        identifier = Identifier();
                                       extraInfo.add(identifier);
        break;
      case REG_INT:
        integer = Integer();
                                 extraInfo.add(integer);
        break;
      default:
        jj_la1[11] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
                if (settings == null || !settings.getFlagIgnoreAccName()) {
                        consumer.provideAcceptanceName(accName, extraInfo);
                }
  }

  static final public void HeaderItemTool() throws ParseException {
        String tool;
        String version = null;
    jj_consume_token(TOOL);
    tool = QuotedString();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case REG_STRING:
      version = QuotedString();
      break;
    default:
      jj_la1[12] = jj_gen;
      ;
    }
                consumer.setTool(tool, version);
  }

  static final public void HeaderItemName() throws ParseException {
        String name;
    jj_consume_token(NAME);
    name = QuotedString();
                consumer.setName(name);
  }

  static final public void HeaderItemProperties() throws ParseException {
        List<String> properties = new ArrayList<String>();
        String property;
    jj_consume_token(PROPERTIES);
    label_8:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case REG_IDENT:
        ;
        break;
      default:
        jj_la1[13] = jj_gen;
        break label_8;
      }
      property = Identifier();
                                  properties.add(property);
    }
                consumer.addProperties(properties);
  }

  static final public void HeaderItemMisc() throws ParseException {
        String headerName;
        List<Object> content = new ArrayList<Object>();
        Object o;
    headerName = HeaderName();
    label_9:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case TRUE:
      case FALSE:
      case REG_INT:
      case REG_STRING:
      case REG_IDENT:
        ;
        break;
      default:
        jj_la1[14] = jj_gen;
        break label_9;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case REG_INT:
        o = Integer();
                          content.add(o);
        break;
      case REG_IDENT:
        o = Identifier();
                             content.add(o);
        break;
      case REG_STRING:
        o = QuotedString();
                               content.add(o);
        break;
      case TRUE:
        o = jj_consume_token(TRUE);
                       content.add("t");
        break;
      case FALSE:
        o = jj_consume_token(FALSE);
                        content.add("f");
        break;
      default:
        jj_la1[15] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
                consumer.addMiscHeader(headerName, content);
  }

  static final public void Body() throws ParseException {
    label_10:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STATE:
        ;
        break;
      default:
        jj_la1[16] = jj_gen;
        break label_10;
      }
      StateName();
      Edges();
    }
  }

  static final public void StateName() throws ParseException {
        BooleanExpression<AtomLabel> labelExpr = null;
        Integer state;
        String stateComment = null;
        List<Integer> accSignature = null;
    jj_consume_token(STATE);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LBRACKET:
      labelExpr = Label();
      break;
    default:
      jj_la1[17] = jj_gen;
      ;
    }
    state = Integer();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case REG_STRING:
      stateComment = QuotedString();
      break;
    default:
      jj_la1[18] = jj_gen;
      ;
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LCURLY:
      accSignature = AcceptanceSignature();
      break;
    default:
      jj_la1[19] = jj_gen;
      ;
    }
                if (currentState != null) {
                        consumer.notifyEndOfState(currentState);
                }
                consumer.addState(state, stateComment, labelExpr, accSignature);
                // store global information:
                currentState = state;
                currentStateHasStateLabel = (labelExpr != null);
  }

  static final public void Edges() throws ParseException {
    label_11:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LBRACKET:
      case REG_INT:
        ;
        break;
      default:
        jj_la1[20] = jj_gen;
        break label_11;
      }
      Edge();
    }
  }

  static final public void Edge() throws ParseException {
        BooleanExpression<AtomLabel> labelExpr = null;
        List<Integer> conjStates;
        List<Integer> accSignature = null;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LBRACKET:
      labelExpr = Label();
      break;
    default:
      jj_la1[21] = jj_gen;
      ;
    }
    conjStates = StateConjunction();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LCURLY:
      accSignature = AcceptanceSignature();
      break;
    default:
      jj_la1[22] = jj_gen;
      ;
    }
                if (labelExpr != null || currentStateHasStateLabel) {
                        consumer.addEdgeWithLabel(currentState, labelExpr, conjStates, accSignature);
                } else {
                        consumer.addEdgeImplicit(currentState, conjStates, accSignature);

                }
  }

  static final public BooleanExpression<AtomLabel> Label() throws ParseException {
        BooleanExpression<AtomLabel> labelExpr;
    jj_consume_token(LBRACKET);
    labelExpr = LabelExpr();
    jj_consume_token(RBRACKET);
         {if (true) return labelExpr;}
    throw new Error("Missing return statement in function");
  }

  static final public BooleanExpression<AtomLabel> LabelExpr() throws ParseException {
        BooleanExpression<AtomLabel> left, right;
    left = LabelExprAnd();
    label_12:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case OR:
        ;
        break;
      default:
        jj_la1[23] = jj_gen;
        break label_12;
      }
      jj_consume_token(OR);
      right = LabelExprAnd();
                                        left = left.or(right);
    }
          {if (true) return left;}
    throw new Error("Missing return statement in function");
  }

  static final public BooleanExpression<AtomLabel> LabelExprAnd() throws ParseException {
        BooleanExpression<AtomLabel> left, right;
    left = LabelExprAtom();
    label_13:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AND:
        ;
        break;
      default:
        jj_la1[24] = jj_gen;
        break label_13;
      }
      jj_consume_token(AND);
      right = LabelExprAtom();
                                          left = left.and(right);
    }
          {if (true) return left;}
    throw new Error("Missing return statement in function");
  }

  static final public BooleanExpression<AtomLabel> LabelExprAtom() throws ParseException {
        BooleanExpression<AtomLabel> expression = null;
        int apIndex;
        String aliasName;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LPARENTH:
      jj_consume_token(LPARENTH);
      expression = LabelExpr();
      jj_consume_token(RPARENTH);
      break;
    case TRUE:
      jj_consume_token(TRUE);
                      expression = new BooleanExpression<AtomLabel>(true);
      break;
    case FALSE:
      jj_consume_token(FALSE);
                      expression = new BooleanExpression<AtomLabel>(false);
      break;
    case NOT:
      jj_consume_token(NOT);
      expression = LabelExprAtom();
                                                 expression = expression.not();
      break;
    case REG_INT:
      apIndex = Integer();
                                  expression = new BooleanExpression<AtomLabel>(AtomLabel.createAPIndex(apIndex));
      break;
    case REG_ANAME:
      aliasName = AliasName();
                                      expression = new BooleanExpression<AtomLabel>(AtomLabel.createAlias(aliasName));
      break;
    default:
      jj_la1[25] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
         {if (true) return expression;}
    throw new Error("Missing return statement in function");
  }

  static final public List<Integer> AcceptanceSignature() throws ParseException {
        List<Integer> accSignature = new ArrayList<Integer>();
        Integer accSet;
    jj_consume_token(LCURLY);
    label_14:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case REG_INT:
        ;
        break;
      default:
        jj_la1[26] = jj_gen;
        break label_14;
      }
      accSet = Integer();
                              accSignature.add(accSet);
    }
    jj_consume_token(RCURLY);
         {if (true) return accSignature;}
    throw new Error("Missing return statement in function");
  }

  static final public List<Integer> StateConjunction() throws ParseException {
        List<Integer> conjStates = new ArrayList<Integer>();
        Integer state;
    state = Integer();
                           conjStates.add(state);
    label_15:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AND:
        ;
        break;
      default:
        jj_la1[27] = jj_gen;
        break label_15;
      }
      jj_consume_token(AND);
      state = Integer();
                                   conjStates.add(state);
    }
         {if (true) return conjStates;}
    throw new Error("Missing return statement in function");
  }

//-----------------------------------------------------------------------------------
// Miscellaneous stuff
//-----------------------------------------------------------------------------------

// Identifier (returns String)
  static final public String Identifier() throws ParseException {
    jj_consume_token(REG_IDENT);
                      {if (true) return getToken(0).image;}
    throw new Error("Missing return statement in function");
  }

// Integer
  static final public int Integer() throws ParseException {
    jj_consume_token(REG_INT);
                    {if (true) return Integer.parseInt(getToken(0).image);}
    throw new Error("Missing return statement in function");
  }

  static final public String QuotedString() throws ParseException {
        String s;
    jj_consume_token(REG_STRING);
                s = getToken(0).image;
                // remove outer quotes "
                s = s.substring(1, s.length()-1);
                // TODO: dequote inside string
                {if (true) return s;}
    throw new Error("Missing return statement in function");
  }

  static final public String HeaderName() throws ParseException {
    jj_consume_token(REG_HEADERNAME);
          String s = getToken(0).image;
          // remove :
          {if (true) return s.substring(0, s.length()-1);}
    throw new Error("Missing return statement in function");
  }

  static final public String AliasName() throws ParseException {
    jj_consume_token(REG_ANAME);
          String s = getToken(0).image;
          // remove @
          {if (true) return s.substring(1);}
    throw new Error("Missing return statement in function");
  }

  static private boolean jj_initialized_once = false;
  /** Generated Token Manager. */
  static public HOAFParserCCTokenManager token_source;
  static SimpleCharStream jj_input_stream;
  /** Current token. */
  static public Token token;
  /** Next token. */
  static public Token jj_nt;
  static private int jj_ntk;
  static private int jj_gen;
  static final private int[] jj_la1 = new int[28];
  static private int[] jj_la1_0;
  static private int[] jj_la1_1;
  static {
      jj_la1_init_0();
      jj_la1_init_1();
   }
   private static void jj_la1_init_0() {
      jj_la1_0 = new int[] {0x1001,0x1001,0x7fc000,0x7fc000,0x1000000,0x0,0x2000000,0x1000000,0x800000,0x4000000,0x0,0x0,0x0,0x0,0x0,0x0,0x2000,0x10000000,0x0,0x40000000,0x10000000,0x10000000,0x40000000,0x2000000,0x1000000,0x4800000,0x0,0x1000000,};
   }
   private static void jj_la1_init_1() {
      jj_la1_1 = new int[] {0x0,0x0,0x20,0x20,0x0,0x8,0x0,0x0,0x0,0x13,0x14,0x14,0x8,0x10,0x1f,0x1f,0x0,0x0,0x8,0x0,0x4,0x0,0x0,0x0,0x0,0x47,0x4,0x0,};
   }

  /** Constructor with InputStream. */
  public HOAFParserCCFixed(java.io.InputStream stream) {
     this(stream, null);
  }
  /** Constructor with InputStream and supplied encoding */
  public HOAFParserCCFixed(java.io.InputStream stream, String encoding) {
    if (jj_initialized_once) {
      System.out.println("ERROR: Second call to constructor of static parser.  ");
      System.out.println("       You must either use ReInit() or set the JavaCC option STATIC to false");
      System.out.println("       during parser generation.");
      throw new Error();
    }
    jj_initialized_once = true;
    try { jj_input_stream = new SimpleCharStream(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source = new HOAFParserCCTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 28; i++) jj_la1[i] = -1;
  }

  /** Reinitialise. */
  static public void ReInit(java.io.InputStream stream) {
     ReInit(stream, null);
  }
  /** Reinitialise. */
  static public void ReInit(java.io.InputStream stream, String encoding) {
    try { jj_input_stream.ReInit(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 28; i++) jj_la1[i] = -1;
  }

  /** Constructor. */
  public HOAFParserCCFixed(java.io.Reader stream) {
    if (jj_initialized_once) {
      System.out.println("ERROR: Second call to constructor of static parser. ");
      System.out.println("       You must either use ReInit() or set the JavaCC option STATIC to false");
      System.out.println("       during parser generation.");
      throw new Error();
    }
    jj_initialized_once = true;
    jj_input_stream = new SimpleCharStream(stream, 1, 1);
    token_source = new HOAFParserCCTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 28; i++) jj_la1[i] = -1;
  }

  /** Reinitialise. */
  static public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 28; i++) jj_la1[i] = -1;
  }

  /** Constructor with generated Token Manager. */
  public HOAFParserCCFixed(HOAFParserCCTokenManager tm) {
    if (jj_initialized_once) {
      System.out.println("ERROR: Second call to constructor of static parser. ");
      System.out.println("       You must either use ReInit() or set the JavaCC option STATIC to false");
      System.out.println("       during parser generation.");
      throw new Error();
    }
    jj_initialized_once = true;
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 28; i++) jj_la1[i] = -1;
  }

  /** Reinitialise. */
  public void ReInit(HOAFParserCCTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 28; i++) jj_la1[i] = -1;
  }

  static private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }


/** Get the next Token. */
  static final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

/** Get the specific Token. */
  static final public Token getToken(int index) {
    Token t = token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  static private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  static private java.util.List<int[]> jj_expentries = new java.util.ArrayList<int[]>();
  static private int[] jj_expentry;
  static private int jj_kind = -1;

  /** Generate ParseException. */
  static public ParseException generateParseException() {
    jj_expentries.clear();
    boolean[] la1tokens = new boolean[40];
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 28; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
          if ((jj_la1_1[i] & (1<<j)) != 0) {
            la1tokens[32+j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 40; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.add(jj_expentry);
      }
    }
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = jj_expentries.get(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }
}