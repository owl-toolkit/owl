#include "owl-automaton.h"
#include "owl-private.h"

namespace owl {
    Automaton::Automaton(JNIEnv *env, const Formula& formula) {
        this->env = env;

        jclass clazz = lookup_class(env, "owl/jni/IntAutomaton");
        jmethodID constructor = get_static_methodID(env, clazz, "of", "(Lowl/ltl/Formula;)Lowl/jni/IntAutomaton;");

        this->handle = call_static_method<jobject, jobject>(env, clazz, constructor, formula.formula);
        bind_methods(clazz);
        deref(env, clazz);
    }

    Automaton::Automaton(JNIEnv *env, jobject handle) {
        this->env = env;
        this->handle = ref(env, handle);

        jclass clazz = lookup_class(env, "owl/jni/IntAutomaton");
        bind_methods(clazz);
        deref(env, clazz);
    }

    void Automaton::bind_methods(const jclass &clazz) {
        alphabet_mappingID = get_methodID(env, clazz, "alphabetMapping", "()[I");
        acceptanceID = get_methodID(env, clazz, "acceptance", "()I");
        edgesID = get_methodID(env, clazz, "edges", "(I)[I");
        successorsID = get_methodID(env, clazz, "successors", "(I)[I");
    }

    Automaton::Automaton(const Automaton &automaton) :
            env(automaton.env),
            handle(ref(automaton.env, automaton.handle)),
            alphabet_mappingID(automaton.alphabet_mappingID),
            acceptanceID(automaton.acceptanceID),
            edgesID(automaton.edgesID),
            successorsID(automaton.successorsID) {}

    Automaton::~Automaton() {
        if (env != nullptr) {
            // TODO: fix this
            // deref(env, handle);
        }
    }

    Acceptance Automaton::acceptance() const {
        return Acceptance(call_int_method<>(env, handle, acceptanceID));
    }

    std::vector<Edge> Automaton::edges(int state) {
        auto result = call_method<jintArray>(env, handle, edgesID, state);

        // Provide an array to copy into...
        jsize length = env->GetArrayLength(result);
        std::vector<Edge> edges = std::vector<Edge>((size_t) length / 2, Edge(-1,-1));
        env->GetIntArrayRegion(result, 0, length, reinterpret_cast<jint *>(&edges[0]));

        deref(env, result);
        return edges;
    }

    std::vector<int> Automaton::successors(int state){
        auto result = call_method<jintArray>(env, handle, successorsID, state);

        // Provide an array to copy into...
        jsize length = env->GetArrayLength(result);
        std::vector<int> successors = std::vector<int>((size_t) length, -1);
        env->GetIntArrayRegion(result, 0, length, &successors[0]);

        deref(env, result);
        return successors;
    }

    std::map<int, int> Automaton::alphabet_mapping() {
        std::map<int, int> mapping = std::map<int, int>();
        auto java_mapping = call_method<jintArray>(env, handle, alphabet_mappingID);

        jsize length = env->GetArrayLength(java_mapping);

        for (int i = 0; i < length; ++i) {
            int j;
            env->GetIntArrayRegion(java_mapping, i, 1, &j);

            if (j != -1) {
                mapping[i] = j;
            }
        }

        return mapping;
    }
}