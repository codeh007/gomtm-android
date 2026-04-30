#include <dlfcn.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>

typedef int (*PyBytesMainFn)(int, char **);

static char *dup_jstring(JNIEnv *env, jstring value) {
    if (value == NULL) {
        return NULL;
    }
    const char *utf = (*env)->GetStringUTFChars(env, value, NULL);
    if (utf == NULL) {
        return NULL;
    }
    char *copy = strdup(utf);
    (*env)->ReleaseStringUTFChars(env, value, utf);
    return copy;
}

JNIEXPORT jint JNICALL
Java_com_gomtm_swarm_platform_python_PythonRuntimeNative_runMain(
    JNIEnv* env,
    jobject thiz,
    jstring python_home,
    jstring source_apk_path,
    jstring native_library_dir,
    jobjectArray args
) {
    (void)thiz;
    (void)source_apk_path;
    (void)native_library_dir;

    char *python_home_utf8 = dup_jstring(env, python_home);
    if (python_home_utf8 == NULL) {
        return 2;
    }

    size_t libpython_path_len = strlen(python_home_utf8) + strlen("/lib/libpython3.14.so") + 1;
    char *libpython_path = (char *)malloc(libpython_path_len);
    if (libpython_path == NULL) {
        free(python_home_utf8);
        return 3;
    }
    snprintf(libpython_path, libpython_path_len, "%s/lib/libpython3.14.so", python_home_utf8);

    setenv("PYTHONHOME", python_home_utf8, 1);

    void *handle = dlopen(libpython_path, RTLD_NOW | RTLD_GLOBAL);
    free(libpython_path);
    if (handle == NULL) {
        free(python_home_utf8);
        return 4;
    }

    PyBytesMainFn py_bytes_main = (PyBytesMainFn)dlsym(handle, "Py_BytesMain");
    if (py_bytes_main == NULL) {
        dlclose(handle);
        free(python_home_utf8);
        return 5;
    }

    jsize argc = (*env)->GetArrayLength(env, args);
    char **argv = (char **)calloc((size_t)argc + 2, sizeof(char *));
    if (argv == NULL) {
        dlclose(handle);
        free(python_home_utf8);
        return 6;
    }

    argv[0] = strdup("python");
    for (jsize i = 0; i < argc; i++) {
        jstring arg = (jstring)(*env)->GetObjectArrayElement(env, args, i);
        argv[i + 1] = dup_jstring(env, arg);
    }
    argv[argc + 1] = NULL;

    int exit_code = py_bytes_main((int)argc + 1, argv);

    for (jsize i = 0; i < argc + 1; i++) {
        free(argv[i]);
    }
    free(argv);
    dlclose(handle);
    free(python_home_utf8);
    return exit_code;
}
