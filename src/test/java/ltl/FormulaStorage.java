package ltl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import ltl.parser.Parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class FormulaStorage {
    public static final Set<String> formulaeStrings = ImmutableSet.of("G a", "F G a", "G a | G b", "(G a) U (G b)", "X G b", "F F ((G a) & b)", "a & G b");

    public static final Set<Formula> formulae = ImmutableSet.copyOf(formulaeStrings.stream().map(Parser::formula).collect(Collectors.toSet()));

    public static final Map<File, List<Formula>> formulaSets = loadSets();

    public static Map<File, List<Formula>> loadSets() {
        ImmutableMap.Builder<File, List<Formula>> builder = ImmutableMap.builder();

        File dir = new File("/Users/sickert/Documents/workspace/Rabinizer/src/test/resources/ltl/");

        assertTrue(dir.isDirectory());

        for (File file : dir.listFiles()) {
            ImmutableList.Builder<Formula> listBuilder = ImmutableList.builder();

            if (file.isHidden()) {
                continue;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();

                while (line != null) {
                    line.trim();

                    if (!line.isEmpty()) {
                        Formula formula = Parser.formula(line);
                        listBuilder.add(formula);
                    }

                    line = reader.readLine();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            builder.put(file, listBuilder.build());
        }

        return builder.build();
    }

}
