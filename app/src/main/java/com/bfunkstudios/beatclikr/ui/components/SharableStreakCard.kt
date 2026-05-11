package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bfunkstudios.beatclikr.R

@Composable
fun SharableStreakCard(
    streakDays: String,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.secondary
    val primaryColor = MaterialTheme.colorScheme.primary
    val gradient = Brush.verticalGradient(listOf(Color.Black, primaryColor, Color.Black))

    Box(
        modifier = modifier
            .size(360.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Flame icon with glow
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(48.dp)
                        .blur(8.dp)
                )
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Streak number
            Text(
                text = streakDays,
                fontSize = 105.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )

            // "day streak"
            Text(
                text = "day streak",
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )

            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 80.dp),
                color = Color.White.copy(alpha = 0.2f)
            )

            // App branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_share_card_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Text(
                    text = "BeatClikr",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
