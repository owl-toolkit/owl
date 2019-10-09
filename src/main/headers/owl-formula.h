#pragma once

#include <jni.h>
#include <vector>
#include <map>

#include "owl-base.h"

namespace owl {
    enum FormulaType {
        BooleanConstant,
        Literal,
        Conjunction,
        Disjunction,

        FOperator,
        GOperator,
        XOperator,

        UOperator,
        ROperator,
        WOperator,
        MOperator,
    };

    class Formula : public ManagedJObject {
    private:
        Formula(JNIEnv *env, jobject handle) : ManagedJObject(env, handle) {}

        friend class Automaton;
        friend class FormulaFactory;
        friend class FormulaRewriter;
        friend class OwlThread;
        friend class copy_from_java;

    public:
        Formula(const Formula &formula) = default;
        Formula(Formula &&formula) noexcept : ManagedJObject(std::move(formula)) {};

        void print() const;
    };

    class FormulaFactory {
    private:
        JNIEnv* env;
        jclass classes[MOperator + 1];
        jmethodID methodIDs[MOperator + 1];
        jclass ltlParser;
        jmethodID ltlParseID;
        jclass ltlfParser;
        jmethodID ltlfParseID;

        void bind_static(int index, const char *className, const char *methodName, const char *signature);

        template<typename... Args>
        Formula create(int index, Args... args);

        explicit FormulaFactory(JNIEnv *env);
        friend class OwlThread;

    public:
        ~FormulaFactory();

        Formula createFOperator(const Formula& formula);
        Formula createGOperator(const Formula& formula);
        Formula createXOperator(const Formula& formula);
        Formula createUOperator(const Formula& left, const Formula& right);
        Formula createROperator(const Formula& left, const Formula& right);
        Formula createMOperator(const Formula& left, const Formula& right);
        Formula createWOperator(const Formula& left, const Formula& right);
        Formula createConjunction(const Formula& left, const Formula& right);
        Formula createDisjunction(const Formula& left, const Formula& right);
        Formula createConstant(bool value);
        Formula createLiteral(int atom);
        Formula createNegatedLiteral(int atom);
        Formula createImplication(const Formula& left, const Formula& right);
        Formula createNegation(const Formula& formula);
        Formula createBiimplication(const Formula &left, const Formula &right);
        Formula parse(const std::string& formula, const std::vector<std::string>& apMapping);
        Formula parseFinite(const std::string& formula_string, const std::vector<std::string>& apMapping);

        template<typename... Args>
        Formula createConjunction(const Formula& left, const Formula& right, Args... args) {
            Formula conjunction = createConjunction(left, right);
            return createConjunction(conjunction, args...);
        }

        template<typename... Args>
        Formula createDisjunction(const Formula& left, const Formula& right, Args... args) {
            Formula disjunction = createDisjunction(left, right);
            return createDisjunction(disjunction, args...);
        }
    };
}