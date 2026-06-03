package com.example.tangible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tangible.R
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ShoppingCart

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: Painter
)

@Composable
fun WelcomeWizardScreen(
    onDismiss: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            title = "Media used to be tangible",
            description = "Digital media is very convenient but you still want to own and display what you love, right?",
            icon = rememberVectorPainter(Icons.Default.Album)
        ),
        OnboardingPage(
            title = "And it can be again!",
            description = "Tangible bridges the gap between the tactility of physical media and the convenience of digital streaming.",
            icon = painterResource(id = R.drawable.animated_images_24px)
        ),
        OnboardingPage(
            title = "Step 1",
            description = "Buy some off the shelf NFC tags,\nthey're pretty cheap!",
            icon = rememberVectorPainter(Icons.Default.Nfc)
        ),
        OnboardingPage(
            title = "Step 2",
            description = "Stick them to your vinyl records, cassette tapes, CDs, DVDs, posters, whatever you'd like!",
            icon = painterResource(id = R.drawable.sticker_add_24px)
        ),
        OnboardingPage(
            title = "Step 3",
            description = "Tap + and search for music or movies.\nThen hold your phone next to a tag to write to it.",
            icon = rememberVectorPainter(Icons.Default.AddCircle)
        ),
        OnboardingPage(
            title = "Enjoy!",
            description = "Now every time you want to play that media you just need to hold the phone (unlocked) next to the tag and the media will open in your favorite streaming service.",
            icon = rememberVectorPainter(Icons.Default.AutoAwesome)
        )
    )
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TangiblePurple)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                OnboardingPageContent(pages[pageIndex])
            }

            // Bottom section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Indicator
                Row(
                    modifier = Modifier.wrapContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(pages.size) { iteration ->
                        val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.3f)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                // Next/Get Started Button
                FloatingActionButton(
                    onClick = {
                        if (pagerState.currentPage < (pages.size - 1)) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onDismiss()
                        }
                    },
                    containerColor = Color.White,
                    contentColor = TangiblePurple,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (pagerState.currentPage < (pages.size - 1)) 
                            Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Done,
                        contentDescription = if (pagerState.currentPage < (pages.size - 1)) "Next" else "Get Started"
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon section - takes top half and aligns icon to bottom
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Icon(
                painter = page.icon,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Text section - takes bottom half and aligns content to top
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }
    }
}
