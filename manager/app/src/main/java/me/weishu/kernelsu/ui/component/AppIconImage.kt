package me.weishu.kernelsu.ui.component

import android.content.pm.PackageInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun AppIconImage(
    packageInfo: PackageInfo,
    label: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(packageInfo)
            .crossfade(true)
            .build(),
        contentDescription = label,
        modifier = modifier
    )
}
