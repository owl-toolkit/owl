#include <memory>
#include <utility>
#include <vector>

#include <jni.h>

#include "owl-formula.h"

#ifndef OWL_AUTOMATON_H
#define OWL_AUTOMATON_H

namespace owl {
    // LabelledTrees

    template<typename L1, typename L2>
    class LabelledTree {
    public:
        virtual const bool is_leaf() const = 0;

        bool const is_node() const {
            return !is_leaf();
        };

        virtual const std::vector<std::unique_ptr<LabelledTree<L1, L2>>> &children() const = 0;

        virtual const L1 &label1() const = 0;

        virtual const L2 &label2() const = 0;
    };

    template<typename L1, typename L2>
    class Node : public LabelledTree<L1, L2> {
    private:
        friend class copy_from_java;

        L1 label;
        std::vector<std::unique_ptr<LabelledTree<L1, L2>>> _children;

        Node(L1 _label, std::vector<std::unique_ptr<LabelledTree<L1, L2>>> _children) :
                label(_label),
                _children(std::move(_children)) {};

    public:
        const bool is_leaf() const override {
            return false;
        }

        const std::vector<std::unique_ptr<LabelledTree<L1, L2>>> &children() const override {
            return _children;
        }

        const L1 &label1() const override {
            return label;
        }

        const L2 &label2() const override {
            throw std::runtime_error("label2() called on node.");
        }
    };

    template<typename L1, typename L2>
    class Leaf : public LabelledTree<L1, L2> {
    private:
        friend class copy_from_java;

        L2 label;

        explicit Leaf(L2 _label) : label(_label) {};

    public:
        const bool is_leaf() const override {
            return true;
        }

        const std::vector<std::unique_ptr<LabelledTree<L1, L2>>> &children() const override {
            throw std::runtime_error("children() called on leaf.");
        }

        const L1 &label1() const override {
            throw std::runtime_error("label1() called on leaf.");
        }

        const L2 &label2() const override {
            return label;
        }
    };

    // Automata

    struct Edge {
        int successor;
        int colour;

        Edge() : successor(-1), colour(-1) {};
        Edge(int _successor, int _colour) : successor(_successor), colour(_colour) {};
    };

    struct Reference {
        int index;
        Formula formula;
        std::map<int, int> alphabet_mapping;

        Reference(Formula _formula, int _index, std::map<int, int> _alphabet_mapping) : formula(std::move(_formula)), index(_index), alphabet_mapping(std::move(_alphabet_mapping)) {};
    };

    enum Tag {
        CONJUNCTION, DISJUNCTION
    };

    enum SafetySplitting {
        NEVER, AUTO, ALWAYS
    };

    enum Acceptance {
        BUCHI, CO_BUCHI, CO_SAFETY, PARITY_MAX_EVEN, PARITY_MAX_ODD, PARITY_MIN_EVEN, PARITY_MIN_ODD, SAFETY
    };

    class Automaton : public owl::ManagedJObject {
    private:
        jmethodID acceptanceID;
        jmethodID acceptanceSetCountID;
        jmethodID edgesID;
        jmethodID successorsID;

        Automaton(JNIEnv *env, jobject handle);
        friend class copy_from_java;
        friend class OwlThread;

    public:
        Automaton(const Automaton &automaton) = default;
        Automaton(Automaton &&automaton) noexcept;

        Acceptance acceptance() const;
        int acceptance_set_count() const;
        std::vector<Edge> edges(int state) const;
        std::vector<int> successors(int state) const;
    };

    class EmersonLeiAutomaton : public owl::ManagedJObject {
    private:
        EmersonLeiAutomaton(JNIEnv *env, jobject handle) : ManagedJObject(env, "owl/jni/JniEmersonLeiAutomaton", handle) {}
        friend class copy_from_java;

    public:
        EmersonLeiAutomaton(const EmersonLeiAutomaton &automaton) = default;
        EmersonLeiAutomaton(EmersonLeiAutomaton &&automaton) noexcept : ManagedJObject(std::move(automaton)) {};

        std::vector<Automaton> automata();
        std::unique_ptr<LabelledTree<Tag, Reference>> structure();
    };
}

#endif // OWL_AUTOMATON_H