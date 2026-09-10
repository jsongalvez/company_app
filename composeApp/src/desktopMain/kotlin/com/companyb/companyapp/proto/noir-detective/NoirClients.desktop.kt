package com.companyb.companyapp.proto.noirdetective

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun NoirClients(repo: NoirRepo) {
    FolderTab(
        title = "Persons of interest",
        right = repo.persons.size.toString() + " names · global registry, every branch",
    ) {}
    Row(horizontalArrangement = Arrangement.spacedBy(NoirPadMd)) {
        Column(Modifier.weight(1f)) {
            CaseFolder(Modifier.fillMaxWidth()) {
                val pending = repo.pendingPersonIds()
                repo.persons.forEach { person ->
                    FileRow(
                        selected = person.id == repo.selectedPersonId,
                        onClick = { repo.selectedPersonId = person.id },
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(displayName(person), style = NoirType.bodyLarge)
                                if (pending.contains(person.id)) {
                                    NoirBadge("★ PENDING", NoirPalette.LampAmber)
                                }
                                if (person.anonymized) {
                                    NoirBadge("REDACTED", NoirPalette.SirenRed)
                                }
                            }
                            Text(
                                person.id.uppercase() + " · " + person.gender + " · age " + person.age,
                                style = NoirType.bodySmall,
                                color = NoirPalette.Dim,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(NoirPadSm))
            DeskLampNote("At most one PENDING session per client — the ★ marks the live one.")
        }
        Column(Modifier.width(NoirDetailWidth)) {
            CaseFolder(Modifier.fillMaxWidth()) {
                PersonDetail(repo)
            }
        }
    }
}

private fun displayName(person: Person): String =
    if (person.anonymized) "REDACTED ${person.id.uppercase()}" else person.name

@Composable
private fun PersonDetail(repo: NoirRepo) {
    val person = repo.persons.firstOrNull { it.id == repo.selectedPersonId }
    if (person == null) {
        RainyEmpty("no file pulled", "pick a name from the registry")
        return
    }
    Text("FILE " + person.id.uppercase(), style = NoirType.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(displayName(person), style = NoirType.titleMedium)
    Spacer(Modifier.height(4.dp))
    if (person.anonymized) {
        CaseStamp("redacted", NoirPalette.SirenRed)
        Spacer(Modifier.height(4.dp))
        Text(
            "PII nullified on request. Gender and age stay on the books for reporting.",
            style = NoirType.bodyMedium,
            color = NoirPalette.Dim,
        )
        Text("Gender: " + person.gender + " · Age: " + person.age, style = NoirType.bodySmall)
    } else {
        Text("Gender: " + person.gender + " · Age: " + person.age, style = NoirType.bodyMedium)
        Text("Contact: " + person.phone, style = NoirType.bodyMedium)
        Spacer(Modifier.height(NoirPadSm))
        OutlinedButton(onClick = { repo.anonymizePerson(person.id) }) {
            Text("Anonymize — nullify PII", style = NoirType.labelMedium, color = NoirPalette.SirenRed)
        }
    }
    Spacer(Modifier.height(NoirPadSm))
    val history = repo.cases.filter { it.clientId == person.id }
    Text("SESSION HISTORY (" + history.size + ")", style = NoirType.titleSmall)
    history.forEach { file ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(file.time, style = NoirType.bodySmall, color = NoirPalette.NeonBlue)
            Text(file.id.uppercase(), style = NoirType.bodySmall)
            Text(file.status.label, style = NoirType.bodySmall, color = NoirPalette.Dim)
        }
    }
}
