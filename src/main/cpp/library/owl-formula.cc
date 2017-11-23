#include "owl-formula.h"
#include "owl-private.h"

namespace owl {
    Formula::Formula(JNIEnv *env, jobject formula) : env(env), formula(formula) {}

    Formula::Formula(const Formula &formula) : env(formula.env), formula(ref(formula.env, formula.formula)) {}

    Formula::Formula(const Formula &&formula) noexcept : env(formula.env), formula(ref(formula.env, formula.formula)) {}

    Formula::~Formula() {
       deref(env, formula);
    }

    const void Formula::print() {
        jclass clazz = get_class(env, formula);
        jmethodID toString = get_methodID(env, clazz, "toString", "()Ljava/lang/String;");
        auto string = call_method<jstring>(env, formula, toString);

        // Get a C-style string
        const char* str = env->GetStringUTFChars(string, JNI_FALSE);
        std::cout << str << std::endl;
        env->ReleaseStringUTFChars(string, str);

        deref(env, clazz, string);
    }

    void FormulaFactory::bind_static(int index, const char *className, const char *methodName, const char *signature) {
        bind_static_method(env, className, methodName, signature, classes[index], methodIDs[index]);
    }

    FormulaFactory::FormulaFactory(JNIEnv* env) : env(env) {
        const char* create = "create";
        const char* unarySignature = "(Lowl/ltl/Formula;)Lowl/ltl/Formula;";
        const char* binarySignature = "(Lowl/ltl/Formula;Lowl/ltl/Formula;)Lowl/ltl/Formula;";

        bind_static(BooleanConstant, "owl/ltl/BooleanConstant", "get", "(Z)Lowl/ltl/BooleanConstant;");
        bind_static(Literal, "owl/ltl/Literal", "create", "(IZ)Lowl/ltl/Literal;");

        bind_static(Conjunction, "owl/ltl/Conjunction", create, binarySignature);
        bind_static(Disjunction, "owl/ltl/Disjunction", create, binarySignature);

        bind_static(FOperator, "owl/ltl/FOperator", create, unarySignature);
        bind_static(GOperator, "owl/ltl/GOperator", create, unarySignature);
        bind_static(XOperator, "owl/ltl/XOperator", create, unarySignature);

        bind_static(UOperator, "owl/ltl/UOperator", create, binarySignature);
        bind_static(ROperator, "owl/ltl/ROperator", create, binarySignature);
        bind_static(WOperator, "owl/ltl/WOperator", create, binarySignature);
        bind_static(MOperator, "owl/ltl/MOperator", create, binarySignature);

        bind_static_method(env, "owl/ltl/parser/LtlParser", "syntax", "(Ljava/lang/String;Ljava/util/List;)Lowl/ltl/Formula;", parser, parseID);
    }

    FormulaFactory::~FormulaFactory() {
        for (auto& clazz : classes) {
            deref(env, clazz);
        }

        deref(env, parser);
    }

    template<typename... Args>
    Formula FormulaFactory::create(int index, Args... args) {
        return Formula(env, call_static_method<jobject>(env, classes[index], methodIDs[index], args...));
    }

    Formula FormulaFactory::createFOperator(const Formula& formula) {
        return create<jobject>(FOperator, formula.formula);
    }

    Formula FormulaFactory::createGOperator(const Formula& formula) {
        return create<jobject>(GOperator, formula.formula);
    }

    Formula FormulaFactory::createXOperator(const Formula& formula) {
        return create<jobject>(XOperator, formula.formula);
    }

    Formula FormulaFactory::createUOperator(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(UOperator, left.formula, right.formula);
    }

    Formula FormulaFactory::createROperator(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(ROperator, left.formula, right.formula);
    }

    Formula FormulaFactory::createMOperator(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(MOperator, left.formula, right.formula);
    }

    Formula FormulaFactory::createWOperator(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(WOperator, left.formula, right.formula);
    }

    Formula FormulaFactory::createConjunction(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(Conjunction, left.formula, right.formula);
    }

    Formula FormulaFactory::createDisjunction(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(Disjunction, left.formula, right.formula);
    }

    Formula FormulaFactory::createConstant(const bool value) {
        return create<bool>(BooleanConstant, value);
    }

    Formula FormulaFactory::createLiteral(const int atom) {
        return create<int, bool>(Literal, atom, false);
    }

    Formula FormulaFactory::createNegatedLiteral(const int atom) {
        return create<int, bool>(Literal, atom, true);
    }

    Formula FormulaFactory::createImplication(const Formula &left, const Formula &right) {
        Formula notLeft = createNegation(left);
        return createDisjunction(notLeft, right);
    }

    Formula FormulaFactory::createBiimplication(const Formula &left, const Formula &right) {
        return createConjunction(createImplication(left, right), createImplication(right, left));
    }

    Formula FormulaFactory::createNegation(const Formula &formula) {
        jclass clazz;
        jmethodID notID = get_methodID(env, formula.formula, clazz, "not", "()Lowl/ltl/Formula;");
        Formula negation = Formula(env, call_method<jobject>(env, formula.formula, notID));
        deref(env, clazz);
        return negation;
    }

    Formula FormulaFactory::parse(const std::string &formula_string, const std::vector<std::string>& apMapping) {
        jstring string = copy_to_java(env, formula_string);
        jobject mapping = copy_to_java(env, apMapping);
        auto formula = call_static_method<jobject, jstring, jobject>(env, parser, parseID, string, mapping);
        deref(env, string, mapping);
        return Formula(env, formula);
    }

    FormulaRewriter::FormulaRewriter(JNIEnv *env) : env(env) {
        bind_static_method(env, "owl/ltl/rewriter/ShiftRewriter", "shiftLiterals",
                           "(Lowl/ltl/Formula;)Lowl/ltl/rewriter/ShiftRewriter$ShiftedFormula;", shift_rewriter,
                           shift_literalsID);
        bind_static_method(env, "owl/ltl/rewriter/RealizabilityRewriter", "split",
                           "(Lowl/ltl/Formula;ILjava/util/Map;)[Lowl/ltl/Formula;", realizability_rewriter, splitID);
        bind_static_method(env, "owl/ltl/rewriter/RewriterFactory", "apply", "(Lowl/ltl/Formula;)Lowl/ltl/Formula;", simplifier, simplifyID);
    }

    std::vector<Formula> FormulaRewriter::split(const Formula &input, int lastInputAtom, std::map<int, bool>& map) {
        auto java_map = new_object<jobject>(env, "java/util/HashMap", "()V");
        auto array = call_static_method<jobjectArray>(env, realizability_rewriter, splitID, input.formula, lastInputAtom, java_map);
        map = copy_from_java(env, java_map);

        jsize length = env->GetArrayLength(array);
        std::vector<Formula> formulas = std::vector<Formula>();

        for (int i = 0; i < length; ++i) {
            jobject output = make_global(env, env->GetObjectArrayElement(array, i));
            formulas.emplace_back(Formula(env, output));
        }

        deref(env, array, java_map);
        return formulas;
    }

    Formula FormulaRewriter::shift_literals(const Formula &formula, std::map<int, int> &map) {
        map.clear();

        auto shifted_formula = call_static_method<jobject>(env, shift_rewriter, shift_literalsID, formula.formula);
        auto result = get_object_field<jobject>(env, shifted_formula, "formula", "Lowl/ltl/Formula;");
        auto mapping = get_object_field<jintArray>(env, shifted_formula, "mapping", "[I");

        jsize length = env->GetArrayLength(mapping);

        for (int i = 0; i < length; ++i) {
            int j;
            env->GetIntArrayRegion(mapping, i, 1, &j);

            if (j != -1) {
                map[i] = j;
            }
        }

        std::vector<std::string> list = std::vector<std::string>({"foo", "bar"});

        copy_to_java(env, list);

        deref(env, shifted_formula, mapping);
        return Formula(env, result);
    }

    Formula FormulaRewriter::simplify(const Formula &formula) {
        return Formula(env, call_static_method<jobject, jobject>(env, simplifier, simplifyID, formula.formula));
    }

    FormulaRewriter::~FormulaRewriter() {
        deref(env, shift_rewriter, realizability_rewriter, simplifier);
    }
}