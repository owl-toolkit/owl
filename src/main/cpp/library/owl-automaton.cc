#include "owl-automaton.h"
#include "owl-private.h"

namespace owl {
    DPA::DPA(JNIEnv *env, const Formula& formula) {
        this->env = env;

        jclass clazz = lookup_class(env, "owl/jni/Ltl2Dpa");
        jmethodID constructor = get_methodID(env, clazz, "<init>", "(Lowl/ltl/Formula;)V");

        handle = new_object<jobject, jobject>(env, clazz, constructor, formula.formula);
        successorsID = get_methodID(env, clazz, "successors", "(I)[I");
        parityID = get_methodID(env, clazz, "parity", "()I");

        deref(env, clazz);
    }

    DPA::DPA(const DPA &dpa) : env(dpa.env), handle(ref(dpa.env, dpa.handle)), successorsID(dpa.successorsID), parityID(dpa.parityID) {}

    DPA::~DPA() {
        deref(env, handle);
    }

    Parity DPA::parity() {
        return Parity(call_int_method(env, handle, parityID));
    }

    std::vector<Edge> DPA::successors(int state) {
        auto result = call_method<jintArray>(env, handle, successorsID, state);

        // Provide an array to copy into...
        jsize length = env->GetArrayLength(result);
        std::vector<Edge> successors = std::vector<Edge>((size_t) length / 2, Edge(0,0));
        env->GetIntArrayRegion(result, 0, length, reinterpret_cast<jint *>(&successors[0]));

        deref(env, result);
        return successors;
    }
}