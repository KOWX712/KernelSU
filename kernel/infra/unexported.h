#ifndef __KSU_UNEXPORTED_H
#define __KSU_UNEXPORTED_H

/*
 * Kernel symbols referenced by KernelSU that are NOT exported via
 * EXPORT_SYMBOL in modern kernels (>= 6.x / 7.x). They are resolved at
 * module init through find_kernel_symbol_exact() (kallsyms bootstrap),
 * see infra/unexported.c.
 *
 * Every pointer is named ksu_<symbol> and keeps the kernel prototype via
 * typeof(&<symbol>), which is unevaluated and emits no relocation.
 */

#include <linux/cred.h>
#include <linux/fs.h>
#include <linux/fs_struct.h>
#include <linux/kallsyms.h>
#include <linux/mm.h>
#include <linux/nsfs.h>
#include <linux/pid.h>
#include <linux/proc_ns.h>
#include <linux/sched.h>
#include <linux/sched/signal.h>
#include <linux/sched/user.h>
#include <linux/syscalls.h>
#include <linux/task_work.h>
#include <linux/tracepoint.h>
#include <linux/types.h>
#include <linux/uaccess.h>

/* Prototypes with no stable exported declaration; keep in sync with kernel. */
extern int path_mount(const char *dev_name, struct path *path, const char *type_page,
                      unsigned long flags, void *data_page);
extern int path_umount(struct path *path, int flags);
extern void ext4_unregister_sysfs(struct super_block *sb);
extern void seccomp_filter_release(struct task_struct *tsk);

/* Resolved function pointers (kernel functions, not exported). */
extern typeof(&find_task_by_vpid) ksu_find_task_by_vpid;
extern typeof(&copy_to_kernel_nofault) ksu_copy_to_kernel_nofault;
extern typeof(&ns_get_path) ksu_ns_get_path;
extern typeof(&set_fs_pwd) ksu_set_fs_pwd;
extern typeof(&task_work_add) ksu_task_work_add;
extern typeof(&strncpy_from_user_nofault) ksu_strncpy_from_user_nofault;
extern typeof(&dentry_open_nonotify) ksu_dentry_open_nonotify;
extern typeof(&get_mm_exe_file) ksu_get_mm_exe_file;
extern typeof(&alloc_uid) ksu_alloc_uid;
extern typeof(&set_cred_ucounts) ksu_set_cred_ucounts;
extern typeof(&seccomp_filter_release) ksu_seccomp_filter_release;
extern typeof(&kallsyms_lookup_size_offset) ksu_kallsyms_lookup_size_offset;
extern typeof(&change_pid) ksu_change_pid;
extern typeof(&free_pids) ksu_free_pids;
extern typeof(&path_mount) ksu_path_mount;
extern typeof(&path_umount) ksu_path_umount;
extern typeof(&ext4_unregister_sysfs) ksu_ext4_unregister_sysfs;

/* Resolved data symbols. */
extern rwlock_t *ksu_tasklist_lock;
extern struct mm_struct *ksu_init_mm;
extern const struct proc_ns_operations *ksu_mntns_operations;
extern struct tracepoint *ksu_tp_sys_enter;
extern struct tracepoint *ksu_tp_sched_process_exec;
extern struct tracepoint *ksu_tp_sched_process_exit;

/*
 * Drop-in replacements for __x64_sys_close/__x64_sys_setns/__x64_sys_unshare
 * (arch prefix via KSU_SYS_PREFIX): build a zeroed pt_regs like __ksyscall()
 * and call the resolved syscall entry point.
 */
long ksu_sys_close_impl(int fd);
long ksu_sys_setns_impl(int fd, int flags);
long ksu_sys_unshare_impl(unsigned long flags);

/* Resolve everything; returns 0 on success, negative errno otherwise. */
int ksu_init_unexported_symbols(void);

#endif
