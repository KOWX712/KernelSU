package me.weishu.kernelsu.ui.component.selectlkmdialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.screen.install.LkmVariant
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference

@Composable
fun SelectLkmDialogMiuix(
    show: Boolean,
    currentVariant: LkmVariant,
    onDismissRequest: () -> Unit,
    onSelectVariant: (LkmVariant) -> Unit
) {
    val variants = listOf(
        LkmVariant.KOWSU to R.string.install_lkm_kowsu,
        LkmVariant.XXKSU to R.string.install_lkm_xxksu,
        LkmVariant.CUSTOM to R.string.install_upload_lkm_file
    )

    val currentSelection = rememberSaveable { mutableStateOf(currentVariant) }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.install_select_lkm_variant),
        onDismissRequest = onDismissRequest,
        insideMargin = DpSize(0.dp, 24.dp),
        content = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(variants) { (variant, stringRes) ->
                        CheckboxPreference(
                            title = stringResource(stringRes),
                            insideMargin = PaddingValues(horizontal = 30.dp, vertical = 16.dp),
                            checkboxLocation = CheckboxLocation.End,
                            checked = currentSelection.value == variant,
                            holdDownState = currentSelection.value == variant,
                            onCheckedChange = { _ ->
                                currentSelection.value = variant
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            currentSelection.value = currentVariant
                        },
                        text = stringResource(android.R.string.cancel),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(
                        onClick = {
                            onSelectVariant(currentSelection.value)
                            onDismissRequest()
                        },
                        text = stringResource(R.string.confirm),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}
