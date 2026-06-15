package com.colworx.babycam.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colworx.babycam.ui.theme.IndigoDeep
import com.colworx.babycam.ui.theme.Lavender100

/** Filled primary action button (full width). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = IndigoDeep, contentColor = Color.White),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) { Text(text, fontWeight = FontWeight.Medium, fontSize = 15.sp) }
}

/** Soft secondary button (lavender bg). */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Lavender100, contentColor = IndigoDeep),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) { Text(text, fontWeight = FontWeight.Medium, fontSize = 15.sp) }
}

/** White rounded card with subtle border. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Lavender100),
    ) { Box(Modifier.padding(14.dp)) { content() } }
}

/** Small status pill: pass background + text color. */
@Composable
fun StatusPill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Surface(color = bg, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Text(
            text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** Circular icon control with caption (used in live-view control bar). */
@Composable
fun RoundControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Int = 44,
    bg: Color = Color(0x1FFFFFFF),
    fg: Color = Color.White,
) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = bg,
            shape = CircleShape,
            modifier = Modifier.size(size.dp),
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size((size * 0.45).dp))
            }
        }
        Text(label, color = fg, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
