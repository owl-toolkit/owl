#include <jni.h>
#include <vector>
#include <string>
#include <iostream>

#include "owl.h"

#ifndef OWL_PRIVATE_H
#define OWL_PRIVATE_H

inline void check_exception(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        throw std::runtime_error(message);
    }
}

template<typename T1>
constexpr bool is_any_of() {
    return false;
}

template<typename T1, typename T2, typename... T3>
constexpr bool is_any_of() {
    return std::is_same<T1, T2>::value || is_any_of<T1, T3...>();
}

template<typename T>
constexpr bool is_jobject() {
    return is_any_of<T, jobject, jclass, jstring, jintArray, jobjectArray>();
}

template<typename T>
inline T ref(JNIEnv* env, T ref) {
    if (env == nullptr || ref == nullptr) {
        throw std::runtime_error("ref called with null arguments");
    }

    static_assert(is_jobject<T>());
    T globalRef = reinterpret_cast<T>(env->NewGlobalRef(ref));
    check_exception(env, "Failed to acquire global reference.");
    return globalRef;
}

inline void deref(JNIEnv* env, jobject globalRef) {
    if (env == nullptr || globalRef == nullptr) {
        throw std::runtime_error("deref called with null arguments");
    }

    env->DeleteGlobalRef(globalRef);
    check_exception(env, "Failed to release global reference.");
}

template<typename T>
inline T make_global(JNIEnv* env, T local_ref) {
    T globalRef = ref(env, local_ref);
    env->DeleteLocalRef(local_ref);
    check_exception(env, "Failed to release local reference.");
    return globalRef;
}

template<typename... Args>
inline void deref(JNIEnv* env, jobject globalRef, Args... args) {
    deref(env, globalRef);
    deref(env, args...);
}

inline jclass lookup_class(JNIEnv* env, const char* name) {
    jclass clazz = env->FindClass(name);
    check_exception(env, "Failed to acquire class of object.");
    return make_global(env, clazz);
}

inline jclass get_class(JNIEnv* env, jobject object) {
    jclass clazz = env->GetObjectClass(object);
    check_exception(env, "Failed to acquire class of object.");
    return make_global(env, clazz);
}

inline jmethodID get_methodID(JNIEnv *env, jclass clazz, const char *name, const char *signature) {
    jmethodID methodID = env->GetMethodID(clazz, name, signature);
    check_exception(env, "Failed to acquire methodID.");
    return methodID;
}

inline jmethodID get_methodID(JNIEnv *env, jobject object, jclass& clazz, const char *name, const char *signature) {
    clazz = get_class(env, object);
    return get_methodID(env, clazz, name, signature);
}

inline jfieldID get_fieldID(JNIEnv *env, jclass clazz, const char *name, const char *signature) {
    jfieldID methodID = env->GetFieldID(clazz, name, signature);
    check_exception(env, "Failed to acquire fieldID.");
    return methodID;
}

inline jfieldID get_fieldID(JNIEnv *env, jobject object, jclass& clazz, const char *name, const char *signature) {
    clazz = get_class(env, object);
    return get_fieldID(env, clazz, name, signature);
}

inline jmethodID get_static_methodID(JNIEnv *env, jclass clazz, const char *name, const char *signature) {
    jmethodID methodID = env->GetStaticMethodID(clazz, name, signature);
    check_exception(env, "Failed to acquire static methodID");
    return methodID;
}

template<typename T, typename... Args>
inline T call_method(JNIEnv *env, jobject object, jmethodID methodID, Args... args) {
    static_assert(is_jobject<T>());
    auto result = reinterpret_cast<T>(env->CallObjectMethod(object, methodID, args...));
    check_exception(env, "Failed to call object method.");
    return make_global(env, result);
}

template<typename T, typename... Args>
inline T call_static_method(JNIEnv *env, jclass clazz, jmethodID methodID, Args... args) {
    static_assert(is_jobject<T>());
    auto result = reinterpret_cast<T>(env->CallStaticObjectMethod(clazz, methodID, args...));
    check_exception(env, "Failed to call object method.");
    return make_global(env, result);
}

template<typename... Args>
inline jboolean call_bool_method(JNIEnv *env, jobject object, jmethodID methodID, Args... args) {
    jboolean result = env->CallBooleanMethod(object, methodID, args...);
    check_exception(env, "Failed to call int method.");
    return result;
}

template<typename... Args>
inline jint call_int_method(JNIEnv *env, jobject object, jmethodID methodID, Args... args) {
    jint result = env->CallIntMethod(object, methodID, args...);
    check_exception(env, "Failed to call int method.");
    return result;
}

template<typename T, typename... Args>
inline T new_object(JNIEnv *env, jclass clazz, jmethodID constructor, Args... args) {
    static_assert(is_jobject<T>());
    auto object = reinterpret_cast<T>(env->NewObject(clazz, constructor, args...));
    check_exception(env, "Failed to construct object.");
    return make_global(env, object);
}

template<typename T, typename... Args>
inline T new_object(JNIEnv *env, const char *className, const char *signature, Args... args) {
    static_assert(is_jobject<T>());
    jclass clazz = lookup_class(env, className);
    jmethodID constructor = get_methodID(env, clazz, "<init>", signature);
    auto object = new_object<T>(env, clazz, constructor, args...);
    deref(env, clazz);
    return object;
}

inline void bind_method(JNIEnv *env, const char *className, const char *methodName, const char *signature,
                        jclass &classOut, jmethodID &jmethodIDOut) {
    classOut = lookup_class(env, className);
    jmethodIDOut = get_methodID(env, classOut, methodName, signature);
}

inline void bind_static_method(JNIEnv *env, const char *className, const char *methodName, const char *signature,
                               jclass &classOut, jmethodID &jmethodIDOut) {
    classOut = lookup_class(env, className);
    jmethodIDOut = get_static_methodID(env, classOut, methodName, signature);
}

template<typename T>
inline T get_object_field(JNIEnv *env, jobject object, const char *fieldName, const char *signature) {
    jclass clazz = nullptr;
    jfieldID fieldID = get_fieldID(env, object, clazz, fieldName, signature);
    auto field = reinterpret_cast<T>(env->GetObjectField(object, fieldID));
    check_exception(env, "Failed to access field.");
    deref(env, clazz);
    return make_global(env, field);
}

inline jstring copy_to_java(JNIEnv *env, const std::string& string) {
    jstring java_string = env->NewStringUTF(string.c_str());
    check_exception(env, "Failed to copy string to Java.");
    return make_global(env, java_string);
}

template<typename T>
inline jobject copy_to_java(JNIEnv *env, const std::vector<T>& vector) {
    jobject list = new_object<jobject>(env, "java/util/ArrayList", "(I)V", vector.size());
    jclass listClass;
    jmethodID addID;
    bind_method(env, "java/util/List", "add", "(Ljava/lang/Object;)Z", listClass, addID);

    for (T element : vector) {
        jobject javaElement = copy_to_java(env, element);
        call_bool_method(env, list, addID, javaElement);
        deref(env, javaElement);
    }

    deref(env, listClass);
    return list;
}

inline bool is_instance_of(JNIEnv *env, jobject object, const char *className) {
    jclass clazz = lookup_class(env, className);
    bool is_instance_of = env->IsInstanceOf(object, clazz);
    check_exception(env, "Failed to check instance of");
    deref(env, clazz);
    return is_instance_of;
}

namespace owl {
    class copy_from_java
    {
    private:
        JNIEnv *env {nullptr};
        jobject value {nullptr};

    public:
        copy_from_java(JNIEnv *_env, jobject _value) : env(_env), value(_value) {};
        copy_from_java(const copy_from_java& o) : env(o.env), value(ref(o.env, o.value)) {};

        ~copy_from_java() {
            deref(env, value);
        }

        operator bool() const {
            jclass clazz = lookup_class(env, "java/lang/Boolean");
            jmethodID methodID = get_methodID(env, clazz, "booleanValue", "()Z");
            bool result = call_bool_method(env, value, methodID);
            deref(env, clazz);
            return result;
        }

        operator int() const {
            jclass clazz = lookup_class(env, "java/lang/Integer");
            jmethodID methodID = get_methodID(env, clazz, "intValue", "()I");
            int result = call_int_method(env, value, methodID);
            deref(env, clazz);
            return result;
        }

        operator owl::Tag() const {
            jclass clazz = lookup_class(env, "java/lang/Enum");
            jmethodID methodID = get_methodID(env, clazz, "ordinal", "()I");
            int result = call_int_method(env, value, methodID);
            deref(env, clazz);
            return owl::Tag(result);
        }

        operator owl::Automaton() const {
            return owl::Automaton(env, value);
        }

        template<typename T1, typename T2>
        operator owl::LabelledTree<T1, T2>() const {
            bool leaf = is_instance_of(env, value, "owl/collections/LabelledTree$Leaf");
            jclass clazz;

            if (leaf) {
                jmethodID get_labelId = get_methodID(env, value, clazz, "getLabel", "()Ljava/lang/Object;");
                T2 label2 = copy_from_java(env, call_method<jobject>(env, value, get_labelId));
                deref(env, clazz);
                return owl::LabelledTree<T1, T2>(label2);
            } else {
                jmethodID get_labelId = get_methodID(env, value, clazz, "getLabel", "()Ljava/lang/Object;");
                jmethodID get_childrenId = get_methodID(env, value, clazz, "getChildren", "()Ljava/util/List;");
                T1 label1 = copy_from_java(env, call_method<jobject>(env, value, get_labelId));
                std::vector<owl::LabelledTree<T1, T2>> children = copy_from_java(env, call_method<jobject>(env, value, get_childrenId));
                deref(env, clazz);
                return owl::LabelledTree<T1, T2>(label1, std::move(children));
            }
        };

        template<typename T>
        operator std::vector<T>() const {
            std::vector<T> vector = std::vector<T>();
            jclass list_class = lookup_class(env, "java/util/List");
            jclass iterator_class = lookup_class(env, "java/util/Iterator");

            auto iterator = call_method<jobject>(env, value, get_methodID(env, list_class, "iterator", "()Ljava/util/Iterator;"));

            jmethodID next = get_methodID(env, iterator_class, "next", "()Ljava/lang/Object;");
            jmethodID has_next = get_methodID(env, iterator_class, "hasNext", "()Z");

            while (call_bool_method(env, iterator, has_next)) {
                vector.push_back(copy_from_java(env, call_method<jobject>(env, iterator, next)));
            }

            deref(env, list_class, iterator_class, iterator);
            return vector;
        }


        template<typename T1, typename T2>
        operator std::map<T1, T2>() const {
            std::map<T1, T2> map = std::map<T1, T2>();
            jclass map_class = lookup_class(env, "java/util/Map");
            jclass set_class = lookup_class(env, "java/util/Set");
            jclass iterator_class = lookup_class(env, "java/util/Iterator");
            jclass entry_class = lookup_class(env, "java/util/Map$Entry");

            auto entry_set = call_method<jobject>(env, value, get_methodID(env, map_class, "entrySet", "()Ljava/util/Set;"));
            auto iterator = call_method<jobject>(env, entry_set, get_methodID(env, set_class, "iterator", "()Ljava/util/Iterator;"));

            jmethodID next = get_methodID(env, iterator_class, "next", "()Ljava/lang/Object;");
            jmethodID has_next = get_methodID(env, iterator_class, "hasNext", "()Z");

            jmethodID get_key = get_methodID(env, entry_class, "getKey", "()Ljava/lang/Object;");
            jmethodID get_value = get_methodID(env, entry_class, "getValue", "()Ljava/lang/Object;");

            while (call_bool_method(env, iterator, has_next)) {
                auto entry = call_method<jobject>(env, iterator, next);
                T1 key = copy_from_java(env, call_method<jobject>(env, entry, get_key));
                T2 value = copy_from_java(env, call_method<jobject>(env, entry, get_value));
                map[key] = value;
                deref(env, entry);
            }

            deref(env, map_class, set_class, iterator_class, entry_class, entry_set, iterator);
            return map;
        }
    };
}

#endif // OWL_PRIVATE_H