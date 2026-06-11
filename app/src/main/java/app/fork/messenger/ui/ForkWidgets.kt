package app.fork.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/**
 * Аватар Fork: фото или градиентная пара с инициалами; `online` рисует
 * фирменное градиентное кольцо 2dp с зазором 2dp (Fork Design Spec §5).
 */
@Composable
fun ForkAvatar(
    size: Dp,
    avatarPath: String?,
    initials: String,
    seed: Long,
    online: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ring = forkTokens.brandGradient
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                if (online) {
                    drawCircle(brush = ring, style = Stroke(width = 2.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val inner = if (online) size - 8.dp else size
        if (avatarPath != null) {
            AsyncImage(
                model = File(avatarPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(inner).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(inner)
                    .clip(CircleShape)
                    .background(avatarBrush(seed)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = (inner.value * 0.36f).sp,
                    color = Color.White,
                )
            }
        }
    }
}

/** Градиентная кнопка-капсула 56dp (Fork Design Spec §5). */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val tokens = forkTokens
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .then(
                if (enabled && !busy) {
                    Modifier.background(tokens.brandGradient).clickable(onClick = onClick)
                } else if (busy) {
                    Modifier.background(tokens.brandGradient)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color.White
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}

/** Стеклянная капсула: дата-разделители, кнопка «вниз», оверлеи медиа. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(percent = 50),
    content: @Composable () -> Unit,
) {
    val tokens = forkTokens
    Box(
        modifier = modifier
            .clip(shape)
            .background(tokens.glassPill)
            .border(1.dp, tokens.glassBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Пустое состояние с вилкой-логотипом (Fork Design Spec §7.9). */
@Composable
fun ForkEmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    iconSize: Dp = 96.dp,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            ForkIcons.ForkMark,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Счётчик непрочитанных: градиентная капсула 22dp (Fork Design Spec §5). */
@Composable
fun UnreadBadge(count: Int, muted: Boolean) {
    val tokens = forkTokens
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(shape)
            .then(
                if (muted) Modifier.background(MaterialTheme.colorScheme.outlineVariant)
                else Modifier.background(tokens.unreadBadge),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else tokens.unreadBadgeText,
            modifier = Modifier.padding(horizontal = 7.dp),
        )
    }
}
