package me.weishu.kernelsu.ui.component.selectlkmdialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedRadioItem
import me.weishu.kernelsu.ui.screen.install.LkmVariant

@Composable
fun SelectLkmDialog(
    show: Boolean,
    currentVariant: LkmVariant,
    onDismissRequest: () -> Unit,
    onSelectVariant: (LkmVariant) -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.install_select_lkm_variant)) },
        text = {
            Column {
                SegmentedColumn(
                    content = listOf(
                        {
                            SegmentedRadioItem(
                                title = stringResource(R.string.install_lkm_kowsu),
                                selected = currentVariant == LkmVariant.KOWSU,
                                onClick = { onSelectVariant(LkmVariant.KOWSU) }
                            )
                        },
                        {
                            SegmentedRadioItem(
                                title = stringResource(R.string.install_lkm_xxksu),
                                selected = currentVariant == LkmVariant.XXKSU,
                                onClick = { onSelectVariant(LkmVariant.XXKSU) }
                            )
                        },
                        {
                            SegmentedRadioItem(
                                title = stringResource(R.string.install_upload_lkm_file),
                                selected = currentVariant == LkmVariant.CUSTOM,
                                onClick = { onSelectVariant(LkmVariant.CUSTOM) }
                            )
                        }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}
