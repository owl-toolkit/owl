#include <jni.h>
#include <vector>

#include "owl-automaton.h"
#include "owl-formula.h"

#ifndef OWL_H
#define OWL_H

namespace owl {
    enum Tag {
        CONJUNCTION, DISJUNCTION
    };

    enum Type {
        NODE, LEAF
    };

    enum SafetySplitting {
        NEVER, AUTO, ALWAYS
    };

    template<typename L1, typename L2>
    class LabelledTree {
    public:
        Type type;

    private:
        L1 label1;
        L2 label2;
        std::vector<LabelledTree<L1, L2>> children;

    public:
        LabelledTree(const LabelledTree<L1, L2> &tree);
        LabelledTree(L1 label1, std::vector<LabelledTree<L1, L2>> children);
        LabelledTree(L2 label2);

        const std::vector<LabelledTree<L1, L2>>& getChildren() const;
        const L1& getLabel1() const;
        const L2& getLabel2() const;
    };

    template<typename L1, typename L2>
    const std::vector<LabelledTree<L1, L2>>& LabelledTree<L1, L2>::getChildren() const {
        return children;
    }

    template<typename L1, typename L2>
    const L1& LabelledTree<L1, L2>::getLabel1() const {
        if (type != NODE) {
            throw std::runtime_error("Illegal Label 1 access");
        }

        return label1;
    }

    template<typename L1, typename L2>
    const L2& LabelledTree<L1, L2>::getLabel2() const {
        if (type != LEAF) {
            throw std::runtime_error("Illegal Label 2 access");
        }

        return label2;
    }

    template<typename L1, typename L2>
    LabelledTree<L1, L2>::LabelledTree(L2 label2) : type(LEAF), label1(), label2(label2), children() {}

    template<typename L1, typename L2>
    LabelledTree<L1, L2>::LabelledTree(L1 label1, std::vector<LabelledTree<L1, L2>> children) : type(NODE), label1(label1), label2(), children(children) {}


    template<typename L1, typename L2>
    LabelledTree<L1, L2>::LabelledTree(const LabelledTree<L1, L2> &tree) {
        type = tree.type;
        label1 = tree.label1;
        label2 = tree.label2;
        children = tree.children;
    }

    class Owl {
    private:
        const bool debug;
        JNIEnv* env;
        JavaVM* vm;

    public:
        Owl(const char* classpath, bool debug);
        Owl(const Owl &owl) = delete;
        Owl(const Owl &&owl) noexcept : debug(owl.debug), env(owl.env), vm(owl.vm) {};
        ~Owl();

        FormulaFactory createFormulaFactory() const;
        FormulaRewriter createFormulaRewriter() const;
        Automaton createAutomaton(const Formula &formula, bool on_the_fly) const;
        LabelledTree<Tag, Automaton> createAutomatonTree(const Formula &formula, bool on_the_fly, SafetySplitting safety_splitting) const;
    };
}

#endif // OWL_H