package de.fa.kuran.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fa.kuran.data.AppState

@Composable
fun WelcomeScreen(st: AppState, onDone: (String) -> Unit) {
    val pal = palette(st.theme)
    var choice by remember { mutableStateOf("elmalili") }
    val details = mapOf(
        "elmalili" to Triple("E", "1878 – 1942", "9 cilt · Geniş, ilmî tefsir"),
        "bilmen" to Triple("B", "1883 – 1971", "8 cilt · Klasik, açık anlatım"),
    )
    BoxWithConstraints(
        Modifier.fillMaxSize().background(pal.app).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= 600.dp
        Column(
            Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(pal.card.copy(alpha = 0.8f))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("القُرْآنُ الكَرِيمُ", style = TextStyle(fontFamily = Fonts.amiri, fontSize = 40.sp, color = pal.band))
            Text("Hangi tefsiri kullanmak istersiniz?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = pal.ink, textAlign = TextAlign.Center)
            Text("Bir âyete dokunduğunuzda seçtiğiniz âlimin tefsiri açılır.", fontSize = 16.sp, color = pal.muted, textAlign = TextAlign.Center)
            val cards: @Composable (TefsirInfo, Modifier) -> Unit = { t, mod ->
                val sel = choice == t.id
                val d = details.getValue(t.id)
                Column(
                    mod
                        .heightIn(min = 240.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(pal.card)
                        .border(2.5.dp, if (sel) pal.accent else pal.chip, RoundedCornerShape(24.dp))
                        .clickable { choice = t.id }
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(Modifier.size(54.dp).clip(CircleShape).background(pal.band), contentAlignment = Alignment.Center) {
                            Text(d.first, color = Color(0xFFF7F0DC), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(if (sel) pal.accent else Color.Transparent)
                                .border(2.dp, if (sel) pal.accent else pal.muted, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                    Text(t.name, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = pal.ink)
                    Text(d.second, fontSize = 14.sp, color = pal.muted)
                    Text(t.work, fontSize = 16.sp, fontStyle = FontStyle.Italic, color = pal.ink)
                    Text(d.third, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal.muted)
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    TEFSIRLER.forEach { cards(it, Modifier.weight(1f)) }
                }
            } else {
                TEFSIRLER.forEach { cards(it, Modifier.fillMaxWidth()) }
            }
            Box(
                Modifier.clip(RoundedCornerShape(27.dp)).background(pal.accent).clickable { onDone(choice) }
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Text("${tefsirInfo(choice).short} tefsiri ile başla", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Text("Bu seçimi daha sonra Ayarlar'dan veya okurken değiştirebilirsiniz.", fontSize = 13.sp, color = pal.muted, textAlign = TextAlign.Center)
        }
    }
}
