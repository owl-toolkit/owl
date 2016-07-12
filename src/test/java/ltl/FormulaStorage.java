package ltl;

import com.google.common.collect.ImmutableSet;
import ltl.parser.Parser;

import java.util.Set;
import java.util.stream.Collectors;

public class FormulaStorage {
    public static final Set<String> formulaeStrings = ImmutableSet.of("G a", "F G a", "G a | G b", "(G a) U (G b)", "X G b", "F F ((G a) & b)", "a & G b");
    public static final Set<Formula> formulae = ImmutableSet.copyOf(formulaeStrings.stream().map(Parser::formula).collect(Collectors.toSet()));
}
