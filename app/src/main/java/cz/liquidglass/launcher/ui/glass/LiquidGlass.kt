package cz.liquidglass.launcher.ui.glass

import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.abs
import android.graphics.Paint as NativePaint

/** Direction towards the virtual light source (screen space, y down). Driven by device tilt. */
val LocalGlassLight = staticCompositionLocalOf<() -> Offset> { { Offset(-0.55f, -0.85f) } }

@Composable
fun rememberTiltLight(): () -> Offset {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset(-0.55f, -0.85f)) }
    LifecycleResumeEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val tx = -0.55f - (e.values[0] / 9.81f).coerceIn(-1f, 1f) * 0.5f
                val ty = -0.85f + (e.values[1] / 9.81f - 1f).coerceIn(-1f, 1f) * 0.5f
                val cur = state.value
                val nx = cur.x + (tx - cur.x) * 0.08f
                val ny = cur.y + (ty - cur.y) * 0.08f
                if (abs(nx - cur.x) > 0.004f || abs(ny - cur.y) > 0.004f) state.value = Offset(nx, ny)
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { if (sensor != null) sm.unregisterListener(listener) }
    }
    return { state.value }
}

private const val GLASS_AGSL = """
uniform float2 size;
uniform float radius;
uniform float2 light;
uniform float4 tint;
uniform float strength;

float sdBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 fragCoord) {
    float2 hs = size * 0.5;
    float2 p = fragCoord - hs;
    float d = sdBox(p, hs, radius);
    float2 e = float2(1.0, 0.0);
    float2 n = normalize(float2(
        sdBox(p + e.xy, hs, radius) - sdBox(p - e.xy, hs, radius),
        sdBox(p + e.yx, hs, radius) - sdBox(p - e.yx, hs, radius)) + 0.0001);

    float depth = clamp(-d, 0.0, 48.0);
    float rim = exp(-depth / 1.7);       // hairline specular edge
    float bevel = exp(-depth / 10.0);    // soft inner refraction glow

    float facing = dot(n, normalize(light));
    float spec = pow(max(facing, 0.0), 2.4);   // edge turned to the light
    float back = pow(max(-facing, 0.0), 2.4);  // caustic on the opposite edge

    float sheen = 1.0 - clamp(fragCoord.y / size.y, 0.0, 1.0);
    float body = tint.a + 0.03 * sheen + 0.05 * bevel;
    float edge = (0.85 * spec + 0.32 * back) * rim * strength
               + (0.16 * spec + 0.07 * back) * bevel * strength;

    float alpha = clamp(body + edge, 0.0, 1.0);
    return half4(half3(tint.rgb * alpha), alpha);
}
"""

@RequiresApi(33)
private class GlassShader {
    private val shader = RuntimeShader(GLASS_AGSL)
    val brush = ShaderBrush(shader)

    fun update(w: Float, h: Float, r: Float, light: Offset, tint: Color, rim: Float) {
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("radius", r)
        shader.setFloatUniform("light", light.x, light.y)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
        shader.setFloatUniform("strength", rim)
    }
}

private fun shadowPaint(blur: Float, alpha: Float): NativePaint =
    NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        setShadowLayer(blur.coerceAtLeast(0.1f), 0f, blur * 0.33f, Color.Black.copy(alpha = alpha).toArgb())
    }

/**
 * iOS 18 "Liquid Glass" surface.
 *
 *  - body: ~10 % white with a vertical sheen (AGSL on API 33+, gradient fallback below),
 *  - specular highlights on the rim that follow the tilt-driven [LocalGlassLight],
 *  - ambient shadow drawn ONLY outside the shape (ClipOp.Difference) so it never shows through the glass.
 *
 * Do not put a clipping ancestor around the element or the shadow will be cut off. Apply `.clip(...)`
 * AFTER this modifier to clip the content to the same shape (see [glass]).
 */
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp = 28.dp,
    tint: Color = Color.White.copy(alpha = 0.10f),
    shadowRadius: Dp = 30.dp,
    shadowAlpha: Float = 0.26f,
    rim: Float = 1f,
): Modifier {
    val light = LocalGlassLight.current
    return drawWithCache {
        val w = size.width
        val h = size.height
        val r = cornerRadius.toPx().coerceAtMost(minOf(w, h) / 2f)
        val corner = CornerRadius(r)
        val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, corner)) }
        val ambient = shadowPaint(shadowRadius.toPx(), shadowAlpha)
        val contact = shadowPaint(shadowRadius.toPx() * 0.28f, shadowAlpha * 0.55f)
        val gs = if (Build.VERSION.SDK_INT >= 33) GlassShader() else null
        val fallbackFill = Brush.verticalGradient(
            listOf(tint.copy(alpha = (tint.alpha + 0.05f).coerceAtMost(1f)), tint)
        )
        val fallbackRim = Brush.linearGradient(
            listOf(
                Color.White.copy(0.62f), Color.White.copy(0.05f),
                Color.White.copy(0.05f), Color.White.copy(0.28f),
            ),
            start = Offset.Zero, end = Offset(w, h),
        )
        val stroke = Stroke(1.dp.toPx())

        onDrawWithContent {
            // 1) shadow strictly outside the glass geometry
            clipPath(outline, ClipOp.Difference) {
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    n.drawRoundRect(1f, 1f, w - 1f, h - 1f, r, r, ambient)
                    n.drawRoundRect(1f, 1f, w - 1f, h - 1f, r, r, contact)
                }
            }
            // 2) glass body + specular rim
            if (gs != null) {
                gs.update(w, h, r, light(), tint, rim)
                drawRoundRect(gs.brush, cornerRadius = corner)
            } else {
                drawRoundRect(fallbackFill, cornerRadius = corner)
                drawRoundRect(fallbackRim, cornerRadius = corner, style = stroke)
            }
            drawContent()
        }
    }
}

/** [liquidGlass] + content clipped to the same rounded shape. */
@Composable
fun Modifier.glass(
    cornerRadius: Dp = 28.dp,
    tint: Color = Color.White.copy(alpha = 0.10f),
    shadowRadius: Dp = 30.dp,
    shadowAlpha: Float = 0.26f,
): Modifier =
    this.liquidGlass(cornerRadius, tint, shadowRadius, shadowAlpha).clip(RoundedCornerShape(cornerRadius))

@Composable
fun GlassPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .glass(22.dp, shadowRadius = 18.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
