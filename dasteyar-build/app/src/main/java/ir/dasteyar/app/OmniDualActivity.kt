package ir.dasteyar.app

import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.absoluteValue

class OmniDualActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DualTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    DualApp()
                }
            }
        }
    }
}

private data class Accent(val start: Color, val end: Color, val soft: Color)

private val accents = listOf(
    Accent(Color(0xFF6757E5), Color(0xFF8A6CF6), Color(0xFFF0EDFF)),
    Accent(Color(0xFF00A67E), Color(0xFF43C99B), Color(0xFFE9FAF5)),
    Accent(Color(0xFFE94B7B), Color(0xFFFF7A66), Color(0xFFFFEEF3)),
    Accent(Color(0xFF1976D2), Color(0xFF28B9EE), Color(0xFFEAF7FF)),
    Accent(Color(0xFFF49B25), Color(0xFFFFC14D), Color(0xFFFFF6E4)),
    Accent(Color(0xFF7751B8), Color(0xFFC15CC8), Color(0xFFF8ECFA))
)

private fun accentFor(id: String): Accent = accents[id.hashCode().absoluteValue % accents.size]
private val dualIconCache = object : LruCache<String, ImageBitmap>(128) {}

@Composable
private fun DualApp(vm: AppsViewModel = viewModel()) {
    var addCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<AppCategory?>(null) }
    var deletingCategory by remember { mutableStateOf<AppCategory?>(null) }
    var pickingCategory by remember { mutableStateOf<AppCategory?>(null) }
    var editingApp by remember { mutableStateOf<AppEditTarget?>(null) }

    LaunchedEffect(Unit) { vm.refreshApps() }

    val dark = isSystemInDarkTheme()
    val bg = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF111326), Color(0xFF090B15)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF1F0FF), Color(0xFFF8F9FD), Color.White))
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                DualHeader(
                    categoryCount = vm.categories.size,
                    onAddCategory = { addCategoryDialog = true }
                )
            }
        ) { padding ->
            if (vm.categories.isEmpty()) {
                EmptyDual(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onCreate = { addCategoryDialog = true }
                )
            } else {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    val narrow = maxWidth < 380.dp
                    val side = if (narrow) 7.dp else 9.dp
                    val gap = if (narrow) 6.dp else 8.dp
                    val columns = if (maxWidth >= 700.dp) 3 else 2
                    val cardWidth = (maxWidth - side * 2 - gap * (columns - 1).toFloat()) / columns.toFloat()
                    val cardHeight = maxHeight - 14.dp

                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = side, end = side, top = 7.dp, bottom = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.Top
                    ) {
                        items(vm.categories, key = { it.id }) { category ->
                            CategoryCard(
                                modifier = Modifier.width(cardWidth).height(cardHeight),
                                category = category,
                                vm = vm,
                                onMoveCategory = { dx -> moveCategory(vm, category.id, dx) },
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
    }

    if (addCategoryDialog) {
        CategoryDialogDual("دسته جدید", "", { addCategoryDialog = false }) {
            vm.addCategory(it)
            addCategoryDialog = false
        }
    }

    editingCategory?.let { category ->
        CategoryDialogDual("ویرایش دسته", category.title, { editingCategory = null }) {
            vm.renameCategory(category.id, it)
            editingCategory = null
        }
    }

    deletingCategory?.let { category ->
        DeleteDialogDual(
            title = "حذف «${category.title}»؟",
            body = "فقط این دسته حذف می‌شود و برنامه‌های اصلی گوشی دست‌نخورده می‌مانند.",
            onDismiss = { deletingCategory = null },
            onConfirm = {
                vm.deleteCategory(category.id)
                deletingCategory = null
            }
        )
    }

    pickingCategory?.let { category ->
        AppPickerDual(category, vm.installedApps, { pickingCategory = null }) {
            vm.setApps(category.id, it)
            pickingCategory = null
        }
    }

    editingApp?.let { target ->
        AppNameDialogDual(
            original = vm.label(target.packageName),
            current = vm.displayName(target.packageName),
            onDismiss = { editingApp = null }
        ) {
            vm.renameApp(target.packageName, it)
            editingApp = null
        }
    }
}

@Composable
private fun DualHeader(categoryCount: Int, onAddCategory: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val now = remember(nowMillis / 1000) { LocalDateTime.now() }
    val dateText = remember(now.toLocalDate()) { persianDate(now) }
    val timeText = remember(now.hour, now.minute) {
        faDigits(String.format("%02d:%02d", now.hour, now.minute))
    }
    val brand = BuildConfig.BRAND_NAME

    Surface(
        color = if (dark) Color(0xF0181A2C) else Color(0xFAFFFFFF),
        shadowElevation = 4.dp,
        tonalElevation = 1.dp
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding()) {
            val compact = maxWidth < 390.dp
            val tiny = maxWidth < 345.dp
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = if (compact) 9.dp else 13.dp,
                    vertical = if (compact) 7.dp else 9.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.omnibox_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (compact) 42.dp else 47.dp)
                        .clip(RoundedCornerShape(if (compact) 12.dp else 14.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            brand,
                            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!tiny) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    faDigits(categoryCount.toString()) + " دسته",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Text(
                        "$dateText  •  $timeText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(5.dp))
                Surface(
                    onClick = onAddCategory,
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (tiny) 9.dp else 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, "دسته جدید", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        if (!tiny) {
                            Spacer(Modifier.width(3.dp))
                            Text("دسته", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDual(modifier: Modifier, onCreate: () -> Unit) {
    Box(modifier.padding(20.dp), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .97f)),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.omnibox_icon), null, Modifier.size(76.dp).clip(RoundedCornerShape(21.dp)))
                Spacer(Modifier.height(12.dp))
                Text("اولین دسته را بساز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text("برنامه‌ها را در ستون‌های مرتب بچین و هر زمان خواستی دستی جابه‌جا کن.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onCreate, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("ساخت دسته")
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    modifier: Modifier,
    category: AppCategory,
    vm: AppsViewModel,
    onMoveCategory: (Float) -> Unit,
    onRenameCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onPickApps: () -> Unit,
    onEditApp: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = remember(category.id) { accentFor(category.id) }
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(20.dp)

    Card(
        modifier = modifier.shadow(4.dp, shape, clip = false).border(
            1.dp,
            if (dark) Color.White.copy(alpha = .06f) else Color.White.copy(alpha = .95f),
            shape
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (dark) MaterialTheme.colorScheme.surface else Color.White)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(accent.start, accent.end))).padding(start = 7.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DragDots(
                    color = Color.White.copy(alpha = .92f),
                    modifier = Modifier.pointerInput(category.id) {
                        var sum = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = { sum = 0f },
                            onDragEnd = { sum = 0f },
                            onDragCancel = { sum = 0f }
                        ) { change, drag ->
                            change.consume()
                            sum += drag.x
                            if (kotlin.math.abs(sum) > 42f) {
                                onMoveCategory(sum)
                                sum = 0f
                            }
                        }
                    }
                )
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(30.dp).background(Color.White.copy(alpha = .18f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text(category.title.trim().firstOrNull()?.toString() ?: "•", color = Color.White, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(faDigits(category.packages.size.toString()) + " برنامه", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall)
                }
                Box {
                    IconButton({ menuOpen = true }, Modifier.size(29.dp)) {
                        Icon(Icons.Rounded.MoreVert, "گزینه‌ها", Modifier.size(18.dp), tint = Color.White)
                    }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem({ Text("ویرایش نام دسته") }, onClick = { menuOpen = false; onRenameCategory() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                        DropdownMenuItem({ Text("حذف دسته") }, onClick = { menuOpen = false; onDeleteCategory() }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
                    }
                }
            }

            if (category.packages.isEmpty()) {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(42.dp).background(accent.soft, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Apps, null, Modifier.size(21.dp), tint = accent.start)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text("هنوز خالیه", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(category.packages, key = { it }) { pkg ->
                        AppRowDual(
                            packageName = pkg,
                            displayName = vm.displayName(pkg),
                            accent = accent,
                            onLaunch = { vm.launch(pkg) },
                            onMove = { dy -> moveApp(vm, category.id, pkg, dy) },
                            onRename = { onEditApp(pkg) },
                            onRemove = { vm.removeApp(category.id, pkg) }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onPickApps,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp).height(38.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, accent.start.copy(alpha = .32f)),
                contentPadding = PaddingValues(horizontal = 5.dp)
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = accent.start)
                Spacer(Modifier.width(4.dp))
                Text("افزودن", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent.start)
            }
        }
    }
}

@Composable
private fun AppRowDual(
    packageName: String,
    displayName: String,
    accent: Accent,
    onLaunch: () -> Unit,
    onMove: (Float) -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLaunch),
        shape = RoundedCornerShape(13.dp),
        color = if (dark) Color.White.copy(alpha = .055f) else accent.soft.copy(alpha = .56f)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 1.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            DragDots(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                modifier = Modifier.pointerInput(packageName) {
                    var sum = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { sum = 0f },
                        onDragEnd = { sum = 0f },
                        onDragCancel = { sum = 0f }
                    ) { change, drag ->
                        change.consume()
                        sum += drag.y
                        if (kotlin.math.abs(sum) > 34f) {
                            onMove(sum)
                            sum = 0f
                        }
                    }
                }
            )
            Spacer(Modifier.width(3.dp))
            AppIconDual(packageName, Modifier.size(34.dp))
            Spacer(Modifier.width(5.dp))
            Text(displayName, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box {
                IconButton({ menuOpen = true }, Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.MoreVert, "ویرایش", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem({ Text("تغییر نام نمایشی") }, onClick = { menuOpen = false; onRename() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                    DropdownMenuItem({ Text("حذف از این دسته") }, onClick = { menuOpen = false; onRemove() }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
                }
            }
        }
    }
}

@Composable
private fun DragDots(modifier: Modifier = Modifier, color: Color) {
    Column(modifier.padding(horizontal = 3.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.size(3.dp).background(color, CircleShape))
                Box(Modifier.size(3.dp).background(color, CircleShape))
            }
        }
    }
}

private fun moveCategory(vm: AppsViewModel, id: String, dx: Float) {
    val from = vm.categories.indexOfFirst { it.id == id }
    if (from < 0) return
    val delta = if (dx < 0) 1 else -1
    val to = (from + delta).coerceIn(0, vm.categories.lastIndex)
    if (to == from) return
    val item = vm.categories.removeAt(from)
    vm.categories.add(to, item)
    vm.categories.firstOrNull()?.let { vm.renameCategory(it.id, it.title) }
}

private fun moveApp(vm: AppsViewModel, categoryId: String, pkg: String, dy: Float) {
    val category = vm.categories.firstOrNull { it.id == categoryId } ?: return
    val list = category.packages.toMutableList()
    val from = list.indexOf(pkg)
    if (from < 0) return
    val to = (from + if (dy > 0) 1 else -1).coerceIn(0, list.lastIndex)
    if (to == from) return
    val item = list.removeAt(from)
    list.add(to, item)
    vm.setApps(categoryId, list)
}

@Composable
private fun AppIconDual(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cached = remember(packageName) { dualIconCache.get(packageName) }
    val bitmap by produceState<ImageBitmap?>(cached, packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName).toBitmap(112, 112).asImageBitmap().also { dualIconCache.put(packageName, it) }
                }.getOrNull()
            }
        }
    }
    if (bitmap != null) Image(bitmap!!, null, modifier, contentScale = ContentScale.Fit)
    else Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Apps, null, Modifier.size(17.dp))
    }
}

@Composable
private fun CategoryDialogDual(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = { OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text("نام دسته") }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
        confirmButton = { Button(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("ذخیره") } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun AppNameDialogDual(original: String, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("نام نمایشی برنامه", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("این تغییر فقط داخل همین نرم‌افزار دیده می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text("نام دلخواه") }, supportingText = { Text("نام اصلی: $original") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = { Button(onClick = { onSave(value) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun DeleteDialogDual(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(24.dp), title = { Text(title, fontWeight = FontWeight.Black) }, text = { Text(body) }, confirmButton = { Button(onConfirm) { Text("حذف") } }, dismissButton = { TextButton(onDismiss) { Text("انصراف") } })
}

@Composable
private fun AppPickerDual(category: AppCategory, apps: List<LaunchableApp>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    val selected = remember(category.id, category.packages) { mutableStateListOf<String>().apply { addAll(category.packages) } }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("برنامه‌های ${category.title}", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("جست‌وجوی برنامه") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.fillMaxWidth().height(370.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(
                            Modifier.fillMaxWidth().clickable { if (checked) selected.remove(app.packageName) else selected.add(app.packageName) }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconDual(app.packageName, Modifier.size(38.dp)); Spacer(Modifier.width(8.dp))
                            Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Checkbox(checked, { if (checked) selected.remove(app.packageName) else selected.add(app.packageName) })
                        }
                    }
                }
            }
        },
        confirmButton = { Button({ onSave(selected.toList()) }) { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("ثبت ${faDigits(selected.size.toString())} برنامه") } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } }
    )
}

private fun persianDate(now: LocalDateTime): String {
    val j = gregorianToJalali(now.year, now.monthValue, now.dayOfMonth)
    val months = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val weekdays = arrayOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")
    return "${weekdays[now.dayOfWeek.value - 1]} ${faDigits(j[2].toString())} ${months[j[1] - 1]} ${faDigits(j[0].toString())}"
}

private fun gregorianToJalali(gyInput: Int, gm: Int, gd: Int): IntArray {
    val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    var gy = gyInput
    var jy: Int
    if (gy > 1600) { jy = 979; gy -= 1600 } else { jy = 0; gy -= 621 }
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd + gdm[gm - 1]
    jy += 33 * (days / 12053); days %= 12053
    jy += 4 * (days / 1461); days %= 1461
    if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
    val jm: Int
    val jd: Int
    if (days < 186) { jm = 1 + days / 31; jd = 1 + days % 31 }
    else { jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30 }
    return intArrayOf(jy, jm, jd)
}

private fun faDigits(value: String): String {
    val en = "0123456789"
    val fa = "۰۱۲۳۴۵۶۷۸۹"
    return buildString { value.forEach { ch -> append(if (ch in en) fa[en.indexOf(ch)] else ch) } }
}

private fun dualTypography(): Typography {
    val provider = GoogleFont.Provider("com.google.android.gms.fonts", "com.google.android.gms", R.array.com_google_android_gms_fonts_certs)
    val vazir = GoogleFont("Vazirmatn")
    val family = FontFamily(
        Font(vazir, provider, FontWeight.Normal),
        Font(vazir, provider, FontWeight.Medium),
        Font(vazir, provider, FontWeight.SemiBold),
        Font(vazir, provider, FontWeight.Bold),
        Font(vazir, provider, FontWeight.Black)
    )
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = family), displayMedium = b.displayMedium.copy(fontFamily = family), displaySmall = b.displaySmall.copy(fontFamily = family),
        headlineLarge = b.headlineLarge.copy(fontFamily = family), headlineMedium = b.headlineMedium.copy(fontFamily = family), headlineSmall = b.headlineSmall.copy(fontFamily = family),
        titleLarge = b.titleLarge.copy(fontFamily = family), titleMedium = b.titleMedium.copy(fontFamily = family), titleSmall = b.titleSmall.copy(fontFamily = family),
        bodyLarge = b.bodyLarge.copy(fontFamily = family), bodyMedium = b.bodyMedium.copy(fontFamily = family), bodySmall = b.bodySmall.copy(fontFamily = family),
        labelLarge = b.labelLarge.copy(fontFamily = family), labelMedium = b.labelMedium.copy(fontFamily = family), labelSmall = b.labelSmall.copy(fontFamily = family)
    )
}

@Composable
private fun DualTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF9E8CFF), onPrimary = Color(0xFF17102F), primaryContainer = Color(0xFF322568), onPrimaryContainer = Color(0xFFE9E3FF),
        secondary = Color(0xFF58D4F2), secondaryContainer = Color(0xFF173B49), tertiary = Color(0xFFFF8C73), background = Color(0xFF0C0E18), surface = Color(0xFF171927), surfaceVariant = Color(0xFF242738), outline = Color(0xFF8E90A2)
    ) else lightColorScheme(
        primary = Color(0xFF6657E5), onPrimary = Color.White, primaryContainer = Color(0xFFEAE6FF), onPrimaryContainer = Color(0xFF251B63),
        secondary = Color(0xFF1976D2), secondaryContainer = Color(0xFFE5F2FF), tertiary = Color(0xFFE94B7B), background = Color(0xFFF7F8FC), surface = Color.White, surfaceVariant = Color(0xFFF1F2F8), outline = Color(0xFF7A7D8D)
    )
    MaterialTheme(colorScheme = colors, typography = remember { dualTypography() }, content = content)
}
