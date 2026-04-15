package com.app.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C18)) 
            .padding(top = 64.dp, start = 24.dp, end = 24.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Search",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Slimmer Search input pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 24.dp, vertical = 12.dp), // Reduced vertical padding
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector    = Icons.Default.Search,
                contentDescription = null,
                tint           = Color.White.copy(alpha = 0.35f),
                modifier       = Modifier.size(20.dp)
            )
            BasicTextField(
                value         = query,
                onValueChange = { query = it },
                singleLine    = true,
                textStyle     = TextStyle(
                    color      = Color.White,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                decorationBox = { inner ->
                    if (query.text.isEmpty()) {
                        Text(
                            text       = "City, zip code…",
                            color      = Color.White.copy(alpha = 0.3f),
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    inner()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}