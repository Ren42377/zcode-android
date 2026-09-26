/*
 * PTY implementation for the built-in terminal.
 *
 * Modeled on the AOSP Terminal app forkpty pattern (Apache-2.0): openpty gives a
 * master and slave pair, the child becomes a session leader, takes the slave as
 * its controlling terminal, dups it onto stdin/stdout/stderr and execs the shell.
 * Reading the master returns EIO once the child exits; the Kotlin layer treats
 * that as the normal end of a session.
 */

#include <jni.h>

#include <errno.h>
#include <fcntl.h>
#include <pty.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static void throw_io_exception(JNIEnv *env, const char *message) {
    jclass exception_class = (*env)->FindClass(env, "java/io/IOException");
    if (exception_class != NULL) {
        (*env)->ThrowNew(env, exception_class, message);
    }
}

static char *copy_jstring(JNIEnv *env, jstring value) {
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

static void free_string_array(char **array) {
    if (array == NULL) {
        return;
    }
    for (size_t i = 0; array[i] != NULL; i++) {
        free(array[i]);
    }
    free(array);
}

/* Copies a String[] into a NULL-terminated array of C strings. */
static char **copy_jstring_array(JNIEnv *env, jobjectArray array) {
    jsize length = (*env)->GetArrayLength(env, array);
    char **out = calloc((size_t) length + 1, sizeof(char *));
    if (out == NULL) {
        return NULL;
    }
    for (jsize i = 0; i < length; i++) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, array, i);
        out[i] = copy_jstring(env, item);
        if (item != NULL) {
            (*env)->DeleteLocalRef(env, item);
        }
        if (out[i] == NULL) {
            free_string_array(out);
            return NULL;
        }
    }
    return out;
}

JNIEXPORT jintArray JNICALL
Java_com_zcode_android_core_terminal_PtyChannel_forkExec(
        JNIEnv *env, jclass clazz, jobjectArray cmd, jstring cwd, jobjectArray env_array,
        jint rows, jint columns) {
    (void) clazz;
    char **argv = copy_jstring_array(env, cmd);
    char **envp = copy_jstring_array(env, env_array);
    char *working_directory = copy_jstring(env, cwd);
    if (argv == NULL || envp == NULL || working_directory == NULL) {
        free_string_array(argv);
        free_string_array(envp);
        free(working_directory);
        throw_io_exception(env, "failed to prepare the process arguments");
        return NULL;
    }

    int master = -1;
    int slave = -1;
    struct winsize window_size;
    memset(&window_size, 0, sizeof(window_size));
    window_size.ws_row = (unsigned short) rows;
    window_size.ws_col = (unsigned short) columns;

    if (openpty(&master, &slave, NULL, NULL, &window_size) != 0) {
        int error = errno;
        free_string_array(argv);
        free_string_array(envp);
        free(working_directory);
        throw_io_exception(env, strerror(error));
        return NULL;
    }

    pid_t pid = fork();
    if (pid < 0) {
        int error = errno;
        close(master);
        close(slave);
        free_string_array(argv);
        free_string_array(envp);
        free(working_directory);
        throw_io_exception(env, strerror(error));
        return NULL;
    }
    if (pid == 0) {
        close(master);
        setsid();
        ioctl(slave, TIOCSCTTY, NULL);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) {
            close(slave);
        }
        chdir(working_directory);
        execve(argv[0], argv, envp);
        _exit(127);
    }

    close(slave);
    free_string_array(argv);
    free_string_array(envp);
    free(working_directory);

    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        kill(pid, SIGKILL);
        close(master);
        return NULL;
    }
    jint values[2] = {(jint) pid, (jint) master};
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_zcode_android_core_terminal_PtyChannel_read(
        JNIEnv *env, jclass clazz, jint fd, jbyteArray buffer) {
    (void) clazz;
    jsize length = (*env)->GetArrayLength(env, buffer);
    if (length <= 0) {
        return 0;
    }
    jbyte *elements = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (elements == NULL) {
        return -ENOMEM;
    }
    ssize_t count = read(fd, elements, (size_t) length);
    (*env)->ReleaseByteArrayElements(env, buffer, elements, 0);
    if (count < 0) {
        return (jint) -errno;
    }
    return (jint) count;
}

JNIEXPORT jint JNICALL
Java_com_zcode_android_core_terminal_PtyChannel_write(
        JNIEnv *env, jclass clazz, jint fd, jbyteArray bytes) {
    (void) clazz;
    jsize length = (*env)->GetArrayLength(env, bytes);
    if (length <= 0) {
        return 0;
    }
    jbyte *elements = (*env)->GetByteArrayElements(env, bytes, NULL);
    if (elements == NULL) {
        return -ENOMEM;
    }
    jint result = length;
    jsize remaining = length;
    while (remaining > 0) {
        ssize_t written = write(fd, elements + (length - remaining), (size_t) remaining);
        if (written < 0) {
            if (errno == EINTR) {
                continue;
            }
            result = (jint) -errno;
            break;
        }
        remaining -= (jsize) written;
    }
    (*env)->ReleaseByteArrayElements(env, bytes, elements, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_zcode_android_core_terminal_PtyChannel_resize(
        JNIEnv *env, jclass clazz, jint fd, jint rows, jint columns) {
    (void) env;
    (void) clazz;
    struct winsize window_size;
    memset(&window_size, 0, sizeof(window_size));
    window_size.ws_row = (unsigned short) rows;
    window_size.ws_col = (unsigned short) columns;
    if (ioctl(fd, TIOCSWINSZ, &window_size) != 0) {
        return (jint) -errno;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_zcode_android_core_terminal_PtyChannel_closeFd(
        JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    if (close(fd) != 0 && errno != EINTR) {
        return (jint) -errno;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_zcode_android_core_terminal_PtyChannel_killProcess(
        JNIEnv *env, jclass clazz, jint pid, jint signal) {
    (void) env;
    (void) clazz;
    if (kill((pid_t) pid, (int) signal) != 0) {
        return (jint) -errno;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_zcode_android_core_terminal_PtyChannel_waitFor(
        JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    int status = 0;
    if (waitpid((pid_t) pid, &status, 0) < 0) {
        return (jint) -errno;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return -1;
}
