#include <bitset>
#include <deque>
#include <iostream>
#include <set>

#include "owl.h"

using namespace owl;

Formula parse_ltl(const OwlThread &owl) {
    FormulaFactory factory = owl.createFormulaFactory();

    // Create mapping from string to [0,n[ for the parser.
    std::vector<std::string> mapping = std::vector<std::string>({"a", "b", "c"});

    // Parse with provided mapping
    return factory.parse("X a & (G F G c) | b | (G F a & F G ! a)", mapping);
}

Formula parse_tlsf(const OwlThread& owl) {
    FormulaFactory factory = owl.createFormulaFactory();

    std::vector<std::string> mapping;
    int num_inputs = -1;

    Formula parsed_formula = factory.parseTlsf("INFO {\n"
       "  TITLE:       \"LTL -> DBA  -  Example 12\"\n"
       "  DESCRIPTION: \"One of the Acacia+ example files\"\n"
       "  SEMANTICS:   Moore\n"
       "  TARGET:      Mealy\n"
       "}\n"
       "// TEST COMMENT\n"
       "MAIN {\n"
       "// TEST COMMENT\n"
       "  INPUTS {\n"
       "    p;\n"
       "    q;\n"
       "  }\n"
       "// TEST COMMENT\n"
       "  OUTPUTS {\n"
       "    acc;\n"
       "  }\n"
       "// TEST COMMENT\n"
       "  GUARANTEE {\n"
       "// TEST COMMENT\n"
       "    (G p -> F q) && (G !p <-> F !q)\n"
       "      && G F acc;\n"
       "  }\n"
       "// TEST COMMENT\n"
       "}", mapping, num_inputs);

    std::cout << "Variables: " << std::endl;

    for (const auto & entry : mapping) {
        std::cout << entry << std::endl;
    }

    std::cout << "Number of Inputs: " << num_inputs << std::endl;

    return parsed_formula;
}

Formula create_formula(const OwlThread& owl) {
    FormulaFactory factory = owl.createFormulaFactory();

    Formula literal = factory.createLiteral(2);
    Formula gOperator = factory.createGOperator(literal);
    Formula literal2 = factory.createNegatedLiteral(1);
    Formula disjunction = factory.createDisjunction(gOperator, literal2);
    Formula literal1 = factory.createLiteral(2);
    Formula imp = factory.createImplication(literal1, disjunction);
    Formula input0 = factory.createLiteral(0);
    Formula output0 = factory.createLiteral(5);
    Formula output1 = factory.createLiteral(6);
    Formula iff1 = factory.createGOperator(factory.createBiimplication(input0, output0));
    Formula iff2 = factory.createGOperator(factory.createBiimplication(input0, factory.createConjunction(output0, output1)));
    Formula imp2 = factory.createConjunction(iff1, imp, iff2);

    imp2.print();
    return imp2;
}

void dpa_example(const OwlThread& owl, const Formula& formula) {
    EmersonLeiAutomaton automaton = owl.createAutomaton(formula, false, true, NEVER, true);
    std::cout << "# Automata constructed: " << automaton.automata().size() << std::endl;

    Automaton dpa = automaton.automata()[0];

    std::cout << "Automaton constructed with ";

    switch(dpa.acceptance()) {
        case PARITY_MIN_EVEN:
            std::cout << "(min,even) parity" << std::endl;
            break;

        case PARITY_MAX_EVEN:
            std::cout << "(max,even) parity" << std::endl;
            break;

        case PARITY_MIN_ODD:
            std::cout << "(min,odd) parity" << std::endl;
            break;

        case PARITY_MAX_ODD:
            std::cout << "(max,odd) parity" << std::endl;
            break;

        default:
            std::cout << "not a dpa" << std::endl;
    }

    std::cout << "Transition Function:" << std::endl;

    std::set<int> seenStates = std::set<int>();
    std::deque<int> stateQueue = std::deque<int>();

    // The initial state is always identified with 0.
    stateQueue.push_back(0);

    while (!stateQueue.empty()) {
        const int currentState = stateQueue.front();
        stateQueue.pop_front();
        seenStates.insert(currentState);

        std::cout << "State: " << currentState << "\n";

        std::vector<bool> letter = {true, false, true, false, false, true, false, true, false, true, false, false, false, false};
        const auto &map = dpa.edges(currentState);
        auto x = map.edge(letter);
    }
}

void visit_tree(const std::vector<Automaton>& automta, const std::unique_ptr<LabelledTree<Tag, Reference>>& tree, int indent) {
    for (int i = 0; i < indent; i++) {
        std::cout << "  ";
    }

    if (tree->is_leaf()) {
        std::cout << "* Automaton (" << tree->label2().index << ") with Acceptance Index: " << automta[tree->label2().index].acceptance() << std::endl;
    } else {
        if (tree->label1() == CONJUNCTION) {
            std::cout << "* Conjunction" << std::endl;
        } else {
            std::cout << "* Disjunction" << std::endl;
        }

        for (auto const& child : tree->children()) {
            visit_tree(automta, child, indent + 1);
        }
    }
}


void simple_arbiter_example(const OwlThread& owl) {
    Formula formula = owl.createFormulaFactory().parse("G (!g_0) && !g_0 R !g_1 && G (! g_0 && ! g_1 && (! g_2 && true || (true && (! g_3))) || (! g_0 && true || (true && (! g_1)) && (! g_2 && ! g_3))) && G (r_0 -> F g_0) && G (r_1 -> F g_1) && G (r_2 -> F g_2) && G (r_3 -> F g_3)", std::vector<std::string>());

    EmersonLeiAutomaton tree1 = owl.createAutomaton(formula, false, false, NEVER, true);
    visit_tree(tree1.automata(), tree1.structure(), 0);

    EmersonLeiAutomaton tree2 = owl.createAutomaton(formula, false, false, AUTO, true);
    visit_tree(tree2.automata(), tree2.structure(), 0);

    EmersonLeiAutomaton tree3 = owl.createAutomaton(formula, false, false, ALWAYS, true);
    visit_tree(tree3.automata(), tree3.structure(), 0);
}

int main(int argc, char** argv) {
    // Execute `./gradlew smallDistTar -Pfull` to obtain the right jar!
    const char* classpath = "-Djava.class.path=../../../build/libs/owl.jar";

    // Set the second argument to true to obtain additional debugging output.
    OwlJavaVM owlJavaVM = OwlJavaVM(classpath, true, 4, true);
    OwlThread owl = owlJavaVM.attachCurrentThread();

    std::cout << "Parse Formula Example: " << std::endl << std::endl;
    Formula parsed_formula_1 = parse_ltl(owl);
    parsed_formula_1.print();

    std::cout << "Parse TLSF Example: " << std::endl << std::endl;
    Formula parsed_formula_2 = parse_tlsf(owl);
    parsed_formula_2.print();

    std::cout << std::endl << "Built Formula Example: " << std::endl << std::endl;
    Formula built_formula = create_formula(owl);
    built_formula.print();

    std::cout << std::endl << "Automaton Example 1: " << std::endl << std::endl;
    dpa_example(owl, parsed_formula_1);

    std::cout << std::endl << "Automaton Example 2: " << std::endl << std::endl;
    dpa_example(owl, built_formula);

    std::cout << std::endl << "Arbiter Example: " << std::endl << std::endl;
    simple_arbiter_example(owl);

    return 0;
}

