package me.weishu.kernelsu.ui.component.selectlkmdialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.screen.install.LkmVariant
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun SelectLkmDialogMiuix(
    show: Boolean,
    currentVariant: LkmVariant,
    onDismissRequest: () -> Unit,
    onSelectVariant: (LkmVariant) -> Unit
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.install_select_lkm_variant),
        onDismissRequest = onDismissRequest,
        content = {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column {
                    BasicComponent(
                        title = stringResource(R.string.install_lkm_kowsu),
                        onClick = { onSelectVariant(LkmVariant.KOWSU) },
                        insideMargin = PaddingValues(16.dp, 12.dp)
                    )
                    BasicComponent(
                        title = stringResource(R.string.install_lkm_xxksu),
                        onClick = { onSelectVariant(LkmVariant.XXKSU) },
                        insideMargin = PaddingValues(16.dp, 12.dp)
                    )
                    BasicComponent(
                        title = stringResource(R.string.install_upload_lkm_file),
                        onClick = { onSelectVariant(LkmVariant.CUSTOM) },
                        insideMargin = PaddingValues(16.dp, 12.dp)
                    )
                }
            }
        }
    )
}
