package com.example.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SlatyDarkBackground
import com.example.ui.theme.SlatyMint
import com.example.ui.theme.SlatyPrimary
import com.example.ui.theme.SlatyTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern Animated Splash Screen featuring a Kinetic Gyroscopic Orbit & Harmonic Luminous Pulse.
 * Smoothly transitions into the main app flow after entrance animations settle.
 */
@Composable
fun SplashScreen(
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Entrance Animations
    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val badgesAlpha = remember { Animatable(0f) }

    // Infinite Gyroscopic & Pulse Transitions
    val infiniteTransition = rememberInfiniteTransition(label = "splashInfinite")

    // Primary Clockwise Orbit (Degrees)
    val orbitRotationClockwise by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitRotationClockwise"
    )

    // Counter-Clockwise Secondary Orbit
    val orbitRotationCounter by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitRotationCounter"
    )

    // Luminous Breathing Aura Glow
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    // Subtle 3D Floating Bob Motion
    val floatingOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingOffset"
    )

    // Progress Bar Shimmer Beam
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    // Sequence trigger
    LaunchedEffect(Unit) {
        // Step 1: Spring logo pop
        logoAlpha.animateTo(1f, animationSpec = tween(400))
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
        // Step 2: Reveal typography
        textAlpha.animateTo(1f, animationSpec = tween(500, easing = EaseOutQuad))
        // Step 3: Badges reveal
        badgesAlpha.animateTo(1f, animationSpec = tween(400, easing = EaseOutQuad))

        // Brief cinematic pause before transitioning
        delay(1400)
        onAnimationFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlatyDarkBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Allow instant skip on tap
                onAnimationFinished()
            }
            .testTag("splash_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        // Deep Nebula Radial Background Glow
        Box(
            modifier = Modifier
                .size(360.dp)
                .scale(auraScale)
                .alpha(auraAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x665B4294),
                            Color(0x3300E5A3),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Kinetic Gyroscopic Canvas Rings & Orbiting Constellations
        Canvas(modifier = Modifier.size(280.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2.3f

            // Layer 1: Expanding Concentric Energy Ring
            drawCircle(
                color = SlatyPrimary.copy(alpha = 0.15f),
                radius = baseRadius * 1.05f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Layer 2: Primary Outer Mint Gyroscopic Ring with Sweep
            val outerRadius = baseRadius * 0.95f
            drawCircle(
                color = SlatyMint.copy(alpha = 0.22f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Orbiting Mint Satellite Beaded Node
            val radClockwise = Math.toRadians(orbitRotationClockwise.toDouble())
            val satX1 = center.x + outerRadius * cos(radClockwise).toFloat()
            val satY1 = center.y + outerRadius * sin(radClockwise).toFloat()

            // Glow aura behind satellite bead
            drawCircle(
                color = SlatyMint.copy(alpha = 0.4f),
                radius = 7.dp.toPx(),
                center = Offset(satX1, satY1)
            )
            drawCircle(
                color = Color.White,
                radius = 3.5.dp.toPx(),
                center = Offset(satX1, satY1)
            )

            // Layer 3: Secondary Counter-Clockwise Purple Ring
            val innerRadius = baseRadius * 0.76f
            drawCircle(
                color = SlatyPrimary.copy(alpha = 0.35f),
                radius = innerRadius,
                center = center,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // Orbiting Secondary Violet Node
            val radCounter = Math.toRadians(orbitRotationCounter.toDouble())
            val satX2 = center.x + innerRadius * cos(radCounter).toFloat()
            val satY2 = center.y + innerRadius * sin(radCounter).toFloat()

            drawCircle(
                color = SlatyPrimary.copy(alpha = 0.5f),
                radius = 6.dp.toPx(),
                center = Offset(satX2, satY2)
            )
            drawCircle(
                color = SlatyMint,
                radius = 3.dp.toPx(),
                center = Offset(satX2, satY2)
            )

            // Constellation Anchor Points (8 decorative glowing dots)
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45.0) + (orbitRotationClockwise * 0.3))
                val px = center.x + baseRadius * 1.16f * cos(angle).toFloat()
                val py = center.y + baseRadius * 1.16f * sin(angle).toFloat()
                val dotAlpha = if (i % 2 == 0) 0.5f else 0.25f
                drawCircle(
                    color = Color.White.copy(alpha = dotAlpha),
                    radius = 2.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // Central Emblem & Brand Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Slaty Floating Hero Logo
            Box(
                modifier = Modifier
                    .offset(y = floatingOffset.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E1A2F),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        Brush.linearGradient(listOf(SlatyMint, SlatyPrimary))
                    ),
                    shadowElevation = 16.dp,
                    modifier = Modifier.size(118.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.slaty_login_logo),
                            contentDescription = "Slaty",
                            modifier = Modifier
                                .width(82.dp)
                                .height(46.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Animated Typography Reveal
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha.value)
            ) {
                Text(
                    text = "SLATY",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "The Unified Academic Network",
                    color = SlatyMint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Live HD Classes • Verified University Tutors • CBT Practice",
                    color = SlatyTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Feature Badges Row
            Row(
                modifier = Modifier.alpha(badgesAlpha.value),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SplashFeaturePill(text = "✓ Certified Tutors")
                SplashFeaturePill(text = "⚡ Real-time CBT")
                SplashFeaturePill(text = "● Live HD Rooms")
            }
        }

        // Bottom Animated Progress Indicator
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Neon Progress Track with Shimmer Beam
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF231E34))
            ) {
                val shimmerBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        SlatyMint,
                        Color.White,
                        SlatyMint,
                        Color.Transparent
                    ),
                    startX = shimmerTranslate * 160f,
                    endX = (shimmerTranslate + 1f) * 160f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(shimmerBrush)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Preparing secure academic environment...",
                color = SlatyTextSecondary,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun SplashFeaturePill(text: String) {
    Surface(
        color = Color(0xFF1E1A2E),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF332D48))
    ) {
        Text(
            text = text,
            color = Color(0xFFCCCCCC),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
