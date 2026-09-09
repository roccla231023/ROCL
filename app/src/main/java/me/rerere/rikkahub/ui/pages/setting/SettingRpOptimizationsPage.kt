package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.RpStyleRule
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingRpOptimizationsPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var editingRule by remember { mutableStateOf<RpStyleRule?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun updateRules(rules: List<RpStyleRule>) {
        val next = displaySetting.copy(rpStyleRules = rules)
        displaySetting = next
        vm.updateSettings(settings.copy(displaySetting = next))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_display_page_rp_optimizations_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text(stringResource(R.string.rp_optimizations_page_heading)) },
                        supportingContent = { Text(stringResource(R.string.rp_optimizations_page_desc)) },
                    )
                }
            }
            if (displaySetting.rpStyleRules.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.rp_optimizations_page_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(displaySetting.rpStyleRules, key = { it.id }) { rule ->
                    CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                        item(
                            onClick = { editingRule = rule },
                            headlineContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val preview = runCatching {
                                        Color(android.graphics.Color.parseColor(rule.colorHex))
                                    }.getOrDefault(MaterialTheme.colorScheme.onSurface)
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(preview),
                                    )
                                    Text(stringResource(R.string.rp_optimizations_page_example_text, rule.pattern))
                                }
                            },
                            supportingContent = { Text(rule.colorHex) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            updateRules(displaySetting.rpStyleRules.filterNot { it.id == rule.id })
                                        }
                                    ) {
                                        Icon(HugeIcons.Delete01, contentDescription = null)
                                    }
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { enabled ->
                                            updateRules(
                                                displaySetting.rpStyleRules.map {
                                                    if (it.id == rule.id) it.copy(enabled = enabled) else it
                                                }
                                            )
                                        }
                                    )
                                }
                            },
                        )
                    }
                }
                item {
                    MarkdownBlock(
                        content = displaySetting.rpStyleRules
                            .filter { it.enabled }
                            .joinToString("\n\n") { "${it.pattern}preview${it.pattern}" },
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (showAddDialog || editingRule != null) {
        RpStyleRuleDialog(
            rule = editingRule,
            onDismiss = {
                showAddDialog = false
                editingRule = null
            },
            onSave = { saved ->
                val current = displaySetting.rpStyleRules
                updateRules(
                    if (current.any { it.id == saved.id }) {
                        current.map { if (it.id == saved.id) saved else it }
                    } else {
                        current + saved
                    }
                )
                showAddDialog = false
                editingRule = null
            }
        )
    }
}

@Composable
private fun RpStyleRuleDialog(
    rule: RpStyleRule?,
    onDismiss: () -> Unit,
    onSave: (RpStyleRule) -> Unit,
) {
    var pattern by remember { mutableStateOf(rule?.pattern ?: "*") }
    var colorHex by remember { mutableStateOf(rule?.colorHex ?: "#808080") }
    val parsed = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color.Gray)
    var red by remember { mutableIntStateOf((parsed.red * 255).toInt()) }
    var green by remember { mutableIntStateOf((parsed.green * 255).toInt()) }
    var blue by remember { mutableIntStateOf((parsed.blue * 255).toInt()) }

    fun applyRgb() {
        colorHex = String.format("#%02X%02X%02X", red, green, blue)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (rule != null) R.string.rp_optimizations_dialog_edit_rule_title
                    else R.string.rp_optimizations_dialog_add_rule_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(stringResource(R.string.pattern)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(stringResource(R.string.rp_optimizations_pattern_help_text, pattern.ifBlank { "*" }))
                    }
                )
                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { colorHex = it },
                    label = { Text(stringResource(R.string.color_hex)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#808080", "#FFD700", "#87CEEB", "#90EE90", "#FFB6C1", "#FF6B6B").forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    colorHex = hex
                                    val c = Color(android.graphics.Color.parseColor(hex))
                                    red = (c.red * 255).toInt()
                                    green = (c.green * 255).toInt()
                                    blue = (c.blue * 255).toInt()
                                }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R", color = Color.Red, modifier = Modifier.width(20.dp))
                    Slider(
                        value = red.toFloat(),
                        onValueChange = { red = it.toInt(); applyRgb() },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("G", color = Color.Green, modifier = Modifier.width(20.dp))
                    Slider(
                        value = green.toFloat(),
                        onValueChange = { green = it.toInt(); applyRgb() },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("B", color = Color.Blue, modifier = Modifier.width(20.dp))
                    Slider(
                        value = blue.toFloat(),
                        onValueChange = { blue = it.toInt(); applyRgb() },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pattern.isNotBlank()) {
                        onSave(
                            RpStyleRule(
                                id = rule?.id ?: kotlin.uuid.Uuid.random().toString(),
                                pattern = pattern,
                                colorHex = colorHex,
                                enabled = rule?.enabled ?: true,
                            )
                        )
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
