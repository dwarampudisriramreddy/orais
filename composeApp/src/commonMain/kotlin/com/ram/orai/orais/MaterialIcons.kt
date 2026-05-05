package com.ram.orai.orais

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Design Icons - Clear, filled icons for better visibility
object MaterialIcons {
    val Settings: ImageVector
        get() {
            if (_settings != null) return _settings!!
            _settings = ImageVector.Builder(
                name = "Settings",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                // Settings gear icon - filled Material Design style
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(19.14f, 12.94f)
                    curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
                    curveToRelative(0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
                    lineToRelative(2.03f, -1.58f)
                    curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
                    lineToRelative(-1.92f, -3.32f)
                    curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
                    lineToRelative(-2.39f, 0.96f)
                    curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
                    lineToRelative(-0.36f, -2.54f)
                    curveToRelative(-0.05f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
                    horizontalLineToRelative(-3.84f)
                    curveToRelative(-0.24f, 0f, -0.43f, 0.17f, -0.47f, 0.41f)
                    lineToRelative(-0.36f, 2.54f)
                    curveToRelative(-0.59f, 0.24f, -1.13f, 0.56f, -1.62f, 0.94f)
                    lineToRelative(-2.39f, -0.96f)
                    curveToRelative(-0.22f, -0.08f, -0.47f, 0f, -0.59f, 0.22f)
                    lineToRelative(-1.92f, 3.32f)
                    curveToRelative(-0.12f, 0.21f, -0.08f, 0.47f, 0.12f, 0.61f)
                    lineToRelative(2.03f, 1.58f)
                    curveToRelative(-0.05f, 0.3f, -0.07f, 0.62f, -0.07f, 0.94f)
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
                    curveToRelative(0.24f, 0f, 0.44f, -0.17f, 0.47f, -0.41f)
                    lineToRelative(0.36f, -2.54f)
                    curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
                    lineToRelative(2.39f, 0.96f)
                    curveToRelative(0.22f, 0.08f, 0.47f, 0f, 0.59f, -0.22f)
                    lineToRelative(1.92f, -3.32f)
                    curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
                    lineToRelative(-2.01f, -1.58f)
                    close()
                    moveTo(12f, 15.6f)
                    curveToRelative(-1.98f, 0f, -3.6f, -1.62f, -3.6f, -3.6f)
                    reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
                    reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
                    reflectiveCurveToRelative(-1.62f, 3.6f, -3.6f, 3.6f)
                    close()
                }
            }.build()
            return _settings!!
        }
    private var _settings: ImageVector? = null
    
    val Camera: ImageVector
        get() {
            if (_camera != null) return _camera!!
            _camera = ImageVector.Builder(
                name = "Camera",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(9f, 2f)
                    lineTo(7.17f, 4f)
                    lineTo(4f, 4f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    verticalLineToRelative(12f)
                    curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                    horizontalLineToRelative(16f)
                    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                    verticalLineToRelative(-12f)
                    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                    horizontalLineToRelative(-3.17f)
                    lineTo(15f, 2f)
                    horizontalLineToRelative(-6f)
                    close()
                    moveTo(12f, 17f)
                    curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
                    reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
                    reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
                    reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
                    close()
                }
            }.build()
            return _camera!!
        }
    private var _camera: ImageVector? = null
    
    val CameraSwitch: ImageVector
        get() {
            if (_cameraSwitch != null) return _cameraSwitch!!
            _cameraSwitch = ImageVector.Builder(
                name = "CameraSwitch",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                // Camera icon
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(9f, 2f)
                    lineToRelative(-1.83f, 2f)
                    horizontalLineToRelative(-2.17f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    verticalLineToRelative(10f)
                    curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                    horizontalLineToRelative(12f)
                    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                    verticalLineToRelative(-10f)
                    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                    horizontalLineToRelative(-2.17f)
                    lineToRelative(-1.83f, -2f)
                    horizontalLineToRelative(-6f)
                    close()
                    moveTo(12f, 7f)
                    curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f)
                    reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
                    reflectiveCurveToRelative(-5f, -2.24f, -5f, -5f)
                    reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
                    close()
                }
                // Reverse/swap symbol at bottom
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(8f, 19f)
                    lineToRelative(2f, 2f)
                    lineToRelative(-2f, 2f)
                    verticalLineToRelative(-1.5f)
                    horizontalLineToRelative(-2f)
                    verticalLineToRelative(-1f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(-1.5f)
                    close()
                }
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(16f, 19f)
                    lineToRelative(-2f, 2f)
                    lineToRelative(2f, 2f)
                    verticalLineToRelative(-1.5f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(-1f)
                    horizontalLineToRelative(-2f)
                    verticalLineToRelative(-1.5f)
                    close()
                }
            }.build()
            return _cameraSwitch!!
        }
    private var _cameraSwitch: ImageVector? = null
    
    val FlipHorizontal: ImageVector
        get() {
            if (_flipHorizontal != null) return _flipHorizontal!!
            _flipHorizontal = ImageVector.Builder(
                name = "FlipHorizontal",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                // Mirror/flip icon - shows two arrows pointing away from center
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    // Left arrow pointing left
                    moveTo(4f, 12f)
                    lineToRelative(4f, -4f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(4f)
                    verticalLineToRelative(4f)
                    horizontalLineToRelative(-4f)
                    verticalLineToRelative(2f)
                    lineToRelative(-4f, -4f)
                    close()
                }
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    // Right arrow pointing right
                    moveTo(20f, 12f)
                    lineToRelative(-4f, -4f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(-4f)
                    verticalLineToRelative(4f)
                    horizontalLineToRelative(4f)
                    verticalLineToRelative(2f)
                    lineToRelative(4f, -4f)
                    close()
                }
                // Center line
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(12f, 6f)
                    verticalLineToRelative(12f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(-12f)
                    horizontalLineToRelative(-2f)
                    close()
                }
            }.build()
            return _flipHorizontal!!
        }
    private var _flipHorizontal: ImageVector? = null
    
    val Video: ImageVector
        get() {
            if (_video != null) return _video!!
            _video = ImageVector.Builder(
                name = "Video",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(17f, 10.5f)
                    verticalLineTo(7f)
                    curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                    horizontalLineTo(4f)
                    curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                    verticalLineToRelative(10f)
                    curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                    horizontalLineToRelative(12f)
                    curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                    verticalLineToRelative(-3.5f)
                    lineToRelative(4f, 4f)
                    verticalLineToRelative(-11f)
                    lineToRelative(-4f, 4f)
                    close()
                }
            }.build()
            return _video!!
        }
    private var _video: ImageVector? = null
    
    val Square: ImageVector
        get() {
            if (_square != null) return _square!!
            _square = ImageVector.Builder(
                name = "Square",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(6f, 6f)
                    horizontalLineToRelative(12f)
                    verticalLineToRelative(12f)
                    horizontalLineToRelative(-12f)
                    close()
                }
            }.build()
            return _square!!
        }
    private var _square: ImageVector? = null
    
    val Images: ImageVector
        get() {
            if (_images != null) return _images!!
            _images = ImageVector.Builder(
                name = "Images",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(20f, 4f)
                    horizontalLineTo(4f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    verticalLineToRelative(12f)
                    curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                    horizontalLineToRelative(16f)
                    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                    verticalLineTo(6f)
                    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                    close()
                    moveTo(19f, 18f)
                    horizontalLineTo(5f)
                    lineToRelative(4.5f, -6f)
                    lineToRelative(3f, 4f)
                    lineToRelative(2.5f, -3f)
                    lineToRelative(3f, 5f)
                    horizontalLineToRelative(2f)
                    close()
                }
            }.build()
            return _images!!
        }
    private var _images: ImageVector? = null
    
    val ArrowLeft: ImageVector
        get() {
            if (_arrowLeft != null) return _arrowLeft!!
            _arrowLeft = ImageVector.Builder(
                name = "ArrowLeft",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(20f, 11f)
                    horizontalLineTo(7.83f)
                    lineToRelative(5.59f, -5.59f)
                    lineToRelative(-1.41f, -1.41f)
                    lineToRelative(-8f, 8f)
                    lineToRelative(8f, 8f)
                    lineToRelative(1.41f, -1.41f)
                    lineToRelative(-5.59f, -5.59f)
                    horizontalLineTo(20f)
                    verticalLineToRelative(-2f)
                    close()
                }
            }.build()
            return _arrowLeft!!
        }
    private var _arrowLeft: ImageVector? = null
    
    val Share2: ImageVector
        get() {
            if (_share2 != null) return _share2!!
            _share2 = ImageVector.Builder(
                name = "Share2",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(18f, 16f)
                    curveToRelative(-0.79f, 0f, -1.5f, 0.31f, -2.03f, 0.81f)
                    lineToRelative(-7.03f, -4.05f)
                    curveToRelative(0.15f, -0.46f, 0.23f, -0.96f, 0.23f, -1.48f)
                    curveToRelative(0f, -0.52f, -0.08f, -1.02f, -0.23f, -1.48f)
                    lineToRelative(7.03f, -4.05f)
                    curveToRelative(0.53f, 0.5f, 1.24f, 0.81f, 2.03f, 0.81f)
                    curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
                    reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
                    reflectiveCurveToRelative(-3f, 1.34f, -3f, 3f)
                    curveToRelative(0f, 0.52f, 0.08f, 1.02f, 0.23f, 1.48f)
                    lineToRelative(-7.03f, 4.05f)
                    curveToRelative(-0.53f, -0.5f, -1.24f, -0.81f, -2.03f, -0.81f)
                    curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
                    reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
                    curveToRelative(0.79f, 0f, 1.5f, -0.31f, 2.03f, -0.81f)
                    lineToRelative(7.03f, 4.05f)
                    curveToRelative(-0.15f, 0.46f, -0.23f, 0.96f, -0.23f, 1.48f)
                    curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
                    reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
                    reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
                    close()
                }
            }.build()
            return _share2!!
        }
    private var _share2: ImageVector? = null
    
    val Edit: ImageVector
        get() {
            if (_edit != null) return _edit!!
            _edit = ImageVector.Builder(
                name = "Edit",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(3f, 17.25f)
                    verticalLineToRelative(3.75f)
                    horizontalLineToRelative(3.75f)
                    lineToRelative(11.06f, -11.06f)
                    lineToRelative(-3.75f, -3.75f)
                    lineToRelative(-11.06f, 11.06f)
                    close()
                    moveTo(20.71f, 7.04f)
                    curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                    lineToRelative(-2.34f, -2.34f)
                    curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                    lineToRelative(-1.83f, 1.83f)
                    lineToRelative(3.75f, 3.75f)
                    lineToRelative(1.83f, -1.83f)
                    close()
                }
            }.build()
            return _edit!!
        }
    private var _edit: ImageVector? = null
    
    val Radio: ImageVector
        get() {
            if (_radio != null) return _radio!!
            _radio = ImageVector.Builder(
                name = "Radio",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(12f, 2f)
                    curveToRelative(-5.52f, 0f, -10f, 4.48f, -10f, 10f)
                    reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                    reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                    reflectiveCurveToRelative(-4.48f, -10f, -10f, -10f)
                    close()
                    moveTo(12f, 17f)
                    curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
                    reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
                    reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
                    reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
                    close()
                }
            }.build()
            return _radio!!
        }
    private var _radio: ImageVector? = null
    
    val Mail: ImageVector
        get() {
            if (_mail != null) return _mail!!
            _mail = ImageVector.Builder(
                name = "Mail",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(20f, 4f)
                    horizontalLineTo(4f)
                    curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                    lineTo(2f, 18f)
                    curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                    horizontalLineToRelative(16f)
                    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                    verticalLineTo(6f)
                    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                    close()
                    moveTo(20f, 8f)
                    lineToRelative(-8f, 5f)
                    lineToRelative(-8f, -5f)
                    verticalLineTo(6f)
                    lineToRelative(8f, 5f)
                    lineToRelative(8f, -5f)
                    verticalLineToRelative(2f)
                    close()
                }
            }.build()
            return _mail!!
        }
    private var _mail: ImageVector? = null
    
    val MessageCircle: ImageVector
        get() {
            if (_messageCircle != null) return _messageCircle!!
            _messageCircle = ImageVector.Builder(
                name = "MessageCircle",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(20f, 2f)
                    horizontalLineTo(4f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    verticalLineToRelative(18f)
                    lineToRelative(4f, -4f)
                    horizontalLineToRelative(14f)
                    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                    verticalLineTo(4f)
                    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                    close()
                    moveTo(18f, 14f)
                    horizontalLineTo(6f)
                    verticalLineToRelative(-2f)
                    horizontalLineToRelative(12f)
                    verticalLineToRelative(2f)
                    close()
                    moveTo(18f, 11f)
                    horizontalLineTo(6f)
                    verticalLineTo(9f)
                    horizontalLineToRelative(12f)
                    verticalLineToRelative(2f)
                    close()
                    moveTo(18f, 8f)
                    horizontalLineTo(6f)
                    verticalLineTo(6f)
                    horizontalLineToRelative(12f)
                    verticalLineToRelative(2f)
                    close()
                }
            }.build()
            return _messageCircle!!
        }
    private var _messageCircle: ImageVector? = null
    
    val Plane: ImageVector
        get() {
            if (_plane != null) return _plane!!
            _plane = ImageVector.Builder(
                name = "Plane",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(2.01f, 21f)
                    lineToRelative(19f, -9f)
                    lineToRelative(-19f, -9f)
                    verticalLineToRelative(6f)
                    lineToRelative(15f, 3f)
                    lineToRelative(-15f, 3f)
                    verticalLineToRelative(6f)
                    close()
                }
            }.build()
            return _plane!!
        }
    private var _plane: ImageVector? = null
}


