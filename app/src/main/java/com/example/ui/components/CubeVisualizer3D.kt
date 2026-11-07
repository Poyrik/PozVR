package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gearvr.GearVRState
import com.example.ui.theme.VrAmber
import com.example.ui.theme.VrCyan
import com.example.ui.theme.VrGreen
import com.example.ui.theme.VrOutline
import com.example.ui.theme.VrSurfaceVariant
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class Point3D(val x: Float, val y: Float, val z: Float)
private data class Point2D(val x: Float, val y: Float, val depth: Float)

private data class CubeFace(
    val indices: IntArray,
    val baseColor: Color,
    val name: String
)

@Composable
fun CubeVisualizer3D(
    gearVRState: GearVRState,
    modifier: Modifier = Modifier
) {
    // Dynamic face colors responding to button presses
    val frontFaceColor by animateColorAsState(
        targetValue = if (gearVRState.triggerPressed) Color(0xFF00FFFF) else Color(0xFF007A8A),
        animationSpec = tween(durationMillis = 100),
        label = "frontFace"
    )

    val topFaceColor by animateColorAsState(
        targetValue = if (gearVRState.touchpadClicked) Color(0xFFFFB300) else Color(0xFF995C00),
        animationSpec = tween(durationMillis = 100),
        label = "topFace"
    )

    val backFaceColor by animateColorAsState(
        targetValue = if (gearVRState.backPressed) Color(0xFFFF5252) else Color(0xFF6B1D1D),
        animationSpec = tween(durationMillis = 100),
        label = "backFace"
    )

    val homeFaceColor by animateColorAsState(
        targetValue = if (gearVRState.homePressed) Color(0xFF7C4DFF) else Color(0xFF381E72),
        animationSpec = tween(durationMillis = 100),
        label = "homeFace"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cube_visualizer_card"),
        shape = RoundedCornerShape(16.dp),
        color = VrSurfaceVariant,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with controller live status and angles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "3D CONTROLLER POSE",
                        style = MaterialTheme.typography.labelSmall,
                        color = VrCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Pitch: %+.1f°  Yaw: %+.1f°  Roll: %+.1f°".format(
                            gearVRState.pitchDeg,
                            gearVRState.yawDeg,
                            gearVRState.rollDeg
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (gearVRState.packetsPerSec > 0) Color(0xFF003915) else Color(0xFF2E3846)
                ) {
                    Text(
                        text = "${gearVRState.packetsPerSec} Hz",
                        color = if (gearVRState.packetsPerSec > 0) VrGreen else Color(0xFF8B949E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 3D Canvas Viewport with Touchpad Mini-Radar overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(vertical = 8.dp)
            ) {
                // 3D Projected Cube Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("cube_3d_canvas")
                ) {
                    drawProjectedCube(
                        gearVRState = gearVRState,
                        frontColor = frontFaceColor,
                        topColor = topFaceColor,
                        backColor = backFaceColor,
                        homeColor = homeFaceColor
                    )
                }

                // Touchpad Mini-Radar (Corner overlay)
                TouchpadMiniRadar(
                    touchX = gearVRState.touchX,
                    touchY = gearVRState.touchY,
                    isTouching = gearVRState.isTouching,
                    isClicked = gearVRState.touchpadClicked,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }

            // Active button telemetry pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                ButtonIndicatorPill(name = "Trigger", active = gearVRState.triggerPressed, activeColor = VrCyan)
                ButtonIndicatorPill(name = "Pad", active = gearVRState.touchpadClicked, activeColor = VrAmber)
                ButtonIndicatorPill(name = "Back", active = gearVRState.backPressed, activeColor = Color(0xFFFF5252))
                ButtonIndicatorPill(name = "Home", active = gearVRState.homePressed, activeColor = Color(0xFFB388FF))
                ButtonIndicatorPill(name = "Vol+", active = gearVRState.volumeUpPressed, activeColor = VrGreen)
                ButtonIndicatorPill(name = "Vol-", active = gearVRState.volumeDownPressed, activeColor = VrGreen)
            }
        }
    }
}

/**
 * 3D Geometry and perspective rendering of the rotating cube.
 */
private fun DrawScope.drawProjectedCube(
    gearVRState: GearVRState,
    frontColor: Color,
    topColor: Color,
    backColor: Color,
    homeColor: Color
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val cubeSize = minOf(size.width, size.height) * 0.28f
    val focalLength = 480f
    val cameraDist = 4.0f

    // 8 vertices of a 3D cube centered at origin
    val s = 1.0f
    val vertices = arrayOf(
        Point3D(-s, -s, -s), // 0: Bottom-Left-Back
        Point3D( s, -s, -s), // 1: Bottom-Right-Back
        Point3D( s,  s, -s), // 2: Top-Right-Back
        Point3D(-s,  s, -s), // 3: Top-Left-Back
        Point3D(-s, -s,  s), // 4: Bottom-Left-Front
        Point3D( s, -s,  s), // 5: Bottom-Right-Front
        Point3D( s,  s,  s), // 6: Top-Right-Front
        Point3D(-s,  s,  s)  // 7: Top-Left-Front
    )

    // 6 Faces defined by vertex indices (counter-clockwise)
    val faces = listOf(
        CubeFace(intArrayOf(4, 5, 6, 7), frontColor, "Front (+Z)"),
        CubeFace(intArrayOf(3, 2, 6, 7), topColor, "Top (+Y)"),
        CubeFace(intArrayOf(1, 0, 3, 2), backColor, "Back (-Z)"),
        CubeFace(intArrayOf(0, 1, 5, 4), homeColor, "Bottom (-Y)"),
        CubeFace(intArrayOf(5, 1, 2, 6), Color(0xFF00E676), "Right (+X)"),
        CubeFace(intArrayOf(0, 4, 7, 3), Color(0xFF0091EA), "Left (-X)")
    )

    // Rotate vertices using Quaternion or Euler rotation matrix
    val qW = gearVRState.quatW
    val qX = gearVRState.quatX
    val qY = gearVRState.quatY
    val qZ = gearVRState.quatZ

    val rotatedVertices = vertices.map { v ->
        rotateVectorByQuaternion(v, qW, qX, qY, qZ)
    }

    // Project 3D points to 2D screen coordinates with perspective
    val projectedPoints = rotatedVertices.map { v ->
        val z = v.z + cameraDist
        val scale = focalLength / (z.coerceAtLeast(0.1f))
        Point2D(
            x = cx + v.x * cubeSize * scale / focalLength,
            y = cy - v.y * cubeSize * scale / focalLength, // Invert Y for screen coords
            depth = z
        )
    }

    // Sort faces by average depth (Painter's Algorithm for depth sorting)
    val sortedFaces = faces.map { face ->
        val avgZ = face.indices.map { rotatedVertices[it].z }.average().toFloat()
        Pair(face, avgZ)
    }.sortedBy { it.second } // Draw farthest faces first

    // Draw sorted faces
    sortedFaces.forEach { (face, _) ->
        val path = Path()
        val p0 = projectedPoints[face.indices[0]]
        path.moveTo(p0.x, p0.y)
        for (i in 1 until face.indices.size) {
            val p = projectedPoints[face.indices[i]]
            path.lineTo(p.x, p.y)
        }
        path.close()

        // Calculate face normal to apply lighting shading
        val v0 = rotatedVertices[face.indices[0]]
        val v1 = rotatedVertices[face.indices[1]]
        val v2 = rotatedVertices[face.indices[2]]

        val ax = v1.x - v0.x
        val ay = v1.y - v0.y
        val az = v1.z - v0.z
        val bx = v2.x - v0.x
        val by = v2.y - v0.y
        val bz = v2.z - v0.z

        // Cross product gives normal
        var nx = ay * bz - az * by
        var ny = az * bx - ax * bz
        var nz = ax * by - ay * bx
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
        nx /= len
        ny /= len
        nz /= len

        // Back-face culling / directional light intensity from camera (towards +Z)
        val lightIntensity = (nz * 0.5f + 0.5f).coerceIn(0.25f, 1.0f)
        val shadedColor = face.baseColor.copy(
            red = (face.baseColor.red * lightIntensity).coerceIn(0f, 1f),
            green = (face.baseColor.green * lightIntensity).coerceIn(0f, 1f),
            blue = (face.baseColor.blue * lightIntensity).coerceIn(0f, 1f),
            alpha = 0.90f
        )

        // Draw solid face fill
        drawPath(path = path, color = shadedColor, style = Fill)

        // Draw wireframe edge borders
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.35f),
            style = Stroke(width = 2.5f)
        )
    }

    // Draw coordinate orientation axes projecting from cube center
    drawAxes(cx, cy, cubeSize * 0.7f, qW, qX, qY, qZ)
}

/**
 * Draws 3D XYZ coordinate axes to clearly identify controller forward direction.
 */
private fun DrawScope.drawAxes(
    cx: Float,
    cy: Float,
    length: Float,
    qW: Float,
    qX: Float,
    qY: Float,
    qZ: Float
) {
    val axisX = rotateVectorByQuaternion(Point3D(1f, 0f, 0f), qW, qX, qY, qZ)
    val axisY = rotateVectorByQuaternion(Point3D(0f, 1f, 0f), qW, qX, qY, qZ)
    val axisZ = rotateVectorByQuaternion(Point3D(0f, 0f, 1f), qW, qX, qY, qZ)

    // Red = X (Right), Green = Y (Up), Cyan = Z (Forward/Pointing)
    drawLine(Color(0xFFFF5252), Offset(cx, cy), Offset(cx + axisX.x * length, cy - axisX.y * length), strokeWidth = 3f)
    drawLine(Color(0xFF00E676), Offset(cx, cy), Offset(cx + axisY.x * length, cy - axisY.y * length), strokeWidth = 3f)
    drawLine(Color(0xFF00E5FF), Offset(cx, cy), Offset(cx + axisZ.x * length, cy - axisZ.y * length), strokeWidth = 4f)
}

/**
 * Rotate a 3D vector v using quaternion q: v' = q * v * q^-1
 */
private fun rotateVectorByQuaternion(v: Point3D, w: Float, x: Float, y: Float, z: Float): Point3D {
    // t = 2 * cross(q.xyz, v)
    val tx = 2.0f * (y * v.z - z * v.y)
    val ty = 2.0f * (z * v.x - x * v.z)
    val tz = 2.0f * (x * v.y - y * v.x)

    // v' = v + w * t + cross(q.xyz, t)
    val rx = v.x + w * tx + (y * tz - z * ty)
    val ry = v.y + w * ty + (z * tx - x * tz)
    val rz = v.z + w * tz + (x * ty - y * tx)

    return Point3D(rx, ry, rz)
}

/**
 * 2D Circular Touchpad Radar Overlay
 */
@Composable
private fun TouchpadMiniRadar(
    touchX: Float,
    touchY: Float,
    isTouching: Boolean,
    isClicked: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(76.dp)
            .background(Color(0xFF0B0E14).copy(alpha = 0.85f), CircleShape)
            .border(1.5.dp, if (isClicked) VrAmber else VrOutline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 4f

            // Crosshair lines
            drawLine(
                color = VrOutline.copy(alpha = 0.5f),
                start = Offset(center.x, 4f),
                end = Offset(center.x, size.height - 4f),
                strokeWidth = 1f
            )
            drawLine(
                color = VrOutline.copy(alpha = 0.5f),
                start = Offset(4f, center.y),
                end = Offset(size.width - 4f, center.y),
                strokeWidth = 1f
            )

            // Draw finger touch point (touchpad coordinates are 0..315)
            if (isTouching) {
                val normX = (touchX / 315f - 0.5f) * 2f
                val normY = (touchY / 315f - 0.5f) * 2f
                val fingerOffset = Offset(
                    x = center.x + normX * (radius - 8f),
                    y = center.y + normY * (radius - 8f)
                )

                // Glow ring
                drawCircle(
                    color = if (isClicked) VrAmber.copy(alpha = 0.35f) else VrCyan.copy(alpha = 0.35f),
                    radius = 9f,
                    center = fingerOffset
                )
                // Center dot
                drawCircle(
                    color = if (isClicked) VrAmber else VrCyan,
                    radius = 5f,
                    center = fingerOffset
                )
            }
        }

        Text(
            text = if (isClicked) "CLICK" else if (isTouching) "TOUCH" else "PAD",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isClicked) VrAmber else if (isTouching) VrCyan else Color(0xFF6E7681)
        )
    }
}

/**
 * Reusable pill chip indicating hardware button states.
 */
@Composable
private fun ButtonIndicatorPill(
    name: String,
    active: Boolean,
    activeColor: Color
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) activeColor else Color(0xFF1E232E),
        animationSpec = tween(durationMillis = 80),
        label = "btnBg"
    )
    val textColor = if (active) Color(0xFF0B0E14) else Color(0xFF8B949E)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        modifier = Modifier.height(26.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}
