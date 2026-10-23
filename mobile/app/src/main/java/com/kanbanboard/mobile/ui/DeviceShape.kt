package com.kanbanboard.mobile.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val LocalDeviceCornerShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(20.dp) }
