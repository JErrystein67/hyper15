package cz.liquidglass.launcher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.liquidglass.launcher.data.AppInfo
import cz.liquidglass.launcher.data.AppKey
import cz.liquidglass.launcher.data.HomeItem
import cz.liquidglass.launcher.ui.glass.glass

val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 11.sp,
    textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 4f),
)

@Composable
fun AppIcon(
    info: AppInfo?,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 60.dp,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (info != null) {
            Image(
                bitmap = info.icon,
                contentDescription = info.label,
                modifier = Modifier.size(iconSize).clip(RoundedCornerShape(iconSize * 0.225f)),
            )
        }
        if (showLabel) {
            Text(
                text = info?.label.orEmpty(),
                style = LabelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
fun FolderTile(
    folder: HomeItem.Folder,
    appMap: Map<AppKey, AppInfo>,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .size(size)
                .glass(size * 0.225f, shadowRadius = 12.dp, shadowAlpha = 0.18f)
                .padding(size * 0.09f),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            folder.apps.take(9).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { k ->
                        appMap[k]?.let {
                            Image(
                                bitmap = it.icon,
                                contentDescription = null,
                                modifier = Modifier.size(size * 0.22f).clip(RoundedCornerShape(size * 0.05f)),
                            )
                        }
                    }
                }
            }
        }
        if (showLabel) {
            Text(
                text = folder.title,
                style = LabelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

/** Non-interactive visual of an app / folder (also used for the drag ghost). */
@Composable
fun ItemVisual(
    item: HomeItem,
    appMap: Map<AppKey, AppInfo>,
    showLabel: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    when (item) {
        is HomeItem.App -> AppIcon(appMap[item.key], showLabel, modifier)
        is HomeItem.Folder -> FolderTile(item, appMap, showLabel, modifier)
        is HomeItem.Widget -> Unit
    }
}
