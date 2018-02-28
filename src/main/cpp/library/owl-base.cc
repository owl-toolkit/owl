#include "owl-base.h"
#include "owl-private.h"

namespace owl {
    ManagedJObject::ManagedJObject(JNIEnv *env, jobject handle) :
            env(env),
            clazz(nullptr),
            handle(ref(env, handle)) {}

    ManagedJObject::ManagedJObject(JNIEnv *env, const char *class_name, jobject handle) :
            env(env),
            clazz(lookup_class(env, class_name)),
            handle(ref(env, handle)) {
        if (!env->IsInstanceOf(handle, clazz) || check_exception(env, "Failed to check instance of")) {
            throw std::runtime_error("Expected class " + std::string(class_name));
        }
    }

    ManagedJObject::ManagedJObject(const ManagedJObject &object) :
            env(object.env),
            clazz(object.clazz != nullptr ? ref(env, object.clazz) : nullptr),
            handle(ref(env, object.handle)) {}

    ManagedJObject::ManagedJObject(ManagedJObject &&object) noexcept :
            env(object.env),
            clazz(object.clazz),
            handle(object.handle) {
        object.env = nullptr;
        object.clazz = nullptr;
        object.handle = nullptr;
    }

    ManagedJObject::~ManagedJObject() {
        if (clazz != nullptr) {
            deref(env, clazz);
        }

        if (handle != nullptr) {
            deref(env, handle);
        }
    }
}