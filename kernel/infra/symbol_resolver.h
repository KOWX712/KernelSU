#ifndef __KSU_SYMBOL_RESOLVER_H
#define __KSU_SYMBOL_RESOLVER_H

void *ksu_resolve_symbol_for_functable_hook(const char *symbol_name);
unsigned long find_kernel_symbol_exact(const char *symbol_name);
int ksu_init_symbol_resolver(void);

#endif
