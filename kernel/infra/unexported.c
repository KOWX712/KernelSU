#include <linux/errno.h>
#include <linux/init.h>
#include <linux/printk.h>

#include "infra/symbol_resolver.h"
#include "infra/unexported.h"
#include "util.h"

#define KSU_STR_(x) #x
#define KSU_STR(x) KSU_STR_(x)

/* Syscall entry points carry the arch prefix; declare but never call directly. */
extern long KSU_SYS_PREFIX(close)(const struct pt_regs *);
extern long KSU_SYS_PREFIX(setns)(const struct pt_regs *);

static typeof(&KSU_SYS_PREFIX(close)) ksu_sys_close_fn;
static typeof(&KSU_SYS_PREFIX(setns)) ksu_sys_setns_fn;

/* Resolved data symbols. */
rwlock_t *ksu_tasklist_lock;
struct mm_struct *ksu_init_mm;
const struct proc_ns_operations *ksu_mntns_operations;
struct tracepoint *ksu_tp_sys_enter;
struct tracepoint *ksu_tp_sched_process_exec;
struct tracepoint *ksu_tp_sched_process_exit;

/* Resolved function pointers. */
typeof(&find_task_by_vpid) ksu_find_task_by_vpid;
typeof(&copy_to_kernel_nofault) ksu_copy_to_kernel_nofault;
typeof(&ns_get_path) ksu_ns_get_path;
typeof(&set_fs_pwd) ksu_set_fs_pwd;
typeof(&ksys_unshare) ksu_ksys_unshare;
typeof(&task_work_add) ksu_task_work_add;
typeof(&strncpy_from_user_nofault) ksu_strncpy_from_user_nofault;
typeof(&dentry_open_nonotify) ksu_dentry_open_nonotify;
typeof(&get_mm_exe_file) ksu_get_mm_exe_file;
typeof(&alloc_uid) ksu_alloc_uid;
typeof(&set_cred_ucounts) ksu_set_cred_ucounts;
typeof(&seccomp_filter_release) ksu_seccomp_filter_release;
typeof(&kallsyms_lookup_size_offset) ksu_kallsyms_lookup_size_offset;
typeof(&change_pid) ksu_change_pid;
typeof(&free_pids) ksu_free_pids;
typeof(&path_mount) ksu_path_mount;
typeof(&path_umount) ksu_path_umount;
typeof(&ext4_unregister_sysfs) ksu_ext4_unregister_sysfs;

long ksu_sys_close_impl(int fd)
{
    struct pt_regs regs = { 0 };

    if (unlikely(!ksu_sys_close_fn))
        return -ENOSYS;
    PT_REGS_SYSCALL_PARM1(&regs) = (unsigned long)fd;
    return ksu_sys_close_fn(&regs);
}

long ksu_sys_setns_impl(int fd, int flags)
{
    struct pt_regs regs = { 0 };

    if (unlikely(!ksu_sys_setns_fn))
        return -ENOSYS;
    PT_REGS_SYSCALL_PARM1(&regs) = (unsigned long)fd;
    PT_REGS_PARM2(&regs) = (unsigned long)flags;
    return ksu_sys_setns_fn(&regs);
}

static int ksu_resolve(void **dst, const char *name)
{
    unsigned long addr = find_kernel_symbol_exact(name);

    if (!addr) {
        pr_err("failed to resolve kernel symbol: %s\n", name);
        return -ENOENT;
    }
    *dst = (void *)addr;
    return 0;
}

/* Pass the symbol name without the ksu_ prefix: KSU_RESOLVE(find_task_by_vpid) */
#define KSU_RESOLVE(name) ksu_resolve((void **)&ksu_##name, #name)

int __init ksu_init_unexported_symbols(void)
{
    int ret = 0;

    ret |= KSU_RESOLVE(find_task_by_vpid);
    ret |= KSU_RESOLVE(copy_to_kernel_nofault);
    ret |= KSU_RESOLVE(ns_get_path);
    ret |= KSU_RESOLVE(set_fs_pwd);
    ret |= KSU_RESOLVE(ksys_unshare);
    ret |= KSU_RESOLVE(task_work_add);
    ret |= KSU_RESOLVE(strncpy_from_user_nofault);
    ret |= KSU_RESOLVE(dentry_open_nonotify);
    ret |= KSU_RESOLVE(get_mm_exe_file);
    ret |= KSU_RESOLVE(alloc_uid);
    ret |= KSU_RESOLVE(set_cred_ucounts);
    ret |= KSU_RESOLVE(seccomp_filter_release);
    ret |= KSU_RESOLVE(kallsyms_lookup_size_offset);
    ret |= KSU_RESOLVE(change_pid);
    ret |= KSU_RESOLVE(free_pids);
    ret |= KSU_RESOLVE(path_mount);
    ret |= KSU_RESOLVE(path_umount);
    ret |= KSU_RESOLVE(ext4_unregister_sysfs);

    ret |= ksu_resolve((void **)&ksu_tasklist_lock, "tasklist_lock");
    ret |= ksu_resolve((void **)&ksu_init_mm, "init_mm");
    ret |= ksu_resolve((void **)&ksu_mntns_operations, "mntns_operations");

    ret |= ksu_resolve((void **)&ksu_sys_close_fn, KSU_STR(KSU_SYS_PREFIX(close)));
    ret |= ksu_resolve((void **)&ksu_sys_setns_fn, KSU_STR(KSU_SYS_PREFIX(setns)));

#ifdef CONFIG_HAVE_SYSCALL_TRACEPOINTS
    ret |= ksu_resolve((void **)&ksu_tp_sys_enter, "__tracepoint_sys_enter");
#endif
#ifdef CONFIG_KSU_NON_ANDROID
    ret |= ksu_resolve((void **)&ksu_tp_sched_process_exec, "__tracepoint_sched_process_exec");
    ret |= ksu_resolve((void **)&ksu_tp_sched_process_exit, "__tracepoint_sched_process_exit");
#endif

    if (ret)
        pr_err("failed to resolve %d groups of kernel symbols\n", ret);
    return ret ? -ENOENT : 0;
}
