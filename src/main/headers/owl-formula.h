#include <jni.h>
#include <vector>
#include <map>

#ifndef OWL_FORMULA_H
#define OWL_FORMULA_H

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

    class Formula {
    private:
        JNIEnv* env;
        jobject formula;
        Formula(JNIEnv *env, jobject formula);

        friend class Automaton;
        friend class FormulaFactory;
        friend class FormulaRewriter;
        friend class OwlThread;

    public:
        Formula(const Formula &obj);
        Formula(const Formula &&obj) noexcept;
        ~Formula();

        void print() const;
    };

    class FormulaFactory {
    private:
        JNIEnv* env;
        jclass classes[MOperator + 1];
        jmethodID methodIDs[MOperator + 1];
        jclass parser;
        jclass tlsfParser;
        jmethodID parseID;
        jmethodID tlsfParseID;

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
        Formula parseTlsf(const std::string& tlsf, std::vector<std::string>& apMapping, int &numberOfInputVariables);

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

    class FormulaRewriter {
    private:
        JNIEnv* env;

        jclass realizability_rewriter;
        jmethodID splitID;

        jclass shift_rewriter;
        jmethodID shift_literalsID;

        jclass simplifier;
        jmethodID simplifyID;

        explicit FormulaRewriter(JNIEnv* env);
        friend class OwlThread;

    public:
        ~FormulaRewriter();

        std::vector<Formula> split(const Formula& input, int numberOfInputVariables, std::map<int, bool>& map);
        Formula shift_literals(const Formula& formula, std::map<int, int>& map);
        Formula simplify(const Formula& formula);
    };
}

#endif // OWL_FORMULA_H