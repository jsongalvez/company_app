package com.companyb.companyapp.proto.cashdrawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.companyb.companyapp.util.logInfo

// #785 — cash-drawer hero: denomination straps, expected vs counted, variance
// flags, and the seal-and-hand-off to remittance.

@Composable
fun CdCountScreen(repo: CashDrawerRepo, user: CdUser, branch: CdBranch) {
    var receipt by remember { mutableStateOf<String?>(null) }
    CdTitle("Drawer count — ${branch.name}")
    CdNote(
        "Count each strap on the mat. Expected comes from today's SESSION takings. " +
            "A flag fires per strap and on the drawer total.",
    )
    Spacer(Modifier.height(8.dp))
    CdSheet {
        CdSection("straps · tap +/− per bundle", ink = true)
        CdRule(ink = true)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("", modifier = Modifier.weight(1.4f))
            CdHeadCell("EXPECTED")
            CdHeadCell("COUNTED")
            CdHeadCell("VARIANCE")
            Spacer(Modifier.width(96.dp))
        }
        repo.countLines.forEach { line ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.4f)) {
                    Text(line.denom.label, fontWeight = FontWeight.Black, fontSize = 14.sp, color = CdColors.Ink)
                    Text(
                        "exp ${line.expectedQty} × ${php(line.denom.centavos)} = ${php(line.expectedTotal)}",
                        fontSize = 11.sp, color = CdColors.InkSoft, fontFamily = CdFigures,
                    )
                }
                CdFiguresText(php(line.expectedTotal), size = 13, modifierWeight = 1f)
                CdFiguresText("×${line.countedQty}  ${php(line.countedTotal)}", size = 13, modifierWeight = 1f)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    CdFlag(cdFlagFor(line.variance))
                    if (line.variance != 0) {
                        Text(
                            " " + php(line.variance),
                            fontSize = 11.sp, fontFamily = CdFigures,
                            fontWeight = FontWeight.Bold,
                            color = if (line.variance < 0) CdColors.ShortDeep else CdColors.BrassDeep,
                        )
                    }
                }
                CdStepper(
                    dec = {
                        if (line.countedQty > 0) {
                            line.countedQty -= 1
                            repo.log(user.login, "count ${line.denom.code} -> ${line.countedQty}")
                        }
                    },
                    inc = {
                        line.countedQty += 1
                        repo.log(user.login, "count ${line.denom.code} -> ${line.countedQty}")
                    },
                    enabled = !repo.countSealed && branch.dayStatus != CdDayStatus.REMITTED,
                )
            }
            CdRule(ink = true)
        }
        Spacer(Modifier.height(4.dp))
        CdKv("expected drawer", php(repo.expectedTotal))
        CdKv("counted drawer", php(repo.countedTotal))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "DRAWER VARIANCE", fontSize = 11.sp, fontWeight = FontWeight.Black,
                color = CdColors.InkSoft, modifier = Modifier.weight(1f),
            )
            CdFlag(cdFlagFor(repo.varianceTotal))
            Spacer(Modifier.width(8.dp))
            CdFiguresText(
                php(repo.varianceTotal), size = 16,
                color = when {
                    repo.varianceTotal == 0 -> CdColors.OkDeep
                    repo.varianceTotal < 0 -> CdColors.ShortDeep
                    else -> CdColors.BrassDeep
                },
            )
        }
        if (repo.countSealed) {
            CdNote("Drawer sealed — counts locked. Undo the handoff from FINANCE inside 48h.", ink = true)
        }
    }
    Spacer(Modifier.height(10.dp))
    val canSeal = !repo.countSealed && repo.countedLines == repo.countLines.size &&
        branch.dayStatus != CdDayStatus.REMITTED
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CdPaperButton(
            "SEAL → REMITTANCE HANDOFF",
            enabled = canSeal,
            onClick = {
                val id = "r-" + "%02d".format(repo.remittanceCounter++)
                repo.remittances.add(
                    0,
                    CdRemittance(id, "SESSION", "SUBMITTED", repo.countedTotal, branch.name, ageHours = 0, fromCount = true),
                )
                repo.countSealed = true
                receipt = id
                repo.log(user.login, "seal drawer ${branch.name} ${php(repo.countedTotal)} variance ${php(repo.varianceTotal)} -> $id")
                logInfo("CashDrawer", "drawer sealed handoff=$id")
            },
        )
        CdGhostButton("ZERO COUNTED", onClick = {
            repo.countLines.forEach { it.countedQty = 0 }
            repo.log(user.login, "zero counted sheet")
        })
    }
    if (!canSeal && !repo.countSealed && branch.dayStatus != CdDayStatus.REMITTED) {
        CdNote("Count every strap (${repo.countedLines}/${repo.countLines.size}) before sealing — partial sheets cannot hand off.")
    }
    if (branch.dayStatus == CdDayStatus.REMITTED) {
        CdNote("REMITTED day: snapshot frozen, counting locked. Undo inside 48h from FINANCE.")
    }
    receipt?.let { id ->
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().background(CdColors.OkDeep, RoundedCornerShape(4.dp)).padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("HANDOFF $id SEALED", fontWeight = FontWeight.Black, fontSize = 15.sp, color = CdColors.Cream)
                Text(
                    "SESSION snapshot ${php(repo.countedTotal)} for ${branch.name} sits in FINANCE. " +
                        "Commission splits 60/40 practitioner/house on COMPLETED sessions only.",
                    fontSize = 12.sp, color = CdColors.Cream,
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    CdNote("House rule: SHORT past ₱500 needs a MANAGER recount note. OVER always recounts before seal.")
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CdHeadCell(text: String) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Black, color = CdColors.InkSoft,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CdFiguresText(text: String, size: Int, modifierWeight: Float) {
    Box(Modifier.weight(modifierWeight)) {
        CdFiguresText(text, size = size)
    }
}
