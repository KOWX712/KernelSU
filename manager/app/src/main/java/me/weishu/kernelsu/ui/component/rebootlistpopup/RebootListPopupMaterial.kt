package me.weishu.kernelsu.ui.component.rebootlistpopup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.KsuIsValid
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.util.reboot

private data class RebootOption(
    val labelRes: Int,
    val reason: String,
    val icon: ImageVector
)

@Composable
private fun getRebootOptions(): List<RebootOption> {
    return getRebootListOption().map { option ->
        RebootOption(
            labelRes = option.labelRes,
            reason = option.reason,
            icon = when (option.reason) {
                "" -> Icons.Outlined.Refresh
                "userspace" -> Icons.Outlined.RestartAlt
                "soft_reboot" -> Icons.AutoMirrored.Outlined.RotateRight
                "recovery" -> Icons.Outlined.SystemUpdate
                "bootloader" -> Icons.Outlined.Memory
                "download" -> Icons.Outlined.Download
                "edl" -> Icons.Outlined.DeveloperMode
                else -> Icons.Outlined.Refresh
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RebootDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onReboot: (String) -> Unit
) {
    if (!show) return

    val options = getRebootOptions()

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.reboot),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                SegmentedColumn(
                    modifier = Modifier.padding(top = 20.dp),
                    content = options.map { option ->
                        {
                            SegmentedListItem(
                                headlineContent = {
                                    Text(stringResource(option.labelRes))
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = option.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                },
                                onClick = {
                                    onDismiss()
                                    onReboot(option.reason)
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun RebootListPopupMaterial() {
    var showDialog by remember { mutableStateOf(false) }

    KsuIsValid {
        IconButton(onClick = { showDialog = true }) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = stringResource(id = R.string.reboot)
            )
        }

        RebootDialog(
            show = showDialog,
            onDismiss = { showDialog = false },
            onReboot = { reason -> reboot(reason) }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RebootDialogPreview() {
    MaterialTheme {
        RebootDialog(
            show = true,
            onDismiss = {},
            onReboot = {}
        )
    }
}
