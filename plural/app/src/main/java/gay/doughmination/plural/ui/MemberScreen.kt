@file:OptIn(ExperimentalLayoutApi::class)

package gay.doughmination.plural.ui

import gay.doughmination.design.*
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gay.doughmination.plural.BuildConfig
import gay.doughmination.plural.data.Member
import gay.doughmination.plural.data.crushesOf
import gay.doughmination.plural.data.memberColour
import gay.doughmination.plural.data.partnersOf
import gay.doughmination.plural.data.readableOnDark
import java.net.URLEncoder

/** One member's page, as the website's /[member_id]. */
@Composable
fun MemberScreen(vm: SystemViewModel, id: String) {
    val member = vm.member(id)
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        BackBar(member?.shownName ?: "Member", onBack = vm::back) {
            if (member != null) {
                IconButton(onClick = {
                    val url = "${BuildConfig.SITE_URL}/${URLEncoder.encode(member.key, "UTF-8").replace("+", "%20")}"
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                    context.startActivity(Intent.createChooser(send, null))
                }) { Icon(Icons.Outlined.Share, contentDescription = "Share") }
            }
        }
        if (member == null) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Panel {
                    Text("ERROR 404", style = Eyebrow)
                    Text("Member not found", style = MaterialTheme.typography.headlineSmall)
                    Text("This member could not be found in the Doughmination System.", color = Muted)
                }
            }
            return
        }
        MemberDetails(vm, member)
    }
}

@Composable
private fun MemberDetails(vm: SystemViewModel, member: Member) {
    val colour = memberColour(member.color) ?: Accent
    val nameColour = readableOnDark(member.color, TextColour)
    val partners = remember(member, vm.relationships, vm.members) { partnersOf(member, vm.relationships, vm.members) }
    val crushes = remember(member, vm.relationships, vm.members) { crushesOf(member, vm.relationships, vm.members) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Panel(Modifier) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(member.avatarUrl, 132.dp, ring = colour)
                Text(member.shownName, color = nameColour, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                member.pronouns?.ifBlank { null }?.let { Text(it, color = Muted) }
                if (vm.isFronting(member)) Badge("fronting now")
                member.status?.let { StatusTag(it) }
            }

            member.description?.ifBlank { null }?.let { about ->
                Gap(4)
                SectionTitle("About", nameColour)
                SelectionContainer { Text(about, style = MaterialTheme.typography.bodyLarge) }
            }

            if (member.tags.isNotEmpty()) {
                Gap(4)
                SectionTitle("Tags", nameColour)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    member.tags.forEach { tag -> Chip(tag, tint = colour) { vm.filterBy(tag = tag) } }
                }
            }

            if (member.pride.isNotEmpty()) {
                Gap(4)
                SectionTitle("Pride", nameColour)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    member.pride.forEach { identity ->
                        Chip(identity, leading = { PrideSwatch(identity) }) { vm.filterBy(identity = identity) }
                    }
                }
            }

            if (partners.isNotEmpty()) {
                Gap(4)
                SectionTitle("Partners", nameColour)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    partners.forEach { partner -> PersonChip(partner) { vm.open(Screen.MemberPage(partner.id)) } }
                }
            }

            if (crushes.isNotEmpty()) {
                Gap(4)
                SectionTitle("Crushes", nameColour)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    crushes.forEach { crush ->
                        PersonChip(crush.member, badge = if (crush.mutual) "mutual" else null) {
                            vm.open(Screen.MemberPage(crush.member.id))
                        }
                    }
                }
            }
        }
    }
}

/** Another member as a chip with their avatar, like the website's partner chips. */
@Composable
fun PersonChip(member: Member, badge: String? = null, onClick: () -> Unit) {
    Chip(
        if (badge != null) "${member.shownName} · $badge" else member.shownName,
        leading = { Avatar(member.avatarUrl, 22.dp) },
        tint = memberColour(member.color),
        onClick = onClick,
    )
}
