#include <linux/uaccess.h>
#include <linux/kprobes.h>
#include <linux/slab.h>
#include <linux/lsm_audit.h>
#include <linux/security.h>
#include <linux/atomic.h>
#include <linux/version.h>

#include <asm/insn.h>

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 14, 0)
#include <asm/patching.h>
#endif

#include "policy/feature.h"
#include "include/klog.h"
#include "runtime/ksud_boot.h"
#include "infra/seccomp_cache.h"

/* changelog
 *
 * 20260430 - intercept ksu sid
 * 20260703 - move to bl patch (testing)
 *
 */

static u32 ksu_sid = 0;
static u32 priv_app_sid = 0;

// init as disabled by default
static bool ksu_avc_spoof_status __read_mostly = false;

void ksu_avc_spoof_enable();
void ksu_avc_spoof_disable();

static bool ksu_avc_spoof_enabled = true;
static bool boot_completed = false;

static int avc_spoof_feature_get(u64 *value)
{
	*value = ksu_avc_spoof_enabled ? 1 : 0;
	return 0;
}

static int avc_spoof_feature_set(u64 value)
{
	bool enable = value != 0;

	if (enable == ksu_avc_spoof_enabled) {
		pr_info("avc_spoof: no need to change\n");
		return 0;
	}

	ksu_avc_spoof_enabled = enable;

	if (boot_completed) {
		if (enable) {
			ksu_avc_spoof_enable();
		} else {
			ksu_avc_spoof_disable();
		}
	}

	pr_info("avc_spoof: set to %d\n", enable);

	return 0;
}

static const struct ksu_feature_handler avc_spoof_handler = {
	.feature_id = KSU_FEATURE_AVC_SPOOF,
	.name = "avc_spoof",
	.get_handler = avc_spoof_feature_get,
	.set_handler = avc_spoof_feature_set,
};

static int get_sid()
{
	int err = security_secctx_to_secid("u:r:ksu:s0", strlen("u:r:ksu:s0"), &ksu_sid);
	if (err) {
		pr_info("avc_spoof/get_sid: ksu_sid not found!\n");
		return -1;
	}
	pr_info("avc_spoof/get_sid: ksu_sid: %u\n", ksu_sid);

	err = security_secctx_to_secid("u:r:priv_app:s0:c512,c768", strlen("u:r:priv_app:s0:c512,c768"), &priv_app_sid);
	if (err) {
		pr_info("avc_spoof/get_sid: priv_app_sid not found!\n");
		return -1;
	}
	pr_info("avc_spoof/get_sid: priv_app_sid: %u\n", priv_app_sid);
	return 0;
}

static __always_inline int ksu_slow_avc_audit_swap_tsid(u32 *tsid)
{
	if (!ksu_avc_spoof_status)
		return 0;

	if (*tsid != ksu_sid)
		return 0;

	pr_info("avc_spoof/slow_avc_audit: replacing tsid: %u with priv_app_sid: %u\n", *tsid, priv_app_sid);
	*tsid = priv_app_sid;

	return 0;
}

/**
 * arm64_bl_patch(): hunt and patch first bl insn found with target
 *
 * @target_callsite: callsite on where to start scanning for (ptr / fn_ptr)
 * @target_width: how far to scan for (bytes / ptrdiff)
 * @symbol_addr: symbol being called to look for at the site (fn_ptr)
 * @hook_addr: symbol to redirect "bl symbol_addr" to (fn_ptr)
 *
 * CONTEXT:
 * - do NOT call inside atomic context! (stop_machine)
 * - safe to run early or within kernel threads.
 *
 * NOTES:
 * - both symbol_addr and hook_addr must be inside +/-128MB ptrdiff to callsite!
 *
 * 'Can' bypass kCFI and PAC limitations by patching static branch-with-link insn directly.
 * returns 0 on successful branch patching, 1 if callsite isn't found.
 */
static int arm64_bl_patch(uintptr_t target_callsite, ptrdiff_t target_width, uintptr_t symbol_addr, uintptr_t hook_addr)
{
	if (!target_callsite || !symbol_addr) {
		pr_info("%s: no callsite or symbol addr specified!\n", __func__);
		return 1;
	}

	might_sleep();

	uintptr_t start_addr = (uintptr_t)target_callsite;
	uintptr_t end_addr = start_addr + target_width;
	uintptr_t curr_addr = start_addr;
	uint32_t raw_instruction; // arm64 wordsize
	const ptrdiff_t bl_max_delta = (1L << 25) * sizeof(uint32_t); // 26 bits signed * insn size

start_scan:
	if (curr_addr >= end_addr)
		goto bail;

	if (copy_from_kernel_nofault(&raw_instruction, (void *)curr_addr, sizeof(uint32_t)))
		goto step_up;

	// aarch64_insn_is_##abbr
	if (!aarch64_insn_is_bl(raw_instruction))
		goto step_up;

	// signed
	long offset = aarch64_get_branch_offset(raw_instruction);
	uintptr_t calculated_destination = curr_addr + offset;

	if (calculated_destination != symbol_addr)
		goto step_up;

	pr_info("%s: found call site at 0x%lx\n", __func__, curr_addr);

	ptrdiff_t delta = 0;
	if (hook_addr > curr_addr)
		delta = hook_addr - curr_addr;
	else
		delta = curr_addr - hook_addr;
		
	if (delta >= bl_max_delta) {
		pr_info("%s: callsite 0x%lx to hook 0x%lx out of range! (delta: %ld bytes)\n",  __func__, curr_addr, hook_addr, delta);
		return 1;
	} else
		pr_info("%s: callsite 0x%lx to hook 0x%lx inside range! (delta: %ld bytes)\n",  __func__, curr_addr, hook_addr, delta);

	u32 insn = aarch64_insn_gen_branch_imm(curr_addr, hook_addr, AARCH64_INSN_BRANCH_LINK);
	void *arr_addr[] = { (void*)curr_addr };
	uint32_t arr_insn[] = { insn };

	int res = aarch64_insn_patch_text(arr_addr, arr_insn, 1);
	pr_info("%s: patched callsite: 0x%lx ret: %d\n", __func__, curr_addr, res);

	return 0;

step_up:
	curr_addr = curr_addr + sizeof(uint32_t);
	goto start_scan;

bail:
	pr_info("%s: callsite scan done!\n", __func__);
	return 1;
}

static uintptr_t kp_kallsyms_lookup_name(const char *name)
{
	struct kprobe kp = { .symbol_name = name };
	uintptr_t addr = NULL;

	if (!!register_kprobe(&kp))
		return NULL;

	addr = (uintptr_t)kp.addr;
	unregister_kprobe(&kp);

	return addr;
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 4, 0)
extern noinline int slow_avc_audit(u32 ssid, u32 tsid, u16 tclass,
			    u32 requested, u32 audited, u32 denied, int result,
			    struct common_audit_data *a);

__attribute__((used))
static int ksu_hook_slow_avc_audit(u32 ssid, u32 tsid, u16 tclass,
				   u32 requested, u32 audited, u32 denied, int result,
				   struct common_audit_data *a)
{
	ksu_slow_avc_audit_swap_tsid(&tsid);
	return slow_avc_audit(ssid, tsid, tclass, requested, audited, denied, result, a);
}
#else
#include <security.h> // security/selinux/include/security.h
extern noinline int slow_avc_audit(struct selinux_state *state,
			    u32 ssid, u32 tsid, u16 tclass,
			    u32 requested, u32 audited, u32 denied, int result,
			    struct common_audit_data *a);

__attribute__((used))
static int ksu_hook_slow_avc_audit(struct selinux_state *state,
				   u32 ssid, u32 tsid, u16 tclass,
				   u32 requested, u32 audited, u32 denied, int result,
				   struct common_audit_data *a)
{
	ksu_slow_avc_audit_swap_tsid(&tsid);
	return slow_avc_audit(state, ssid, tsid, tclass, requested, audited, denied, result, a);
}
#endif

void ksu_avc_spoof_disable(void)
{
	ksu_avc_spoof_status = false;
	pr_info("avc_spoof/exit: slow_avc_audit spoofing disabled!\n");
}

void ksu_avc_spoof_enable(void) 
{
	int ret = get_sid();
	if (ret) {
		pr_info("avc_spoof/init: sid grab fail!\n");
		return;
	}
	
	ret = arm64_bl_patch(kp_kallsyms_lookup_name("avc_has_extended_perms"), 384 * sizeof(uint32_t), kp_kallsyms_lookup_name("slow_avc_audit"), (uintptr_t)&ksu_hook_slow_avc_audit);
	pr_info("avc_spoof: hook on slow_avc_audit on avc_has_extended_perms ret: %d\n", ret);

	ret = arm64_bl_patch(kp_kallsyms_lookup_name("audit_inode_permission"), 64 * sizeof(uint32_t), kp_kallsyms_lookup_name("slow_avc_audit"), (uintptr_t)&ksu_hook_slow_avc_audit);
	pr_info("avc_spoof: hook on slow_avc_audit on audit_inode_permission ret: %d\n", ret);

	ksu_avc_spoof_status = true;
	
	pr_info("avc_spoof/init: slow_avc_audit spoofing enabled!\n");
}

void ksu_avc_spoof_late_init()
{
	boot_completed = true;
	
	if (ksu_avc_spoof_enabled)
		ksu_avc_spoof_enable();
}

void __init ksu_avc_spoof_init()
{
	if (ksu_register_feature_handler(&avc_spoof_handler))
		pr_err("Failed to register avc spoof feature handler\n");
}

void __exit ksu_avc_spoof_exit()
{
	if (ksu_avc_spoof_enabled)
		ksu_avc_spoof_disable();

	ksu_unregister_feature_handler(KSU_FEATURE_AVC_SPOOF);
}
