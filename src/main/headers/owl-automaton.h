#pragma once

#include <memory>
#include <utility>
#include <vector>
#include <limits>

#include <jni.h>

#include "owl-formula.h"

namespace owl {
    // LabelledTrees

    template<typename L1, typename L2>
    class LabelledTree {
    public:
        virtual ~LabelledTree() = default;

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

        Edge() : successor(std::numeric_limits<int>::min()), colour(std::numeric_limits<int>::min()) {};
        Edge(int _successor, int _colour) : successor(_successor), colour(_colour) {};
    };

    struct EdgeTree {
        const std::vector<int32_t> tree;

        EdgeTree(const std::vector<int32_t> _tree) : tree(_tree) {};

        Edge edge(const std::vector<bool> &bitmap) const {
            int32_t i = 1;

            while (i > 0) {
                int32_t variable = tree[i];

                if (bitmap[variable]) {
                    i = tree[i + 2];
                } else {
                    i = tree[i + 1];
                }
            }

            i = tree[0] - i;
            return Edge(tree[i], tree[i + 1]);
        }
    };

    struct Reference {
        int index;
        Formula formula;
        std::map<int, int> alphabet_mapping;

        Reference(Formula _formula, int _index, std::map<int, int> _alphabet_mapping) : index(_index), formula(std::move(_formula)), alphabet_mapping(std::move(_alphabet_mapping)) {};
    };

    enum Tag {
        BICONDITIONAL, CONJUNCTION, DISJUNCTION
    };

    enum Acceptance {
        BUCHI, CO_BUCHI, CO_SAFETY, PARITY, PARITY_MAX_EVEN, PARITY_MAX_ODD, PARITY_MIN_EVEN, PARITY_MIN_ODD, SAFETY, WEAK
    };

    class Automaton : public owl::ManagedJObject {
    private:
        jmethodID acceptanceID;
        jmethodID acceptanceSetCountID;
        jmethodID edgesID;
        jmethodID qualityScoreID;

        Automaton(JNIEnv *env, jobject handle);
        friend class copy_from_java;
        friend class OwlThread;

    public:
        Automaton(const Automaton &automaton) = default;
        Automaton(Automaton &&automaton) noexcept;

        Acceptance acceptance() const;
        int acceptance_set_count() const;
        EdgeTree edges(int state) const;
        double quality_score(int successor, int colour) const;
    };

    enum RealizabilityStatus {
        REALIZABLE, UNREALIZABLE, UNKNOWN
    };

    enum VariableStatus {
        CONSTANT_TRUE, CONSTANT_FALSE, USED, UNUSED
    };

    class DecomposedDPA : public owl::ManagedJObject {
    private:
        DecomposedDPA(JNIEnv *env, jobject handle) : ManagedJObject(env, "owl/cinterface/DecomposedDPA", handle) {}
        friend class copy_from_java;

    public:
        DecomposedDPA(const DecomposedDPA &automaton) = default;
        DecomposedDPA(DecomposedDPA &&automaton) noexcept : ManagedJObject(std::move(automaton)) {};

        std::vector<Automaton> automata();
        std::unique_ptr<LabelledTree<Tag, Reference>> structure();
        std::vector<VariableStatus> variable_statuses() const;

        bool declare(RealizabilityStatus status, const std::vector<jint> &states);
        RealizabilityStatus query(const std::vector<jint> &states);
    };


}
