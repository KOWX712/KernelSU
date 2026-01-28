package me.weishu.kernelsu.ui.component

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.reboot

private data class RebootOption(
    val titleRes: Int,
    val reason: String,
    val icon: ImageVector
)

@Composable
private fun getRebootOptions(): List<RebootOption> {
    val pm = LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?

    @Suppress("DEPRECATION")
    val isRebootingUserspaceSupported =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true

    val options = mutableListOf(
        RebootOption(R.string.reboot, "", Icons.Outlined.Refresh),
        RebootOption(R.string.reboot_recovery, "recovery", Icons.Outlined.SystemUpdate),
        RebootOption(R.string.reboot_bootloader, "bootloader", Icons.Outlined.Memory),
        RebootOption(R.string.reboot_download, "download", Icons.Outlined.Download),
        RebootOption(R.string.reboot_edl, "edl", Icons.Outlined.DeveloperMode)
    )
    if (isRebootingUserspaceSupported) {
        options.add(1, RebootOption(R.string.reboot_userspace, "userspace", Icons.Outlined.RestartAlt))
    }
    return options
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

                val content = options.map { option ->
                    @Composable
                    {
                        ExpressiveListItem(
                            modifier = Modifier.padding(vertical = 8.dp),
                            headlineContent = {
                                Text(stringResource(option.titleRes))
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

                ExpressiveColumn(
                    modifier = Modifier.padding(top = 20.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun RebootListPopup() {
    var showDialog by remember { mutableStateOf(false) }

    KsuIsValid {
        IconButton(onClick = { showDialog = true }) {
            Icon(
                imageVector = Icons.Filled.Refresh,
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
