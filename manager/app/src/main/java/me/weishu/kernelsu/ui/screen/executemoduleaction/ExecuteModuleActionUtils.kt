package me.weishu.kernelsu.ui.screen.executemoduleaction

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.runModuleAction
import me.weishu.kernelsu.ui.viewmodel.ModuleViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExecuteModuleActionEffect(
    moduleId: String,
    text: String,
    logContent: StringBuilder,
    fromShortcut: Boolean,
    onTextUpdate: (String) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val noModule = context.getString(R.string.no_such_module)
    val moduleUnavailable = context.getString(R.string.module_unavailable)

    LaunchedEffect(Unit) {
        if (text.isNotEmpty()) {
            return@LaunchedEffect
        }
        val viewModel = ModuleViewModel()
        if (viewModel.moduleList.isEmpty()) {
            viewModel.loadModuleList()
        }
        val moduleInfo = viewModel.moduleList.find { info -> info.id == moduleId }
        if (moduleInfo == null) {
            Toast.makeText(context, noModule.format(moduleId), Toast.LENGTH_SHORT).show()
            onExit()
            return@LaunchedEffect
        }
        if (!moduleInfo.hasActionScript) {
            onExit()
            return@LaunchedEffect
        }
        if (!moduleInfo.enabled || moduleInfo.update || moduleInfo.remove) {
            Toast.makeText(context, moduleUnavailable.format(moduleInfo.name), Toast.LENGTH_SHORT).show()
            onExit()
            return@LaunchedEffect
        }
        var actionResult: Boolean
        var currentText = text
        withContext(Dispatchers.IO) {
            runModuleAction(
                moduleId = moduleId,
                onStdout = {
                    val tempText = "$it\n"
                    if (tempText.startsWith("[H[J")) { // clear command
                        currentText = tempText.substring(6)
                    } else {
                        currentText += tempText
                    }
                    onTextUpdate(currentText)
                    logContent.append(it).append("\n")
                },
                onStderr = {
                    logContent.append(it).append("\n")
                }
            ).let {
                actionResult = it
            }
        }
        if (actionResult) {
            if (fromShortcut) {
                Toast.makeText(
                    context,
                    context.getString(R.string.module_action_success),
                    Toast.LENGTH_SHORT
                ).show()
            }
            onExit()
        }
    }
}

@Composable
fun saveLog(
    logContent: StringBuilder,
    context: Context,
    scope: CoroutineScope
): () -> Unit {
    return {
        scope.launch {
            val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
            val date = format.format(Date())
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "KernelSU_module_action_log_${date}.log"
            )
            file.writeText(logContent.toString())
            Toast.makeText(context, "Log saved to ${file.absolutePath}", Toast.LENGTH_SHORT).show()
        }
    }
}
