package com.samar.jarviswidget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class JarvisWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            JarvisWidgetContent(context)
        }
    }
}

@Composable
private fun JarvisWidgetContent(context: Context) {
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF0A0F1A)))
            .padding(16.dp)
            .clickable(actionStartActivity(openAppIntent)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "SAMAR",
            style = TextStyle(
                color = ColorProvider(Color(0xFF00F0FF)),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = "Tap to launch assistant",
            style = TextStyle(
                color = ColorProvider(Color(0xFF94A3B8)),
                fontSize = 13.sp
            )
        )
    }
}
