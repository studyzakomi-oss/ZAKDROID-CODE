/* Minimal stand-in for Samba's lib/replace when building talloc for Android (bionic). */
#ifndef ZAKDROID_TALLOC_REPLACE_H
#define ZAKDROID_TALLOC_REPLACE_H
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <errno.h>
#include <limits.h>
#include <unistd.h>
#include <sys/types.h>
#define HAVE_VA_COPY 1
#define HAVE_INTPTR_T 1
#define HAVE_CONSTRUCTOR_ATTRIBUTE 1
#define HAVE_GETAUXVAL 1
#define HAVE_SYS_AUXV_H 1
#ifndef _PUBLIC_
#define _PUBLIC_ __attribute__((visibility("default")))
#endif
#ifndef _PRIVATE_
#define _PRIVATE_ __attribute__((visibility("hidden")))
#endif
#ifndef likely
#define likely(x) __builtin_expect(!!(x), 1)
#endif
#ifndef unlikely
#define unlikely(x) __builtin_expect(!!(x), 0)
#endif
#ifndef MIN
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#endif
#ifndef MAX
#define MAX(a, b) ((a) > (b) ? (a) : (b))
#endif
#ifndef discard_const
#define discard_const(ptr) ((void *)((uintptr_t)(ptr)))
#endif
#ifndef discard_const_p
#define discard_const_p(type, ptr) ((type *)discard_const(ptr))
#endif
#ifndef PRINTF_ATTRIBUTE
#define PRINTF_ATTRIBUTE(a1, a2) __attribute__((format(__printf__, a1, a2)))
#endif
#ifndef FALL_THROUGH
#define FALL_THROUGH __attribute__((fallthrough))
#endif
/* C23 memset_explicit is unavailable on older bionic/glibc; keep the zeroing observable. */
static inline void *zd_memset_explicit(void *dest, int c, size_t n)
{
	memset(dest, c, n);
	__asm__ __volatile__("" : : "r"(dest) : "memory");
	return dest;
}
#define memset_explicit zd_memset_explicit
#endif
