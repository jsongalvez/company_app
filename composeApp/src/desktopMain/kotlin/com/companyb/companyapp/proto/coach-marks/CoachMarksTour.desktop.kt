package com.companyb.companyapp.proto.coachmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun stepState(store: CoachMarksStore, step: CmTourStep): String =
    when {
        step.id in store.tourDone -> "done"
        step.id in store.tourSkipped -> "skipped"
        store.tourActive.value && store.currentStep()?.id == step.id -> "current"
        else -> "pending"
    }

@Composable
fun TourBanner(store: CoachMarksStore) {
    if (!store.tourActive.value) return
    val step = store.currentStep() ?: return
    val (finished, total) = store.tourProgress()
    val number = store.stepNumber(step.id)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CmAmber.copy(alpha = 0.12f))
            .border(1.dp, CmAmber.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CmBeacon(number)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Guided tour — step $number of $total ($finished finished)",
                color = CmAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(step.title, color = CmText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = {
            if (step.screen != store.currentScreen.value) store.currentScreen.value = step.screen
            store.tourGuideOpen.value = true
        }) {
            Text("Guide", color = CmAmber, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { store.skipCurrent() }) {
            Text("Skip step", color = CmMuted)
        }
        TextButton(onClick = { store.endTour(completed = false) }) {
            Text("End tour", color = CmMuted)
        }
    }
}

@Composable
fun TourGuideDialog(store: CoachMarksStore) {
    if (!store.tourActive.value || !store.tourGuideOpen.value) return
    val step = store.currentStep() ?: return
    val number = store.stepNumber(step.id)
    val total = coachTourSteps.size
    val isLast = number == total
    val isFirst = number == 1
    AlertDialog(
        onDismissRequest = { store.tourGuideOpen.value = false },
        title = {
            Column {
                Text(
                    "Step $number of $total · ${step.screen.label}",
                    color = CmAmberDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(step.title, color = CmAmberInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(step.body, color = CmAmberInk.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CmAmber.copy(alpha = 0.25f))
                        .padding(10.dp),
                ) {
                    Text("Try it: ${step.tryIt}", color = CmAmberInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Skipping is safe — the app stays fully usable and the checklist keeps your place.",
                    color = CmAmberInk.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Row {
                if (!isFirst) {
                    TextButton(onClick = { store.backOne() }) {
                        Text("Back", color = CmAmberDeep)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        if (step.screen != store.currentScreen.value) {
                            store.currentScreen.value = step.screen
                        }
                        store.tourGuideOpen.value = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CmAmber, contentColor = CmAmberInk),
                ) {
                    Text(if (isLast) "Finish" else "Show me", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { store.skipCurrent() }) {
                    Text("Skip step", color = CmAmberDeep)
                }
                TextButton(onClick = { store.endTour(completed = false) }) {
                    Text("End tour", color = CmAmberDeep)
                }
            }
        },
        containerColor = CmBubble,
    )
}

@Composable
fun TourChecklistScreen(store: CoachMarksStore) {
    val (finished, total) = store.tourProgress()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CmCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CmSectionTitle("Guided tour progress")
                    Spacer(Modifier.height(4.dp))
                    CmHint(
                        if (store.tourActive.value) {
                            "Tour is running — $finished of $total steps finished. " +
                                "Do the highlighted moves, or skip freely."
                        } else if (finished >= total) {
                            "Tour complete — replay it any time, or leave it off."
                        } else {
                            "Tour is paused at $finished of $total steps. Resume where you left off, " +
                                "restart from step one, or explore without it."
                        },
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (store.tourActive.value) {
                    OutlinedButton(onClick = { store.endTour(completed = false) }) {
                        Text("End tour")
                    }
                } else {
                    Button(
                        onClick = { store.resumeTour() },
                        colors = ButtonDefaults.buttonColors(containerColor = CmAmber, contentColor = CmAmberInk),
                    ) {
                        Text(if (finished > 0) "Resume tour" else "Start tour", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = store.dontShowAgain.value,
                    onCheckedChange = { store.dontShowAgain.value = it },
                    colors = CheckboxDefaults.colors(checkedColor = CmAmber, checkmarkColor = CmAmberInk),
                )
                Text("Don't auto-start the tour on sign-in", color = CmMuted, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                if (!store.tourActive.value && finished > 0) {
                    TextButton(onClick = { store.restartTour() }) {
                        Text("Restart from step 1", color = CmAmber)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            coachTourSteps.forEach { step ->
                val state = stepState(store, step)
                val number = store.stepNumber(step.id)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CmPanel)
                        .border(
                            1.dp,
                            if (state == "current") CmAmber else CmHairline,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable {
                            store.currentScreen.value = step.screen
                            if (store.tourActive.value) {
                                store.tourIndex.value = number - 1
                                store.tourGuideOpen.value = true
                            }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CmBeacon(number, done = state == "done")
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.title, color = CmText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${step.screen.label} · ${step.tryIt}",
                            color = CmMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (state) {
                            "done" -> "Done"
                            "skipped" -> "Skipped"
                            "current" -> "Now"
                            else -> "Todo"
                        },
                        color = when (state) {
                            "done" -> CmGood
                            "skipped" -> CmMuted
                            "current" -> CmAmber
                            else -> CmMuted
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
