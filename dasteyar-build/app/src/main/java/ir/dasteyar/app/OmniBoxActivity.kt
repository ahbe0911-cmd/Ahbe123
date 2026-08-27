package ir.dasteyar.app

import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

class OmniBoxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniBoxTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    OmniBoxApp()
                }
            }
        }
    }
}

private data class OmniAccent(
    val start: Color,
    val end: Color,
    val soft: Color
)

private val omniAccents = listOf(
    OmniAccent(Color(0xFF6757E5), Color(0xFF8A6CF6), Color(0xFFF0EDFF)),
    OmniAccent(Color(0xFF1976D2), Color(0xFF28B9EE), Color(0xFFEAF7FF)),
    OmniAccent(Color(0xFFE94B7B), Color(0xFFFF7A66), Color(0xFFFFEEF3)),
    OmniAccent(Color(0xFF00A67E), Color(0xFF43C99B), Color(0xFFE9FAF5)),
    OmniAccent(Color(0xFFF49B25), Color(0xFFFFC14D), Color(0xFFFFF6E4)),
    OmniAccent(Color(0xFF7751B8), Color(0xFFC15CC8), Color(0xFFF8ECFA))
)

private fun accentFor(id: String): OmniAccent =
    omniAccents[id.hashCode().absoluteValue % omniAccents.size]

private val iconCache = object : LruCache<String, ImageBitmap>(96) {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OmniBoxApp(vm: AppsViewModel = viewModel()) {
    var addCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<AppCategory?>(null) }
    var deletingCategory by remember { mutableStateOf<AppCategory?>(null) }
    var pickingCategory by remember { mutableStateOf<AppCategory?>(null) }
    var editingApp by remember { mutableStateOf<AppEditTarget?>(null) }

    LaunchedEffect(Unit) { vm.refreshApps() }

    val dark = isSystemInDarkTheme()
    val backgroundBrush = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF111326), Color(0xFF0A0C17)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF2F1FF), Color(0xFFF7F8FC), Color(0xFFFFFFFF)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                OmniBoxHeader(
                    categoryCount = vm.categories.size,
                    onAddCategory = { addCategoryDialog = true }
                )
            }
        ) { padding ->
            if (vm.categories.isEmpty()) {
                OmniEmptyState(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onCreate = { addCategoryDialog = true }
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    items(vm.categories, key = { it.id }) { category ->
                        OmniCategoryCard(
                            modifier = Modifier.width(188.dp),
                            category = category,
                            vm = vm,
                            onRenameCategory = { editingCategory = category },
                            onDeleteCategory = { deletingCategory = category },
                            onPickApps = { pickingCategory = category },
                            onEditApp = { pkg -> editingApp = AppEditTarget(category.id, pkg) }
                        )
                    }
                }
            }
        }
    }

    if (addCategoryDialog) {
        OmniCategoryDialog(
            title = "دسته جدید",
            initial = "",
            onDismiss = { addCategoryDialog = false },
            onSave = {
                vm.addCategory(it)
                addCategoryDialog = false
            }
        )
    }

    editingCategory?.let { category ->
        OmniCategoryDialog(
            title = "ویرایش دسته",
            initial = category.title,
            onDismiss = { editingCategory = null },
            onSave = {
                vm.renameCategory(category.id, it)
                editingCategory = null
            }
        )
    }

    deletingCategory?.let { category ->
        OmniDeleteDialog(
            title = "حذف «${category.title}»؟",
            body = "فقط دسته از OmniBox حذف می‌شود؛ برنامه اصلی گوشی پاک نمی‌شود.",
            onDismiss = { deletingCategory = null },
            onConfirm = {
                vm.deleteCategory(category.id)
                deletingCategory = null
            }
        )
    }

    pickingCategory?.let { category ->
        OmniAppPickerDialog(
            category = category,
            apps = vm.installedApps,
            onDismiss = { pickingCategory = null },
            onSave = {
                vm.setApps(category.id, it)
                pickingCategory = null
            }
        )
    }

    editingApp?.let { target ->
        OmniAppNameDialog(
            originalName = vm.label(target.packageName),
            currentName = vm.displayName(target.packageName),
            onDismiss = { editingApp = null },
            onSave = {
                vm.renameApp(target.packageName, it)
                editingApp = null
            }
        )
    }
}

@Composable
private fun OmniBoxHeader(categoryCount: Int, onAddCategory: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val glass = if (dark) Color(0xE6181A2C) else Color(0xEFFFFFFF)

    Surface(
        color = glass,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.omnibox_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "OmniBox",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (categoryCount == 0) "همه‌چیز، سر جای خودش" else "$categoryCount دسته · همه‌چیز دم‌دست",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                onClick = onAddCategory,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "دسته جدید",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "دسته",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun OmniEmptyState(modifier: Modifier, onCreate: () -> Unit) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.omnibox_icon),
                    contentDescription = null,
                    modifier = Modifier.size(86.dp).clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(17.dp))
                Text("اولین Box را بساز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(7.dp))
                Text(
                    "مثلاً کتابخانه، کافی‌نت، کار یا شخصی؛ بعد برنامه‌ها را خیلی سریع داخلش بچین.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onCreate, shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ساخت دسته", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OmniCategoryCard(
    modifier: Modifier,
    category: AppCategory,
    vm: AppsViewModel,
    onRenameCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onPickApps: () -> Unit,
    onEditApp: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = remember(category.id) { accentFor(category.id) }
    val dark = isSystemInDarkTheme()
    val cardColor = if (dark) MaterialTheme.colorScheme.surface.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.97f)

    Card(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(24.dp), clip = false)
            .border(
                width = 1.dp,
                color = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.9f),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(accent.start, accent.end)))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 13.dp, end = 5.dp, top = 12.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category.title.trim().firstOrNull()?.toString() ?: "•",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = category.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${category.packages.size} برنامه",
                            color = Color.White.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "گزینه‌های دسته", tint = Color.White)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("ویرایش نام دسته") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onRenameCategory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("حذف دسته") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onDeleteCategory()
                                }
                            )
                        }
                    }
                }
            }

            if (category.packages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).background(accent.soft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, tint = accent.start, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "هنوز خالیه",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(category.packages, key = { it }) { pkg ->
                        OmniAppRow(
                            packageName = pkg,
                            displayName = vm.displayName(pkg),
                            accent = accent,
                            onLaunch = { vm.launch(pkg) },
                            onRename = { onEditApp(pkg) },
                            onRemove = { vm.removeApp(category.id, pkg) }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onPickApps,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).height(40.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.start.copy(alpha = 0.34f)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(17.dp), tint = accent.start)
                Spacer(Modifier.width(5.dp))
                Text("افزودن برنامه", fontWeight = FontWeight.Bold, color = accent.start)
            }
        }
    }
}

@Composable
private fun OmniAppRow(
    packageName: String,
    displayName: String,
    accent: OmniAccent,
    onLaunch: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val rowColor = if (dark) Color.White.copy(alpha = 0.055f) else accent.soft.copy(alpha = 0.58f)

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLaunch),
        shape = RoundedCornerShape(16.dp),
        color = rowColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OmniAppIcon(packageName, Modifier.size(40.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "ویرایش برنامه",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("تغییر نام داخل OmniBox") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("حذف از این دسته") },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OmniAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cached = remember(packageName) { iconCache.get(packageName) }
    val bitmap by produceState<ImageBitmap?>(initialValue = cached, key1 = packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(112, 112)
                        .asImageBitmap()
                        .also { iconCache.put(packageName, it) }
                }.getOrNull()
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Apps, contentDescription = null, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OmniCategoryDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("نام دسته") },
                placeholder = { Text("مثلاً کتابخانه") },
                singleLine = true,
                shape = RoundedCornerShape(15.dp)
            )
        },
        confirmButton = { Button(enabled = value.isNotBlank(), onClick = { onSave(value) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun OmniAppNameDialog(
    originalName: String,
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text("نام نمایشی برنامه", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(
                    "این تغییر فقط داخل OmniBox دیده می‌شود و نام اصلی برنامه دست‌نخورده می‌ماند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("نام دلخواه") },
                    supportingText = { Text("نام اصلی: $originalName") },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp)
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(value) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun OmniDeleteDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onConfirm) { Text("حذف") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun OmniAppPickerDialog(
    category: AppCategory,
    apps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val selected = remember(category.id, category.packages) {
        mutableStateListOf<String>().apply { addAll(category.packages) }
    }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text("برنامه‌های ${category.title}", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("جست‌وجوی برنامه") },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp)
                )
                Spacer(Modifier.height(7.dp))
                LazyColumn(Modifier.fillMaxWidth().height(370.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selected.remove(app.packageName) else selected.add(app.packageName)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OmniAppIcon(app.packageName, Modifier.size(40.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(
                                app.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selected.remove(app.packageName) else selected.add(app.packageName)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected.toList()) }) {
                Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("ثبت ${selected.size} برنامه")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

private fun createOmniTypography(): Typography {
    val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )
    val vazirmatn = GoogleFont("Vazirmatn")
    val family = FontFamily(
        Font(googleFont = vazirmatn, fontProvider = provider, weight = FontWeight.Normal),
        Font(googleFont = vazirmatn, fontProvider = provider, weight = FontWeight.Medium),
        Font(googleFont = vazirmatn, fontProvider = provider, weight = FontWeight.SemiBold),
        Font(googleFont = vazirmatn, fontProvider = provider, weight = FontWeight.Bold),
        Font(googleFont = vazirmatn, fontProvider = provider, weight = FontWeight.Black)
    )
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family)
    )
}

@Composable
private fun OmniBoxTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF9E8CFF),
            onPrimary = Color(0xFF17102F),
            primaryContainer = Color(0xFF322568),
            onPrimaryContainer = Color(0xFFE9E3FF),
            secondary = Color(0xFF58D4F2),
            secondaryContainer = Color(0xFF173B49),
            tertiary = Color(0xFFFF8C73),
            background = Color(0xFF0C0E18),
            surface = Color(0xFF171927),
            surfaceVariant = Color(0xFF242738),
            outline = Color(0xFF8E90A2)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6657E5),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEAE6FF),
            onPrimaryContainer = Color(0xFF251B63),
            secondary = Color(0xFF1976D2),
            secondaryContainer = Color(0xFFE5F2FF),
            tertiary = Color(0xFFE94B7B),
            background = Color(0xFFF7F8FC),
            surface = Color.White,
            surfaceVariant = Color(0xFFF1F2F8),
            outline = Color(0xFF7A7D8D)
        )
    }
    val typography = remember { createOmniTypography() }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
