package me.weishu.kernelsu.ui.component.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.profile.Capabilities
import me.weishu.kernelsu.profile.Groups
import me.weishu.kernelsu.ui.component.material.ExpressiveColumn
import me.weishu.kernelsu.ui.component.material.ExpressiveListItem
import me.weishu.kernelsu.ui.component.profile.dialogs.MultiSelectDialog
import me.weishu.kernelsu.ui.component.profile.dialogs.MultilineInputDialog
import me.weishu.kernelsu.ui.component.profile.dialogs.NumberInputDialog
import me.weishu.kernelsu.ui.component.profile.dialogs.SingleSelectDialog
import me.weishu.kernelsu.ui.component.profile.dialogs.TextInputDialog

@Composable
fun RootProfileConfigMaterial(
    modifier: Modifier = Modifier,
    fixedName: Boolean,
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit
) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UidGidPanel(
            uid = profile.uid,
            gid = profile.gid,
            onUidChange = { onProfileChange(profile.copy(uid = it, rootUseDefault = false)) },
            onGidChange = { onProfileChange(profile.copy(gid = it, rootUseDefault = false)) }
        )

        GroupsPanel(
            selected = profile.groups.mapNotNull { gid ->
                Groups.entries.find { it.gid == gid }
            },
            onSelectionChange = { selection ->
                onProfileChange(
                    profile.copy(
                        groups = selection.map { it.gid }.ifEmpty { listOf(0) },
                        rootUseDefault = false
                    )
                )
            }
        )

        CapsPanel(
            selected = profile.capabilities,
            onSelectionChange = { selection ->
                onProfileChange(
                    profile.copy(
                        capabilities = selection.map { it.cap },
                        rootUseDefault = false
                    )
                )
            }
        )

        MountNameSpacePanel(
            namespace = profile.namespace,
            onNamespaceChange = { onProfileChange(profile.copy(namespace = it, rootUseDefault = false)) }
        )

        SELinuxPanel(
            context = profile.context,
            rules = profile.rules,
            onContextChange = { domain ->
                onProfileChange(profile.copy(context = domain, rootUseDefault = false))
            },
            onRulesChange = { rules ->
                onProfileChange(profile.copy(rules = rules, rootUseDefault = false))
            }
        )
    }
}

@Composable
private fun UidGidPanel(
    uid: Int,
    gid: Int,
    onUidChange: (Int) -> Unit,
    onGidChange: (Int) -> Unit
) {
    val showUidDialog = remember { mutableStateOf(false) }
    val showGidDialog = remember { mutableStateOf(false) }

    if (showUidDialog.value) {
        NumberInputDialog(
            title = "UID",
            value = uid,
            onValueChange = onUidChange,
            onDismiss = { showUidDialog.value = false }
        )
    }

    if (showGidDialog.value) {
        NumberInputDialog(
            title = "GID",
            value = gid,
            onValueChange = onGidChange,
            onDismiss = { showGidDialog.value = false }
        )
    }

    ExpressiveColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        content = listOf(
            {
                ExpressiveListItem(
                    headlineContent = { Text("UID") },
                    supportingContent = { Text(uid.toString()) },
                    onClick = { showUidDialog.value = true }
                )
            },
            {
                ExpressiveListItem(
                    headlineContent = { Text("GID") },
                    supportingContent = { Text(gid.toString()) },
                    onClick = { showGidDialog.value = true }
                )
            }
        )
    )
}

@Composable
private fun GroupsPanel(
    selected: List<Groups>,
    onSelectionChange: (Set<Groups>) -> Unit
) {
    val showDialog = remember { mutableStateOf(false) }

    val groups = remember {
        Groups.entries.sortedWith(
            compareBy<Groups> { if (selected.contains(it)) 0 else 1 }
                .then(compareBy {
                    when (it) {
                        Groups.ROOT -> 0
                        Groups.SYSTEM -> 1
                        Groups.SHELL -> 2
                        else -> Int.MAX_VALUE
                    }
                })
                .then(compareBy { it.name })
        )
    }

    if (showDialog.value) {
        MultiSelectDialog(
            title = "Groups",
            subtitle = "${selected.size} / 32",
            items = groups,
            selectedItems = selected.toSet(),
            itemTitle = { it.display },
            itemSubtitle = { it.desc },
            maxSelection = 32,
            onConfirm = {
                onSelectionChange(it)
                showDialog.value = false
            },
            onDismiss = { showDialog.value = false }
        )
    }

    val tag = if (selected.isEmpty()) {
        "None"
    } else {
        selected.joinToString(", ") { it.display }
    }

    ExpressiveColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        content = listOf {
            ExpressiveListItem(
                headlineContent = { Text(stringResource(R.string.profile_groups)) },
                supportingContent = { Text(tag) },
                onClick = { showDialog.value = true }
            )
        }
    )
}

@Composable
private fun MountNameSpacePanel(
    namespace: Int,
    onNamespaceChange: (Int) -> Unit
) {
    data class NamespaceOption(
        val value: Int,
        val label: String
    )

    val showDialog = remember { mutableStateOf(false) }

    val inheritedLabel = stringResource(R.string.profile_namespace_inherited)
    val globalLabel = stringResource(R.string.profile_namespace_global)
    val individualLabel = stringResource(R.string.profile_namespace_individual)

    val options = remember(inheritedLabel, globalLabel, individualLabel) {
        listOf(
            NamespaceOption(0, inheritedLabel),
            NamespaceOption(1, globalLabel),
            NamespaceOption(2, individualLabel)
        )
    }

    val selectedOption = options.find { it.value == namespace } ?: options[0]

    if (showDialog.value) {
        SingleSelectDialog(
            title = stringResource(R.string.profile_namespace),
            items = options,
            selectedItem = selectedOption,
            itemTitle = { it.label },
            onConfirm = {
                onNamespaceChange(it.value)
                showDialog.value = false
            },
            onDismiss = { showDialog.value = false }
        )
    }

    ExpressiveColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        content = listOf {
            ExpressiveListItem(
                headlineContent = { Text(stringResource(R.string.profile_namespace)) },
                supportingContent = { Text(selectedOption.label) },
                onClick = { showDialog.value = true }
            )
        }
    )
}

@Composable
private fun CapsPanel(
    selected: List<Int>,
    onSelectionChange: (Set<Capabilities>) -> Unit
) {
    val showDialog = remember { mutableStateOf(false) }

    val selectedCaps = remember(selected) {
        selected.mapNotNull { cap ->
            Capabilities.entries.find { it.cap == cap }
        }
    }

    val capabilities = remember(selectedCaps) {
        Capabilities.entries.sortedWith(
            compareBy<Capabilities> { if (selectedCaps.contains(it)) 0 else 1 }
                .then(compareBy { it.display })
        )
    }

    if (showDialog.value) {
        MultiSelectDialog(
            title = "Capabilities",
            subtitle = "${selectedCaps.size} / ${Capabilities.entries.size}",
            items = capabilities,
            selectedItems = selectedCaps.toSet(),
            itemTitle = { it.display },
            itemSubtitle = { null },
            maxSelection = Int.MAX_VALUE,
            onConfirm = {
                onSelectionChange(it)
                showDialog.value = false
            },
            onDismiss = { showDialog.value = false }
        )
    }

    val tag = if (selectedCaps.isEmpty()) {
        "None"
    } else {
        selectedCaps.joinToString(", ") { it.display }
    }

    ExpressiveColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        content = listOf {
            ExpressiveListItem(
                headlineContent = { Text(stringResource(R.string.profile_capabilities)) },
                supportingContent = { Text(tag) },
                onClick = { showDialog.value = true }
            )
        }
    )
}

@Composable
private fun SELinuxPanel(
    context: String,
    rules: String,
    onContextChange: (String) -> Unit,
    onRulesChange: (String) -> Unit
) {
    val showContextDialog = remember { mutableStateOf(false) }
    val showRulesDialog = remember { mutableStateOf(false) }

    if (showContextDialog.value) {
        TextInputDialog(
            title = stringResource(R.string.profile_selinux_context),
            value = context,
            onValueChange = onContextChange,
            onDismiss = { showContextDialog.value = false }
        )
    }

    if (showRulesDialog.value) {
        MultilineInputDialog(
            title = stringResource(R.string.profile_selinux_rules),
            value = rules,
            minLines = 3,
            maxLines = 10,
            onValueChange = onRulesChange,
            onDismiss = { showRulesDialog.value = false }
        )
    }

    val rulesPreview = remember(rules) {
        val lines = rules.lines()
        if (lines.isEmpty() || rules.isBlank()) {
            "—"
        } else {
            val preview = lines.take(4).joinToString("\n")
            if (lines.size > 4) {
                "$preview\n…"
            } else {
                preview
            }
        }
    }

    ExpressiveColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        content = listOf(
            {
                ExpressiveListItem(
                    headlineContent = { Text(stringResource(R.string.profile_selinux_context)) },
                    supportingContent = { Text(context.ifEmpty { "—" }) },
                    onClick = { showContextDialog.value = true }
                )
            },
            {
                ExpressiveListItem(
                    headlineContent = { Text(stringResource(R.string.profile_selinux_rules)) },
                    supportingContent = { Text(rulesPreview) },
                    onClick = { showRulesDialog.value = true }
                )
            }
        )
    )
}
