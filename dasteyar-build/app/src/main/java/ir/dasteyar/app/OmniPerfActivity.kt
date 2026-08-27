package ir.dasteyar.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.Font as ComposeFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.absoluteValue

class OmniPerfActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PerfTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PerfApp()
                }
            }
        }
    }
}

private val perfMemoryIcons = object : LruCache<String, ImageBitmap>(160) {}
private val perfIconGate = Semaphore(2)
private val accents = listOf(
    Color(0xFF6657E5) to Color(0xFF8A6CF6),
    Color(0xFF00A67E) to Color(0xFF43C99B),
    Color(0xFFE94B7B) to Color(0xFFFF7A66),
    Color(0xFF1976D2) to Color(0xFF28B9EE),
    Color(0xFFF49B25) to Color(0xFFFFC14D),
    Color(0xFF7751B8) to Color(0xFFC15CC8)
)

private fun iconFile(context: Context, pkg: String): File {
    val dir = File(context.noBackupFilesDir, "omnibox_icon_cache_v4").apply { mkdirs() }
    return File(dir, pkg.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png")
}

private fun readCachedIcon(context: Context, pkg: String): ImageBitmap? {
    perfMemoryIcons.get(pkg)?.let { return it }
    val f = iconFile(context, pkg)
    if (!f.isFile || f.length() == 0L) return null
    return runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()?.also {
        perfMemoryIcons.put(pkg, it)
    }
}

private fun createCachedIcon(context: Context, pkg: String): ImageBitmap? = runCatching {
    val bitmap = context.packageManager.getApplicationIcon(pkg).toBitmap(64, 64)
    runCatching { iconFile(context, pkg).outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) } }
    bitmap.asImageBitmap().also { perfMemoryIcons.put(pkg, it) }
}.getOrNull()

private class PerfVm(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("dasteyar", Context.MODE_PRIVATE)
    val categories = mutableStateListOf<AppCategory>()
    val aliases = mutableStateMapOf<String, String>()
    private val labels = mutableStateMapOf<String, String>()
    var installedApps by mutableStateOf<List<LaunchableApp>>(emptyList()); private set
    var scanning by mutableStateOf(false); private set
    private var scanned = false

    init {
        loadState()
        loadLabelCache()
        loadInstalledCache()
    }

    fun displayName(pkg: String) = aliases[pkg]?.takeIf { it.isNotBlank() } ?: labels[pkg] ?: pkg.substringAfterLast('.')
    fun originalName(pkg: String) = labels[pkg] ?: pkg.substringAfterLast('.')

    fun addCategory(title: String) {
        val t = title.trim(); if (t.isBlank()) return
        categories.add(AppCategory(title = t)); saveState()
    }
    fun renameCategory(id: String, title: String) {
        val t = title.trim(); if (t.isBlank()) return
        update(id) { it.copy(title = t) }
    }
    fun deleteCategory(id: String) { categories.removeAll { it.id == id }; saveState() }
    fun setApps(id: String, pkgs: List<String>) = update(id) { it.copy(packages = pkgs.distinct()) }
    fun removeApp(id: String, pkg: String) = update(id) { it.copy(packages = it.packages.filterNot { p -> p == pkg }) }
    fun renameApp(pkg: String, title: String) {
        val t = title.trim(); if (t.isBlank() || t == originalName(pkg)) aliases.remove(pkg) else aliases[pkg] = t
        saveState()
    }

    fun launch(pkg: String) {
        val app = getApplication<Application>()
        app.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(it)
        }
    }

    fun moveCategory(id: String, direction: Int) {
        val from = categories.indexOfFirst { it.id == id }; if (from < 0) return
        val to = (from + direction).coerceIn(0, categories.lastIndex); if (to == from) return
        val value = categories.removeAt(from); categories.add(to, value); saveState()
    }
    fun moveApp(id: String, pkg: String, direction: Int) {
        val c = categories.firstOrNull { it.id == id } ?: return
        val list = c.packages.toMutableList(); val from = list.indexOf(pkg); if (from < 0) return
        val to = (from + direction).coerceIn(0, list.lastIndex); if (to == from) return
        val v = list.removeAt(from); list.add(to, v); setApps(id, list)
    }

    fun ensureApps() {
        if (scanning || scanned) return
        scanning = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val app = getApplication<Application>(); val pm = app.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val found = if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0)) else {
                    @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
                }
                found.asSequence().mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == app.packageName) return@mapNotNull null
                    val label = runCatching { info.loadLabel(pm).toString().trim() }.getOrNull().orEmpty().ifBlank { pkg.substringAfterLast('.') }
                    LaunchableApp(label, pkg)
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }.toList()
            }
            installedApps = result
            result.forEach { labels[it.packageName] = it.label }
            saveLabelCache(); saveInstalledCache(); scanning = false; scanned = true
        }
    }

    private fun update(id: String, block: (AppCategory) -> AppCategory) {
        val i = categories.indexOfFirst { it.id == id }; if (i < 0) return
        categories[i] = block(categories[i]); saveState()
    }

    private fun saveState() {
        val arr = JSONArray(); categories.forEach { c ->
            val p = JSONArray(); c.packages.forEach { p.put(it) }
            arr.put(JSONObject().put("id", c.id).put("title", c.title).put("packages", p))
        }
        val a = JSONObject(); aliases.forEach { (k,v) -> a.put(k,v) }
        prefs.edit().putString("categories", arr.toString()).putString("aliases", a.toString()).apply()
    }
    private fun loadState() {
        prefs.getString("categories", null)?.let { raw -> runCatching {
            val a = JSONArray(raw); repeat(a.length()) { i ->
                val o = a.getJSONObject(i); val p = o.optJSONArray("packages") ?: JSONArray()
                val pkgs = buildList { repeat(p.length()) { j -> add(p.getString(j)) } }
                categories.add(AppCategory(o.optString("id", UUID.randomUUID().toString()), o.optString("title", "دسته"), pkgs))
            }
        } }
        prefs.getString("aliases", null)?.let { raw -> runCatching {
            val o = JSONObject(raw); val it = o.keys(); while (it.hasNext()) { val k = it.next(); aliases[k] = o.optString(k) }
        } }
    }
    private fun loadLabelCache() {
        prefs.getString("labels_v2", null)?.let { raw -> runCatching {
            val o = JSONObject(raw); val it = o.keys(); while (it.hasNext()) { val k = it.next(); o.optString(k).takeIf { it.isNotBlank() }?.let { labels[k] = it } }
        } }
    }
    private fun saveLabelCache() {
        val o = JSONObject(); labels.forEach { (k,v) -> if (v.isNotBlank()) o.put(k,v) }
        prefs.edit().putString("labels_v2", o.toString()).apply()
    }
    private fun loadInstalledCache() {
        prefs.getString("installed_apps_v1", null)?.let { raw -> runCatching {
            val a = JSONArray(raw); installedApps = buildList { repeat(a.length()) { i ->
                val o = a.getJSONObject(i); val p = o.optString("p"); if (p.isNotBlank()) add(LaunchableApp(o.optString("l", p.substringAfterLast('.')), p))
            } }
        } }
    }
    private fun saveInstalledCache() {
        val a = JSONArray(); installedApps.forEach { a.put(JSONObject().put("p", it.packageName).put("l", it.label)) }
        prefs.edit().putString("installed_apps_v1", a.toString()).apply()
    }
}

@Composable
private fun PerfApp(vm: PerfVm = viewModel()) {
    var addCategory by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<AppCategory?>(null) }
    var deleteCategory by remember { mutableStateOf<AppCategory?>(null) }
    var picker by remember { mutableStateOf<AppCategory?>(null) }
    var editApp by remember { mutableStateOf<AppEditTarget?>(null) }

    val dark = isSystemInDarkTheme()
    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF0D0F19) else Color(0xFFF6F7FB))) {
        Scaffold(containerColor = Color.Transparent, topBar = { PerfHeader(vm.categories.size) { addCategory = true } }) { pad ->
            if (vm.categories.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.omnibox_icon), null, Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)))
                        Spacer(Modifier.height(12.dp)); Text("اولین دسته را بساز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(12.dp)); Button({ addCategory = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text("ساخت دسته") }
                    }
                }
            } else BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
                val side = if (maxWidth < 380.dp) 6.dp else 8.dp; val gap = 6.dp; val cols = if (maxWidth >= 700.dp) 3 else 2
                val width = (maxWidth - side * 2 - gap * (cols - 1).toFloat()) / cols.toFloat()
                LazyRow(Modifier.fillMaxSize(), contentPadding = PaddingValues(side, 5.dp, side, 5.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items(vm.categories, key = { it.id }) { c ->
                        CategoryCard(c, vm, Modifier.width(width).fillParentMaxHeight(), { editCategory = c }, { deleteCategory = c }, { picker = c }, { editApp = AppEditTarget(c.id, it) })
                    }
                }
            }
        }
    }

    if (addCategory) NameDialog("دسته جدید", "", { addCategory = false }) { vm.addCategory(it); addCategory = false }
    editCategory?.let { c -> NameDialog("ویرایش دسته", c.title, { editCategory = null }) { vm.renameCategory(c.id, it); editCategory = null } }
    deleteCategory?.let { c -> ConfirmDialog("حذف «${c.title}»؟", { deleteCategory = null }) { vm.deleteCategory(c.id); deleteCategory = null } }
    picker?.let { c -> PickerDialog(c, vm, { picker = null }) { vm.setApps(c.id, it); picker = null } }
    editApp?.let { t -> NameDialog("نام نمایشی برنامه", vm.displayName(t.packageName), { editApp = null }) { vm.renameApp(t.packageName, it); editApp = null } }
}

@Composable
private fun PerfHeader(count: Int, add: () -> Unit) {
    val nowMillis by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(60_000) } }
    val now = remember(nowMillis / 60_000) { LocalDateTime.now() }
    Surface(shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.omnibox_icon), null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) {
                Text("OmniBox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("${persianDate(now)}  •  ${faDigits(String.format("%02d:%02d", now.hour, now.minute))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(onClick = add, color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Add, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(3.dp)); Text("دسته", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CategoryCard(c: AppCategory, vm: PerfVm, modifier: Modifier, rename: () -> Unit, delete: () -> Unit, pick: () -> Unit, editApp: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }; val pair = remember(c.id) { accents[c.id.hashCode().absoluteValue % accents.size] }
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(pair.first, pair.second))).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                DragDots(Color.White, Modifier.pointerInput(c.id) { var dx = 0f; detectDragGesturesAfterLongPress { ch,d -> ch.consume(); dx += d.x; if (kotlin.math.abs(dx) > 38f) { vm.moveCategory(c.id, if (dx < 0) 1 else -1); dx = 0f } } })
                Spacer(Modifier.width(4.dp)); Column(Modifier.weight(1f)) { Text(c.title, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${faDigits(c.packages.size.toString())} برنامه", color = Color.White.copy(.8f), style = MaterialTheme.typography.labelSmall) }
                Box { IconButton({ menu = true }, Modifier.size(28.dp)) { Icon(Icons.Rounded.MoreVert, null, tint = Color.White) }; DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("ویرایش نام دسته") }, { menu = false; rename() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                    DropdownMenuItem({ Text("حذف دسته") }, { menu = false; delete() }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
                } }
            }
            if (c.packages.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("هنوز خالیه", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(c.packages, key = { it }) { pkg -> AppRow(pkg, vm.displayName(pkg), { vm.launch(pkg) }, { vm.moveApp(c.id, pkg, it) }, { editApp(pkg) }, { vm.removeApp(c.id, pkg) }) }
            }
            OutlinedButton(pick, Modifier.fillMaxWidth().padding(5.dp).height(36.dp), shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(4.dp)) { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("افزودن", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AppRow(pkg: String, title: String, launch: () -> Unit, move: (Int) -> Unit, rename: () -> Unit, remove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().clickable(onClick = launch), shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
        Row(Modifier.fillMaxWidth().padding(start = 3.dp, end = 1.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DragDots(MaterialTheme.colorScheme.onSurfaceVariant.copy(.65f), Modifier.pointerInput(pkg) { var dy = 0f; detectDragGesturesAfterLongPress { ch,d -> ch.consume(); dy += d.y; if (kotlin.math.abs(dy) > 30f) { move(if (dy > 0) 1 else -1); dy = 0f } } })
            Spacer(Modifier.width(2.dp)); PerfIcon(pkg, Modifier.size(32.dp)); Spacer(Modifier.width(5.dp)); Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box { IconButton({ menu = true }, Modifier.size(27.dp)) { Icon(Icons.Rounded.MoreVert, null, Modifier.size(16.dp)) }; DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("تغییر نام نمایشی") }, { menu = false; rename() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                DropdownMenuItem({ Text("حذف از این دسته") }, { menu = false; remove() }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
            } }
        }
    }
}

@Composable
private fun PerfIcon(pkg: String, modifier: Modifier) {
    val context = LocalContext.current.applicationContext; val cached = remember(pkg) { perfMemoryIcons.get(pkg) }
    val bitmap by produceState<ImageBitmap?>(cached, pkg) {
        if (value == null) value = withContext(Dispatchers.IO) { perfIconGate.withPermit { readCachedIcon(context, pkg) } }
        if (value == null) { delay(500); value = withContext(Dispatchers.IO) { perfIconGate.withPermit { createCachedIcon(context, pkg) } } }
    }
    if (bitmap != null) Image(bitmap!!, null, modifier, contentScale = ContentScale.Fit) else Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Apps, null, Modifier.size(15.dp)) }
}

@Composable
private fun DragDots(color: Color, modifier: Modifier = Modifier) { Column(modifier.padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(2) { Box(Modifier.size(3.dp).background(color, CircleShape)) } } } } }

@Composable
private fun PickerDialog(c: AppCategory, vm: PerfVm, dismiss: () -> Unit, save: (List<String>) -> Unit) {
    var q by remember { mutableStateOf("") }; val selected = remember(c.id, c.packages) { mutableStateListOf<String>().apply { addAll(c.packages) } }; LaunchedEffect(Unit) { vm.ensureApps() }
    val list = remember(vm.installedApps, q) { if (q.isBlank()) vm.installedApps else vm.installedApps.filter { it.label.contains(q, true) || it.packageName.contains(q, true) } }
    AlertDialog(onDismissRequest = dismiss, title = { Text("برنامه‌های ${c.title}", fontWeight = FontWeight.Black) }, text = { Column { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("جست‌وجوی برنامه") }, singleLine = true); Spacer(Modifier.height(6.dp)); if (vm.scanning && vm.installedApps.isEmpty()) Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } else LazyColumn(Modifier.fillMaxWidth().height(360.dp)) { items(list, key = { it.packageName }) { a -> val checked = a.packageName in selected; Row(Modifier.fillMaxWidth().clickable { if (checked) selected.remove(a.packageName) else selected.add(a.packageName) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { PerfIcon(a.packageName, Modifier.size(34.dp)); Spacer(Modifier.width(7.dp)); Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold); Checkbox(checked, { if (checked) selected.remove(a.packageName) else selected.add(a.packageName) }) } } } } }, confirmButton = { Button({ save(selected.toList()) }, enabled = !vm.scanning) { Text("ثبت ${faDigits(selected.size.toString())} برنامه") } }, dismissButton = { TextButton(dismiss) { Text("انصراف") } })
}

@Composable
private fun NameDialog(title: String, initial: String, dismiss: () -> Unit, save: (String) -> Unit) { var v by remember(initial) { mutableStateOf(initial) }; AlertDialog(onDismissRequest = dismiss, title = { Text(title, fontWeight = FontWeight.Black) }, text = { OutlinedTextField(v, { v = it }, Modifier.fillMaxWidth(), singleLine = true) }, confirmButton = { Button({ save(v) }, enabled = v.isNotBlank()) { Text("ذخیره") } }, dismissButton = { TextButton(dismiss) { Text("انصراف") } }) }
@Composable
private fun ConfirmDialog(title: String, dismiss: () -> Unit, confirm: () -> Unit) { AlertDialog(onDismissRequest = dismiss, title = { Text(title, fontWeight = FontWeight.Black) }, text = { Text("فقط از OmniBox حذف می‌شود و برنامه اصلی گوشی دست‌نخورده می‌ماند.") }, confirmButton = { Button(confirm) { Text("حذف") } }, dismissButton = { TextButton(dismiss) { Text("انصراف") } }) }

private fun persianDate(now: LocalDateTime): String {
    val j = g2j(now.year, now.monthValue, now.dayOfMonth); val m = arrayOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند"); val w = arrayOf("دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه","شنبه","یکشنبه")
    return "${w[now.dayOfWeek.value-1]} ${faDigits(j[2].toString())} ${m[j[1]-1]} ${faDigits(j[0].toString())}"
}
private fun g2j(gy0:Int, gm:Int, gd:Int):IntArray { val d=intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334); var gy=gy0; var jy:Int; if(gy>1600){jy=979;gy-=1600}else{jy=0;gy-=621}; val gy2=if(gm>2)gy+1 else gy; var days=365*gy+(gy2+3)/4-(gy2+99)/100+(gy2+399)/400-80+gd+d[gm-1]; jy+=33*(days/12053);days%=12053;jy+=4*(days/1461);days%=1461;if(days>365){jy+=(days-1)/365;days=(days-1)%365}; val jm:Int;val jd:Int;if(days<186){jm=1+days/31;jd=1+days%31}else{jm=7+(days-186)/30;jd=1+(days-186)%30};return intArrayOf(jy,jm,jd) }
private fun faDigits(s:String):String { val e="0123456789";val f="۰۱۲۳۴۵۶۷۸۹";return buildString{s.forEach{append(if(it in e)f[e.indexOf(it)]else it)}} }

@Composable
private fun PerfTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme(); val colors = if (dark) darkColorScheme(primary=Color(0xFF9E8CFF),surface=Color(0xFF171927),background=Color(0xFF0D0F19)) else lightColorScheme(primary=Color(0xFF6657E5),surface=Color.White,background=Color(0xFFF6F7FB))
    val family = remember { FontFamily(ComposeFont(R.font.vazirmatn, FontWeight.Normal), ComposeFont(R.font.vazirmatn, FontWeight.Medium), ComposeFont(R.font.vazirmatn, FontWeight.SemiBold), ComposeFont(R.font.vazirmatn, FontWeight.Bold), ComposeFont(R.font.vazirmatn, FontWeight.Black)) }
    val base = Typography(); val typography = remember(family) { Typography(headlineSmall=base.headlineSmall.copy(fontFamily=family),titleLarge=base.titleLarge.copy(fontFamily=family),titleMedium=base.titleMedium.copy(fontFamily=family),titleSmall=base.titleSmall.copy(fontFamily=family),bodyLarge=base.bodyLarge.copy(fontFamily=family),bodyMedium=base.bodyMedium.copy(fontFamily=family),bodySmall=base.bodySmall.copy(fontFamily=family),labelLarge=base.labelLarge.copy(fontFamily=family),labelMedium=base.labelMedium.copy(fontFamily=family),labelSmall=base.labelSmall.copy(fontFamily=family)) }
    MaterialTheme(colorScheme=colors, typography=typography, content=content)
}
