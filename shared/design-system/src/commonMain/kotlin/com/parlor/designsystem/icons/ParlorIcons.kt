/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.parlor.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small, dependency-free vector set used by Parlor controls.
 *
 * Keeping the reviewed icons here avoids font-dependent Unicode glyphs and the
 * binary cost of the full Material Icons artifact. Directional vectors opt in
 * to Compose's automatic RTL mirroring.
 */
object ParlorIcons {
    val Settings: ImageVector = parlorIcon("Settings") {
        moveTo(19.14f, 12.94f)
        curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
        curveToRelative(0.0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
        lineToRelative(2.03f, -1.58f)
        curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
        lineToRelative(-1.92f, -3.32f)
        curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
        lineToRelative(-2.39f, 0.96f)
        curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
        lineTo(14.4f, 2.81f)
        curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
        horizontalLineToRelative(-3.84f)
        curveToRelative(-0.24f, 0.0f, -0.43f, 0.17f, -0.47f, 0.41f)
        lineTo(9.25f, 5.35f)
        curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
        lineTo(5.24f, 5.33f)
        curveToRelative(-0.22f, -0.08f, -0.47f, 0.0f, -0.59f, 0.22f)
        lineTo(2.74f, 8.87f)
        curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
        lineToRelative(2.03f, 1.58f)
        curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12.0f)
        reflectiveCurveToRelative(0.02f, 0.64f, 0.07f, 0.94f)
        lineToRelative(-2.03f, 1.58f)
        curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
        lineToRelative(1.92f, 3.32f)
        curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
        lineToRelative(2.39f, -0.96f)
        curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
        lineToRelative(0.36f, 2.54f)
        curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
        horizontalLineToRelative(3.84f)
        curveToRelative(0.24f, 0.0f, 0.44f, -0.17f, 0.47f, -0.41f)
        lineToRelative(0.36f, -2.54f)
        curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
        lineToRelative(2.39f, 0.96f)
        curveToRelative(0.22f, 0.08f, 0.47f, 0.0f, 0.59f, -0.22f)
        lineToRelative(1.92f, -3.32f)
        curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
        lineTo(19.14f, 12.94f)
        close()
        moveTo(12.0f, 15.6f)
        curveToRelative(-1.98f, 0.0f, -3.6f, -1.62f, -3.6f, -3.6f)
        reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
        reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
        reflectiveCurveTo(13.98f, 15.6f, 12.0f, 15.6f)
        close()
    }

    val Back: ImageVector = parlorIcon("Back", autoMirror = true) {
        moveTo(20.0f, 11.0f)
        horizontalLineTo(7.83f)
        lineToRelative(5.59f, -5.59f)
        lineTo(12.0f, 4.0f)
        lineToRelative(-8.0f, 8.0f)
        lineToRelative(8.0f, 8.0f)
        lineToRelative(1.41f, -1.41f)
        lineTo(7.83f, 13.0f)
        horizontalLineTo(20.0f)
        verticalLineToRelative(-2.0f)
        close()
    }

    val Forward: ImageVector = parlorIcon("Forward", autoMirror = true) {
        moveTo(12.0f, 4.0f)
        lineToRelative(-1.41f, 1.41f)
        lineTo(16.17f, 11.0f)
        horizontalLineTo(4.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(12.17f)
        lineToRelative(-5.58f, 5.59f)
        lineTo(12.0f, 20.0f)
        lineToRelative(8.0f, -8.0f)
        close()
    }

    val Pause: ImageVector = parlorIcon("Pause") {
        moveTo(6.0f, 19.0f)
        horizontalLineToRelative(4.0f)
        verticalLineTo(5.0f)
        horizontalLineTo(6.0f)
        verticalLineToRelative(14.0f)
        close()
        moveTo(14.0f, 5.0f)
        verticalLineToRelative(14.0f)
        horizontalLineToRelative(4.0f)
        verticalLineTo(5.0f)
        horizontalLineToRelative(-4.0f)
        close()
    }

    val Add: ImageVector = parlorIcon("Add") {
        moveTo(19.0f, 13.0f)
        horizontalLineToRelative(-6.0f)
        verticalLineToRelative(6.0f)
        horizontalLineToRelative(-2.0f)
        verticalLineToRelative(-6.0f)
        horizontalLineTo(5.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(6.0f)
        verticalLineTo(5.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(6.0f)
        horizontalLineToRelative(6.0f)
        verticalLineToRelative(2.0f)
        close()
    }

    val Remove: ImageVector = parlorIcon("Remove") {
        moveTo(19.0f, 13.0f)
        horizontalLineTo(5.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(14.0f)
        verticalLineToRelative(2.0f)
        close()
    }

    val FolderOpen: ImageVector = parlorIcon("FolderOpen") {
        moveTo(20.0f, 6.0f)
        horizontalLineToRelative(-8.0f)
        lineToRelative(-2.0f, -2.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-1.11f, 0.0f, -1.99f, 0.89f, -1.99f, 2.0f)
        lineTo(2.0f, 18.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(8.0f)
        curveToRelative(0.0f, -1.11f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(20.0f, 18.0f)
        horizontalLineTo(4.0f)
        verticalLineTo(8.0f)
        horizontalLineToRelative(16.0f)
        verticalLineToRelative(10.0f)
        close()
    }
}

private fun parlorIcon(
    name: String,
    autoMirror: Boolean = false,
    pathBuilder: PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = "Parlor.$name",
    defaultWidth = ICON_VIEWPORT.dp,
    defaultHeight = ICON_VIEWPORT.dp,
    viewportWidth = ICON_VIEWPORT,
    viewportHeight = ICON_VIEWPORT,
    autoMirror = autoMirror,
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathBuilder = pathBuilder,
    )
}.build()

private const val ICON_VIEWPORT = 24f
