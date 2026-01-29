package me.weishu.kernelsu.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.KernelSUTheme
import me.weishu.kernelsu.ui.util.getSupportedKmis

@Composable
fun ChooseKmiDialog(
    showDialog: MutableState<Boolean>,
    onSelected: (String?) -> Unit
) {
    if (!showDialog.value) return

    val supportedKmi by produceState(initialValue = emptyList()) {
        value = getSupportedKmis()
    }

    var selectedKmi by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { showDialog.value = false },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelected(selectedKmi)
                    showDialog.value = false
                },
                enabled = selectedKmi != null
            ) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { showDialog.value = false }) {
                Text(stringResource(id = android.R.string.cancel))
            }
        },
        title = {
            Text(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                text = stringResource(R.string.select_kmi),
                textAlign = TextAlign.Center
            )
        },
        text = {
            ExpressiveColumn(
                content = supportedKmi.map { kmi ->
                    {
                        ExpressiveRadioItem(
                            title = kmi,
                            selected = selectedKmi == kmi,
                            onClick = { selectedKmi = kmi }
                        )
                    }
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun ChooseKmiDialogPreview() {
    KernelSUTheme {
        ChooseKmiDialog(
            showDialog = remember { mutableStateOf(true) },
            onSelected = {}
        )
    }
}
