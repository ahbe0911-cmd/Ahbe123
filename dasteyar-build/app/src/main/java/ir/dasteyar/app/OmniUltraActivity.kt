package ir.dasteyar.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.absoluteValue

class OmniUltraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniUltraTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    OmniUltraApp()
                }
            }
        }
    }
}

private data class UltraAccent(val start: Color, val end: Color, val soft: Color)
private val ultraAccents = listOf(
    UltraAccent(Color(0xFF6657E5), Color(0xFF8A6CF6), Color(0xFFF0EDFF)),
    UltraAccent(Color(0xFF00A67E), Color(0xFF43C99B), Color(0xFFE9FAF5)),
    UltraAccent(Color(0xFFE94B7B), Color(0xFFFF7A66), Color(0xFFFFEEF3)),
    UltraAccent(Color(0xFF1976D2), Color(0xFF28B9EE), Color(0xFFEAF7FF)),
    UltraAccent(Color(0xFFF49B25), Color(0xFFFFC14D), Color(0xFFFFF6E4)),
    UltraAccent(Color(0xFF7751B8), Color(0xFFC15CC8), Color(0xFFF8ECFA))
)
private fun ultraAccentFor(id: String) = ultraAccents[id.hashCode().absoluteValue % ultraAccents.size]
private val ultraIconMemory = object : LruCache<String, ImageBitmap>(220) {}

private fun ultraIconFile(context: Context, pkg: String): File {
    val dir = File(context.noBackupFilesDir, "omnibox_icon_cache_v3")
    if (!dir.exists()) dir.mkdirs()
    val safe = pkg.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return File(dir, "$safe.png")
}

private fun ultraReadOrCreateIcon(context: Context, pkg: String): ImageBitmap? {
    ultraIconMemory.get(pkg)?.let { return it }
    val file = ultraIconFile(context, pkg)
    if (file.isFile && file.length() > 0L) {
        runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()?.let {
            ultraIconMemory.put(pkg, it)
            return it
        }
    }
    return runCatching {
        val bitmap = context.packageManager.getApplicationIcon(pkg).toBitmap(72, 72)
        runCatching {
            file.outputStream().buffered().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 92, out) }
        }
        bitmap.asImageBitmap().also { ultraIconMemory.put(pkg, it) }
    }.getOrNull()
}

class UltraAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("dasteyar", Context.MODE_PRIVATE)
    val categories = mutableStateListOf<AppCategory>()
    val aliases = mutableStateMapOf<String, String>()
    private val labels = mutableStateMapOf<String, String>()

    var installedApps by mutableStateOf<List<LaunchableApp>>(emptyList())
        private set
    var appsLoading by mutableStateOf(false)
        private set
    private var scanRunning = false
    private var fullScanDone = false

    init {
        loadCategories()
        loadLabelCache()
        loadInstalledCache()
    }

    fun warmSelectedMetadataLater() {
        val missing = categories.flatMap { it.packages }.distinct().filter { labels[it].isNullOrBlank() }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            delay(1800)
            val resolved = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                missing.mapNotNull { pkg -> resolveLabel(pm, pkg)?.let { pkg to it } }
            }
            if (resolved.isNotEmpty()) {
                resolved.forEach { (pkg, label) -> labels[pkg] = label }
                saveLabelCache()
            }
        }
    }

    fun ensureInstalledAppsLoaded(force: Boolean = false) {
        if (scanRunning || (fullScanDone && !force)) return
        scanRunning = true
        appsLoading = true
        viewModelScope.launch {
            val cachedNames = installedApps.associate { it.packageName to it.label }
            val quick = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val pm = app.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val found = if (Build.VERSION.SDK_INT >= 33) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
                }
                found.asSequence().mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == app.packageName) return@mapNotNull null
                    val name = labels[pkg] ?: cachedNames[pkg] ?: pkg.substringAfterLast('.')
                    LaunchableApp(name, pkg)
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }.toList()
            }
            installedApps = quick
            saveInstalledCache()
            fullScanDone = true
            appsLoading = false
            scanRunning = false
            resolveMissingPickerLabels(quick)
        }
    }

    private fun resolveMissingPickerLabels(apps: List<LaunchableApp>) {
        val missing = apps.map { it.packageName }.filter { labels[it].isNullOrBlank() }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                missing.mapNotNull { pkg -> resolveLabel(pm, pkg)?.let { pkg to it } }
            }
            if (resolved.isEmpty()) return@launch
            resolved.forEach { (pkg, label) -> labels[pkg] = label }
            val map = resolved.toMap()
            installedApps = installedApps.map { app -> map[app.packageName]?.let { app.copy(label = it) } ?: app }
                .sortedBy { it.label.lowercase() }
            saveLabelCache()
            saveInstalledCache()
        }
    }

    fun addCategory(title: String) {
        val v = title.trim(); if (v.isBlank()) return
        categories.add(AppCategory(title = v)); saveCategories()
    }
    fun renameCategory(id: String, title: String) {
        val v = title.trim(); if (v.isBlank()) return
        updateCategory(id) { it.copy(title = v) }
    }
    fun deleteCategory(id: String) { categories.removeAll { it.id == id }; saveCategories() }
    fun setApps(id: String, packages: List<String>) { updateCategory(id) { it.copy(packages = packages.distinct()) } }
    fun removeApp(id: String, pkg: String) { updateCategory(id) { it.copy(packages = it.packages.filterNot { p -> p == pkg }) } }
    fun renameApp(pkg: String, name: String) {
        val v = name.trim(); if (v.isBlank() || v == originalName(pkg)) aliases.remove(pkg) else aliases[pkg] = v
        saveCategories()
    }
    fun originalName(pkg: String): String = labels[pkg] ?: installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg.substringAfterLast('.')
    fun displayName(pkg: String): String = aliases[pkg]?.takeIf { it.isNotBlank() } ?: originalName(pkg)
    fun launch(pkg: String) {
        val app = getApplication<Application>()
        app.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }
    }
    fun moveCategory(id: String, direction: Int) {
        val from = categories.indexOfFirst { it.id == id }; if (from < 0) return
        val to = (from + direction).coerceIn(0, categories.lastIndex); if (to == from) return
        val item = categories.removeAt(from); categories.add(to, item); saveCategories()
    }
    fun moveApp(categoryId: String, pkg: String, direction: Int) {
        val category = categories.firstOrNull { it.id == categoryId } ?: return
        val list = category.packages.toMutableList(); val from = list.indexOf(pkg); if (from < 0) return
        val to = (from + direction).coerceIn(0, list.lastIndex); if (to == from) return
        val item = list.removeAt(from); list.add(to, item); setApps(categoryId, list)
    }

    private fun updateCategory(id: String, transform: (AppCategory) -> AppCategory) {
        val i = categories.indexOfFirst { it.id == id }; if (i < 0) return
        categories[i] = transform(categories[i]); saveCategories()
    }

    private fun saveCategories() {
        val arr = JSONArray(); categories.forEach { c ->
            val p = JSONArray(); c.packages.forEach { p.put(it) }
            arr.put(JSONObject().put("id", c.id).put("title", c.title).put("packages", p))
        }
        val a = JSONObject(); aliases.forEach { (k, v) -> a.put(k, v) }
        prefs.edit().putString("categories", arr.toString()).putString("aliases", a.toString()).apply()
    }

    private fun loadCategories() {
        prefs.getString("categories", null)?.let { raw -> runCatching {
            val arr = JSONArray(raw); repeat(arr.length()) { i ->
                val o = arr.getJSONObject(i); val p = o.optJSONArray("packages") ?: JSONArray()
                val packages = buildList { repeat(p.length()) { j -> add(p.getString(j)) } }
                categories.add(AppCategory(o.optString("id", UUID.randomUUID().toString()), o.optString("title", "دسته"), packages))
            }
        } }
        prefs.getString("aliases", null)?.let { raw -> runCatching {
            val o = JSONObject(raw); val keys = o.keys(); while (keys.hasNext()) {
                val k = keys.next(); o.optString(k).takeIf { it.isNotBlank() }?.let { aliases[k] = it }
            }
        } }
    }

    private fun loadLabelCache() {
        prefs.getString("labels_v2", null)?.let { raw -> runCatching {
            val o = JSONObject(raw); val keys = o.keys(); while (keys.hasNext()) {
                val k = keys.next(); o.optString(k).takeIf { it.isNotBlank() }?.let { labels[k] = it }
            }
        } }
    }
    private fun saveLabelCache() {
        val o = JSONObject(); labels.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("labels_v2", o.toString()).apply()
    }

    private fun loadInstalledCache() {
        prefs.getString("installed_apps_v1", null)?.let { raw -> runCatching {
            val arr = JSONArray(raw)
            installedApps = buildList {
                repeat(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    val pkg = o.optString("p")
                    if (pkg.isNotBlank()) add(LaunchableApp(o.optString("l", pkg.substringAfterLast('.')), pkg))
                }
            }
        } }
    }
    private fun saveInstalledCache() {
        val arr = JSONArray(); installedApps.forEach { arr.put(JSONObject().put("p", it.packageName).put("l", it.label)) }
        prefs.edit().putString("installed_apps_v1", arr.toString()).apply()
    }

    private fun resolveLabel(pm: PackageManager, pkg: String): String? = runCatching {
        val info: ApplicationInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getApplicationInfo(pkg, 0)
        }
        pm.getApplicationLabel(info).toString().trim().ifBlank { pkg.substringAfterLast('.') }
    }.getOrNull()
}

@Composable
private fun OmniUltraApp(vm: UltraAppsViewModel = viewModel()) {
    var addCategory by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<AppCategory?>(null) }
    var deleteCategory by remember { mutableStateOf<AppCategory?>(null) }
    var pickerCategory by remember { mutableStateOf<AppCategory?>(null) }
    var editApp by remember { mutableStateOf<AppEditTarget?>(null) }

    LaunchedEffect(Unit) { vm.warmSelectedMetadataLater() }

    val dark = isSystemInDarkTheme()
    val bg = if (dark) Brush.verticalGradient(listOf(Color(0xFF111326), Color(0xFF090B15)))
    else Brush.verticalGradient(listOf(Color(0xFFF1F0FF), Color(0xFFF8F9FD), Color.White))

    Box(Modifier.fillMaxSize().background(bg)) {
        Scaffold(containerColor = Color.Transparent, topBar = { UltraHeader(vm.categories.size) { addCategory = true } }) { padding ->
            if (vm.categories.isEmpty()) {
                UltraEmpty(Modifier.fillMaxSize().padding(padding)) { addCategory = true }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                    val narrow = maxWidth < 380.dp
                    val side = if (narrow) 6.dp else 8.dp
                    val gap = if (narrow) 5.dp else 7.dp
                    val columns = if (maxWidth >= 700.dp) 3 else 2
                    val cardWidth = (maxWidth - side * 2 - gap * (columns - 1).toFloat()) / columns.toFloat()
                    val cardHeight = maxHeight - 10.dp
                    LazyRow(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = side, end = side, top = 5.dp, bottom = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.Top
                    ) {
                        items(vm.categories, key = { it.id }) { c ->
                            UltraCategoryCard(
                                Modifier.width(cardWidth).height(cardHeight), c, vm,
                                onRename = { editCategory = c }, onDelete = { deleteCategory = c },
                                onPick = { pickerCategory = c }, onEditApp = { editApp = AppEditTarget(c.id, it) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (addCategory) UltraCategoryDialog("دسته جدید", "", { addCategory = false }) { vm.addCategory(it); addCategory = false }
    editCategory?.let { c -> UltraCategoryDialog("ویرایش دسته", c.title, { editCategory = null }) { vm.renameCategory(c.id, it); editCategory = null } }
    deleteCategory?.let { c -> UltraDeleteDialog("حذف «${c.title}»؟", { deleteCategory = null }) { vm.deleteCategory(c.id); deleteCategory = null } }
    pickerCategory?.let { c -> UltraAppPicker(c, vm, { pickerCategory = null }) { vm.setApps(c.id, it); pickerCategory = null } }
    editApp?.let { t -> UltraAppNameDialog(vm.originalName(t.packageName), vm.displayName(t.packageName), { editApp = null }) { vm.renameApp(t.packageName, it); editApp = null } }
}

@Composable
private fun UltraHeader(count: Int, onAdd: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val nowMillis by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    val now = remember(nowMillis / 1000) { LocalDateTime.now() }
    val date = remember(now.toLocalDate()) { ultraPersianDate(now) }
    val time = remember(now.hour, now.minute) { ultraFaDigits(String.format("%02d:%02d", now.hour, now.minute)) }
    Surface(color = if (dark) Color(0xF0181A2C) else Color(0xFAFFFFFF), shadowElevation = 3.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding()) {
            val compact = maxWidth < 390.dp
            Row(Modifier.fillMaxWidth().padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.omnibox_icon), null, Modifier.size(if (compact) 40.dp else 45.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("OmniBox", style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(6.dp))
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(ultraFaDigits(count.toString()) + " دسته", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("$date  •  $time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(onClick = onAdd, color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(13.dp), shadowElevation = 2.dp) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(3.dp)); Text("دسته", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun UltraEmpty(modifier: Modifier, onCreate: () -> Unit) {
    Box(modifier.padding(20.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.omnibox_icon), null, Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)))
                Spacer(Modifier.height(10.dp)); Text("اولین دسته را بساز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp)); Text("برنامه‌ها را سریع و مرتب کنار هم نگه دار.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp)); Button(onClick = onCreate) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("ساخت دسته") }
            }
        }
    }
}

@Composable
private fun UltraCategoryCard(modifier: Modifier, category: AppCategory, vm: UltraAppsViewModel, onRename: () -> Unit, onDelete: () -> Unit, onPick: () -> Unit, onEditApp: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val accent = remember(category.id) { ultraAccentFor(category.id) }
    val dark = isSystemInDarkTheme(); val shape = RoundedCornerShape(19.dp)
    Card(modifier.shadow(3.dp, shape).clip(shape), shape = shape, colors = CardDefaults.cardColors(containerColor = if (dark) MaterialTheme.colorScheme.surface else Color.White)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(accent.start, accent.end))).padding(start = 6.dp, end = 1.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                UltraDragDots(Color.White.copy(alpha = .92f), Modifier.pointerInput(category.id) {
                    var sum = 0f
                    detectDragGesturesAfterLongPress(onDragStart = { sum = 0f }, onDragEnd = { sum = 0f }, onDragCancel = { sum = 0f }) { change, drag ->
                        change.consume(); sum += drag.x
                        if (kotlin.math.abs(sum) > 38f) { vm.moveCategory(category.id, if (sum < 0) 1 else -1); sum = 0f }
                    }
                })
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(28.dp).background(Color.White.copy(alpha = .18f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Text(category.title.firstOrNull()?.toString() ?: "•", color = Color.White, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ultraFaDigits(category.packages.size.toString()) + " برنامه", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall)
                }
                Box {
                    IconButton({ menu = true }, Modifier.size(28.dp)) { Icon(Icons.Rounded.MoreVert, null, Modifier.size(18.dp), tint = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("ویرایش نام دسته") }, onClick = { menu = false; onRename() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                        DropdownMenuItem(text = { Text("حذف دسته") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
                    }
                }
            }
            if (category.packages.isEmpty()) {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(40.dp).background(accent.soft, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Apps, null, Modifier.size(20.dp), tint = accent.start) }
                    Spacer(Modifier.height(6.dp)); Text("هنوز خالیه", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(category.packages, key = { it }) { pkg ->
                        UltraAppRow(pkg, vm.displayName(pkg), accent, { vm.launch(pkg) }, { dir -> vm.moveApp(category.id, pkg, dir) }, { onEditApp(pkg) }, { vm.removeApp(category.id, pkg) })
                    }
                }
            }
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 6.dp).height(36.dp), shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, accent.start.copy(alpha = .32f)), contentPadding = PaddingValues(horizontal = 5.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = accent.start); Spacer(Modifier.width(3.dp)); Text("افزودن", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent.start)
            }
        }
    }
}

@Composable
private fun UltraAppRow(pkg: String, name: String, accent: UltraAccent, onLaunch: () -> Unit, onMove: (Int) -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }; val dark = isSystemInDarkTheme()
    Surface(Modifier.fillMaxWidth().clickable(onClick = onLaunch), shape = RoundedCornerShape(12.dp), color = if (dark) Color.White.copy(alpha = .05f) else accent.soft.copy(alpha = .55f)) {
        Row(Modifier.fillMaxWidth().padding(start = 3.dp, end = 1.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            UltraDragDots(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f), Modifier.pointerInput(pkg) {
                var sum = 0f
                detectDragGesturesAfterLongPress(onDragStart = { sum = 0f }, onDragEnd = { sum = 0f }, onDragCancel = { sum = 0f }) { change, drag ->
                    change.consume(); sum += drag.y
                    if (kotlin.math.abs(sum) > 30f) { onMove(if (sum > 0) 1 else -1); sum = 0f }
                }
            })
            Spacer(Modifier.width(2.dp)); UltraAppIcon(pkg, Modifier.size(32.dp)); Spacer(Modifier.width(5.dp))
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box {
                IconButton({ menu = true }, Modifier.size(27.dp)) { Icon(Icons.Rounded.MoreVert, null, Modifier.size(16.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("تغییر نام نمایشی") }, onClick = { menu = false; onRename() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                    DropdownMenuItem(text = { Text("حذف از این دسته") }, onClick = { menu = false; onRemove() }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
                }
            }
        }
    }
}

@Composable
private fun UltraDragDots(color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 3.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { Box(Modifier.size(3.dp).background(color, CircleShape)); Box(Modifier.size(3.dp).background(color, CircleShape)) } }
    }
}

@Composable
private fun UltraAppIcon(pkg: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val cached = remember(pkg) { ultraIconMemory.get(pkg) }
    val bitmap by produceState<ImageBitmap?>(cached, pkg) {
        if (value == null) value = withContext(Dispatchers.IO) { ultraReadOrCreateIcon(context, pkg) }
    }
    if (bitmap != null) Image(bitmap!!, null, modifier, contentScale = ContentScale.Fit)
    else Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Apps, null, Modifier.size(16.dp)) }
}

@Composable
private fun UltraAppPicker(category: AppCategory, vm: UltraAppsViewModel, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    val selected = remember(category.id, category.packages) { mutableStateListOf<String>().apply { addAll(category.packages) } }
    LaunchedEffect(Unit) { vm.ensureInstalledAppsLoaded() }
    val filtered = remember(vm.installedApps, query) {
        if (query.isBlank()) vm.installedApps else vm.installedApps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(22.dp), title = { Text("برنامه‌های ${category.title}", fontWeight = FontWeight.Black) }, text = {
        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("جست‌وجوی برنامه") }, singleLine = true, shape = RoundedCornerShape(13.dp))
            Spacer(Modifier.height(6.dp))
            if (vm.appsLoading && vm.installedApps.isEmpty()) {
                Column(Modifier.fillMaxWidth().height(260.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(34.dp)); Spacer(Modifier.height(10.dp)); Text("در حال خواندن برنامه‌ها…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(Modifier.fillMaxWidth().clickable { if (checked) selected.remove(app.packageName) else selected.add(app.packageName) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            UltraAppIcon(app.packageName, Modifier.size(36.dp)); Spacer(Modifier.width(7.dp)); Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Checkbox(checked, { if (checked) selected.remove(app.packageName) else selected.add(app.packageName) })
                        }
                    }
                }
            }
        }
    }, confirmButton = { Button({ onSave(selected.toList()) }) { Icon(Icons.Rounded.Check, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("ثبت ${ultraFaDigits(selected.size.toString())} برنامه") } }, dismissButton = { TextButton(onDismiss) { Text("انصراف") } })
}

@Composable
private fun UltraCategoryDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(22.dp), title = { Text(title, fontWeight = FontWeight.Black) }, text = { OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text("نام دسته") }, singleLine = true, shape = RoundedCornerShape(13.dp)) }, confirmButton = { Button({ onSave(value) }, enabled = value.isNotBlank()) { Text("ذخیره") } }, dismissButton = { TextButton(onDismiss) { Text("انصراف") } })
}

@Composable
private fun UltraAppNameDialog(original: String, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(current) { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(22.dp), title = { Text("نام نمایشی برنامه", fontWeight = FontWeight.Black) }, text = { Column { Text("این تغییر فقط داخل OmniBox دیده می‌شود.", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(7.dp)); OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text("نام دلخواه") }, supportingText = { Text("نام اصلی: $original") }, singleLine = true, shape = RoundedCornerShape(13.dp)) } }, confirmButton = { Button({ onSave(value) }) { Text("ذخیره") } }, dismissButton = { TextButton(onDismiss) { Text("انصراف") } })
}

@Composable
private fun UltraDeleteDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(22.dp), title = { Text(title, fontWeight = FontWeight.Black) }, text = { Text("فقط از OmniBox حذف می‌شود و برنامه اصلی گوشی دست‌نخورده می‌ماند.") }, confirmButton = { Button(onConfirm) { Text("حذف") } }, dismissButton = { TextButton(onDismiss) { Text("انصراف") } })
}

private fun ultraPersianDate(now: LocalDateTime): String {
    val j = ultraGregorianToJalali(now.year, now.monthValue, now.dayOfMonth)
    val months = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val weekdays = arrayOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")
    return "${weekdays[now.dayOfWeek.value - 1]} ${ultraFaDigits(j[2].toString())} ${months[j[1] - 1]} ${ultraFaDigits(j[0].toString())}"
}
private fun ultraGregorianToJalali(gyInput: Int, gm: Int, gd: Int): IntArray {
    val gdm = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334); var gy = gyInput; var jy: Int
    if (gy > 1600) { jy = 979; gy -= 1600 } else { jy = 0; gy -= 621 }
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd + gdm[gm - 1]
    jy += 33 * (days / 12053); days %= 12053; jy += 4 * (days / 1461); days %= 1461
    if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
    val jm: Int; val jd: Int
    if (days < 186) { jm = 1 + days / 31; jd = 1 + days % 31 } else { jm = 7 + (days - 186) / 30; jd = 1 + (days - 186) % 30 }
    return intArrayOf(jy, jm, jd)
}
private fun ultraFaDigits(value: String): String {
    val en = "0123456789"; val fa = "۰۱۲۳۴۵۶۷۸۹"
    return buildString { value.forEach { ch -> append(if (ch in en) fa[en.indexOf(ch)] else ch) } }
}

private fun createUltraVazirTypography(): Typography {
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
        displayLarge = base.displayLarge.copy(fontFamily = family), displayMedium = base.displayMedium.copy(fontFamily = family), displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family), headlineMedium = base.headlineMedium.copy(fontFamily = family), headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family), titleMedium = base.titleMedium.copy(fontFamily = family), titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family), bodyMedium = base.bodyMedium.copy(fontFamily = family), bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family), labelMedium = base.labelMedium.copy(fontFamily = family), labelSmall = base.labelSmall.copy(fontFamily = family)
    )
}

@Composable
private fun OmniUltraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF9E8CFF), onPrimary = Color(0xFF17102F), primaryContainer = Color(0xFF322568), onPrimaryContainer = Color(0xFFE9E3FF),
        secondary = Color(0xFF58D4F2), secondaryContainer = Color(0xFF173B49), tertiary = Color(0xFFFF8C73), background = Color(0xFF0C0E18), surface = Color(0xFF171927), surfaceVariant = Color(0xFF242738), outline = Color(0xFF8E90A2)
    ) else lightColorScheme(
        primary = Color(0xFF6657E5), onPrimary = Color.White, primaryContainer = Color(0xFFEAE6FF), onPrimaryContainer = Color(0xFF251B63),
        secondary = Color(0xFF1976D2), secondaryContainer = Color(0xFFE5F2FF), tertiary = Color(0xFFE94B7B), background = Color(0xFFF7F8FC), surface = Color.White, surfaceVariant = Color(0xFFF1F2F8), outline = Color(0xFF7A7D8D)
    )
    val typography = remember { createUltraVazirTypography() }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
