#include "owl-formula.h"
#include "owl-private.h"

namespace owl {
    void Formula::print() const {
        jclass clazz = get_class(env, handle);
        jmethodID toString = get_methodID(env, clazz, "toString", "()Ljava/lang/String;");
        auto string = call_method<jstring>(env, handle, toString);

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
        const char* of = "of";
        const char* unarySignature = "(Lowl/ltl/Formula;)Lowl/ltl/Formula;";
        const char* binarySignature = "(Lowl/ltl/Formula;Lowl/ltl/Formula;)Lowl/ltl/Formula;";

        bind_static(BooleanConstant, "owl/ltl/BooleanConstant", of, "(Z)Lowl/ltl/BooleanConstant;");
        bind_static(Literal, "owl/ltl/Literal", of, "(IZ)Lowl/ltl/Literal;");

        bind_static(Conjunction, "owl/ltl/Conjunction", of, binarySignature);
        bind_static(Disjunction, "owl/ltl/Disjunction", of, binarySignature);

        bind_static(FOperator, "owl/ltl/FOperator", of, unarySignature);
        bind_static(GOperator, "owl/ltl/GOperator", of, unarySignature);
        bind_static(XOperator, "owl/ltl/XOperator", of, unarySignature);

        bind_static(UOperator, "owl/ltl/UOperator", of, binarySignature);
        bind_static(ROperator, "owl/ltl/ROperator", of, binarySignature);
        bind_static(WOperator, "owl/ltl/WOperator", of, binarySignature);
        bind_static(MOperator, "owl/ltl/MOperator", of, binarySignature);

        bind_static_method(env, "owl/ltl/parser/LtlParser", "syntax", "(Ljava/lang/String;Ljava/util/List;)Lowl/ltl/Formula;", ltlParser, ltlParseID);
        bind_static_method(env, "owl/ltl/ltlf/LtlfParser", "syntaxToLtl", "(Ljava/lang/String;Ljava/util/List;)Lowl/ltl/Formula;", ltlfParser, ltlfParseID);
    }

    FormulaFactory::~FormulaFactory() {
        for (auto& clazz : classes) {
            deref(env, clazz);
        }

        deref(env, ltlParser);
    }

    template<typename... Args>
    Formula FormulaFactory::create(int index, Args... args) {
        return copy_from_java(env, call_static_method<jobject>(env, classes[index], methodIDs[index], args...));
    }

    Formula FormulaFactory::createFOperator(const Formula& formula) {
        return create<jobject>(FOperator, formula.handle);
    }

    Formula FormulaFactory::createGOperator(const Formula& formula) {
        return create<jobject>(GOperator, formula.handle);
    }

    Formula FormulaFactory::createXOperator(const Formula& formula) {
        return create<jobject>(XOperator, formula.handle);
    }

    Formula FormulaFactory::createUOperator(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(UOperator, left.handle, right.handle);
    }

    Formula FormulaFactory::createROperator(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(ROperator, left.handle, right.handle);
    }

    Formula FormulaFactory::createMOperator(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(MOperator, left.handle, right.handle);
    }

    Formula FormulaFactory::createWOperator(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(WOperator, left.handle, right.handle);
    }

    Formula FormulaFactory::createConjunction(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(Conjunction, left.handle, right.handle);
    }

    Formula FormulaFactory::createDisjunction(const Formula& left, const Formula& right) {
        return create<jobject, jobject>(Disjunction, left.handle, right.handle);
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
        return copy_from_java(env, call_method<jobject>(env, formula.handle, "not", "()Lowl/ltl/Formula;"));
    }

    Formula FormulaFactory::parse(const std::string &formula_string, const std::vector<std::string>& apMapping) {
        jstring string = copy_to_java(env, formula_string);
        jobject mapping = copy_to_java(env, apMapping);
        Formula formula = copy_from_java(env, call_static_method<jobject, jstring, jobject>(env, ltlParser, ltlParseID, string, mapping));
        deref(env, string, mapping);
        return formula;
    }

    Formula FormulaFactory::parseFinite(const std::string &formula_string, const std::vector<std::string>& apMapping) {
            jstring string = copy_to_java(env, formula_string);
            jobject mapping = copy_to_java(env, apMapping);
            Formula formula = copy_from_java(env, call_static_method<jobject, jstring, jobject>(env, ltlfParser, ltlfParseID, string, mapping));
            deref(env, string, mapping);
            return formula;
        }
}