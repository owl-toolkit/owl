#include <bitset>
#include <deque>
#include <iostream>
#include <set>

#include "owl.h"

using namespace owl;

Formula parse_formula(const Owl& owl) {
    FormulaFactory factory = owl.createFormulaFactory();
    FormulaRewriter rewriter = owl.createFormulaRewriter();

    // Create mapping from string to [0,n[ for the parser.
    std::vector<std::string> mapping = std::vector<std::string>({"a", "b", "c"});

    // Parse with provided mapping
    Formula parsedFormula = factory.parse("X a & (G F G c) | b | (G F a & F G ! a)", mapping);
    parsedFormula.print();

    // Use the standard simplifier on the formula
    Formula simplifiedFormula = rewriter.simplify(parsedFormula);
    simplifiedFormula.print();

    return simplifiedFormula;
}

Formula create_formula(const Owl& owl) {
    FormulaFactory factory = owl.createFormulaFactory();
    FormulaRewriter rewriter = owl.createFormulaRewriter();

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

    std::cout << "Presplit formula: ";
    imp2.print();

    std::cout << "Split formulae: " << std::endl;

    // Split formula using the realizibilty rewriter.
    std::map<int, bool> removed = std::map<int, bool>();

    int i = 1;

    for (Formula formula : rewriter.split(imp2, 2, removed)) {
        std::cout << i << ": "; i++;
        formula.print();

        std::cout << "Shifted formula: " << std::endl;
        // We now shift literals to close gaps.
        std::map<int, int> mapping = std::map<int, int>();
        rewriter.shift_literals(formula, mapping).print();

        std::cout << "Shifted literals:" << std::endl;

        for (const auto & entry : mapping) {
            std::cout << entry.first << " -> " << entry.second << std::endl;
        }
    }

    std::cout << "Removed literals with fixed valuation:" << std::endl;

    for (const auto &entry : removed) {
        std::cout << entry.first << " -> " << entry.second << std::endl;
    }

    return imp2;
}

void dpa_example(const Owl& owl, const Formula& formula) {
    Automaton dpa = owl.createAutomaton(formula);

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

        unsigned int letter = 0;

        for (const auto &i : dpa.edges(currentState)) {
            std::cout << " Letter: " << std::bitset<32>(letter) << " Successor: " << i.successor << " Colour: " << i.colour << std::endl;
            letter++;

            if (i.successor >= 0 && seenStates.find(i.successor) == seenStates.end()) {
                seenStates.insert(i.successor);
                stateQueue.push_back(i.successor);
            }
        }
    }
}

void visit_tree(const LabelledTree<Tag, Automaton>& tree, int indent) {
    for (int i = 0; i < indent; i++) {
        std::cout << "  ";
    }

    if (tree.type == LEAF) {
        std::cout << "* Automaton with Acceptance Index: " << tree.getLabel2().acceptance() << std::endl;
    } else {
        if (tree.getLabel1() == CONJUNCTION) {
            std::cout << "* Conjunction" << std::endl;
        } else {
            std::cout << "* Disjunction" << std::endl;
        }

        for (auto const& child : tree.getChildren()) {
            visit_tree(child, indent + 1);
        }
    }
}

void simple_arbiter_example(const Owl& owl) {
    Formula formula = owl.createFormulaFactory().parse("(((((((((G ((((((! (g_0)) && (! (g_1))) && (! "
                                                    "(g_2))) && (! (g_3))) && ((((! (g_4)) && (! (g_5))) && (((! (g_6)) && (true)) || ((true) "
                                                    "&& (! (g_7))))) || ((((! (g_4)) && (true)) || ((true) && (! (g_5)))) && ((! (g_6)) && (! "
                                                    "(g_7)))))) || (((((! (g_0)) && (! (g_1))) && (((! (g_2)) && (true)) || ((true) && (! (g_3)"
                                                    ")))) || ((((! (g_0)) && (true)) || ((true) && (! (g_1)))) && ((! (g_2)) && (! (g_3))))) &&"
                                                    " ((((! (g_4)) && (! (g_5))) && (! (g_6))) && (! (g_7)))))) && (G ((r_0) -> (F (g_0))))) &&"
                                                    " (G ((r_1) -> (F (g_1))))) && (G ((r_2) -> (F (g_2))))) && (G ((r_3) -> (F (g_3))))) && (G"
                                                    " ((r_4) -> (F (g_4))))) && (G ((r_5) -> (F (g_5))))) && (G ((r_6) -> (F (g_6))))) && (G ("
                                                    "(r_7) -> (F (g_7)))))", std::vector<std::string>());

    LabelledTree<Tag, Automaton> tree = owl.createAutomatonTree(formula);
    visit_tree(tree, 0);
}

int main(int argc, char** argv) {
    const char* classpath = "-Djava.class.path="
            "../../../build/lib/owl-1.2.0-SNAPSHOT.jar:"
            "../../../build/lib/jhoafparser-1.1.1-patched.jar:"
            "../../../build/lib/jbdd-0.2.0.jar:"
            "../../../build/lib/guava-23.4-jre.jar:"
            "../../../build/lib/naturals-util-0.7.0.jar:"
            "../../../build/lib/fastutil-8.1.0.jar:"
            "../../../build/lib/commons-cli-1.4.jar:"
            "../../../build/lib/antlr4-runtime-4.7.jar";

    // Set the second argument to true to obtain additional debugging output.
    Owl owl = Owl(classpath, true);

    std::cout << "Parse Formula Example: " << std::endl << std::endl;
    Formula parsed_formula = parse_formula(owl);

    std::cout << std::endl << "Built Formula Example: " << std::endl << std::endl;
    Formula built_formula = create_formula(owl);

    std::cout << std::endl << "Automaton Example 1: " << std::endl << std::endl;
    dpa_example(owl, parsed_formula);

    std::cout << std::endl << "Automaton Example 2: " << std::endl << std::endl;
    dpa_example(owl, built_formula);

    std::cout << std::endl << "Arbiter Example: " << std::endl << std::endl;
    simple_arbiter_example(owl);

    return 0;
}

