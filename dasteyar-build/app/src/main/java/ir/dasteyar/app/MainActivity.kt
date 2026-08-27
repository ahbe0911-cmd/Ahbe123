package ir.dasteyar.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DasteYarTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) { DasteYarApp() }
                }
            }
        }
    }
}

data class AppCategory(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val packages: List<String> = emptyList()
)

data class LaunchableApp(val label: String, val packageName: String)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("dasteyar", Context.MODE_PRIVATE)
    val categories = mutableStateListOf<AppCategory>()
    var installedApps by mutableStateOf<List<LaunchableApp>>(emptyList())
        private set

    init { load() }

    suspend fun refreshApps() {
        installedApps = withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            val pm = app.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val found = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            found.asSequence()
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == app.packageName) return@mapNotNull null
                    LaunchableApp(
                        label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg },
                        packageName = pkg
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
                .toList()
        }
    }

    fun addCategory(title: String) {
        val value = title.trim()
        if (value.isBlank()) return
        categories.add(AppCategory(title = value))
        save()
    }

    fun renameCategory(id: String, title: String) {
        val value = title.trim()
        if (value.isBlank()) return
        update(id) { it.copy(title = value) }
    }

    fun deleteCategory(id: String) {
        categories.removeAll { it.id == id }
        save()
    }

    fun setApps(id: String, packages: Set<String>) {
        update(id) { it.copy(packages = packages.toList()) }
    }

    fun removeApp(id: String, pkg: String) {
        update(id) { it.copy(packages = it.packages.filterNot { p -> p == pkg }) }
    }

    fun launch(pkg: String) {
        val app = getApplication<Application>()
        app.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(it)
        }
    }

    fun label(pkg: String): String = installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg

    private fun update(id: String, transform: (AppCategory) -> AppCategory) {
        val index = categories.indexOfFirst { it.id == id }
        if (index < 0) return
        categories[index] = transform(categories[index])
        save()
    }

    private fun save() {
        val array = JSONArray()
        categories.forEach { category ->
            val packages = JSONArray()
            category.packages.forEach { packages.put(it) }
            array.put(
                JSONObject()
                    .put("id", category.id)
                    .put("title", category.title)
                    .put("packages", packages)
            )
        }
        prefs.edit().putString("categories", array.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString("categories", null) ?: return
        runCatching {
            val array = JSONArray(raw)
            repeat(array.length()) { i ->
                val obj = array.getJSONObject(i)
                val p = obj.optJSONArray("packages") ?: JSONArray()
                val packages = buildList {
                    repeat(p.length()) { j -> add(p.getString(j)) }
                }
                categories.add(
                    AppCategory(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "دسته"),
                        packages = packages
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DasteYarApp(vm: AppsViewModel = viewModel()) {
    var addDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppCategory?>(null) }
    var picking by remember { mutableStateOf<AppCategory?>(null) }

    LaunchedEffect(Unit) { vm.refreshApps() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("دسته‌یار", fontWeight = FontWeight.Black)
                        Text("لانچر شخصی برنامه‌ها", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addDialog = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("دسته جدید") }
            )
        }
    ) { padding ->
        if (vm.categories.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Apps, null, Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("اولین دسته را بساز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "مثلاً «کتابخانه» یا «کافی‌نت»؛ سپس برنامه‌های مربوط را از گوشی انتخاب کن.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { addDialog = true }) { Text("ساخت دسته") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.categories, key = { it.id }) { category ->
                    CategoryCard(
                        category = category,
                        vm = vm,
                        onEdit = { editing = category },
                        onDelete = { vm.deleteCategory(category.id) },
                        onPick = { picking = category }
                    )
                }
            }
        }
    }

    if (addDialog) {
        CategoryDialog(
            title = "دسته جدید",
            initial = "",
            onDismiss = { addDialog = false },
            onSave = {
                vm.addCategory(it)
                addDialog = false
            }
        )
    }

    editing?.let { category ->
        CategoryDialog(
            title = "ویرایش نام دسته",
            initial = category.title,
            onDismiss = { editing = null },
            onSave = {
                vm.renameCategory(category.id, it)
                editing = null
            }
        )
    }

    picking?.let { category ->
        AppPickerDialog(
            category = category,
            apps = vm.installedApps,
            onDismiss = { picking = null },
            onSave = {
                vm.setApps(category.id, it)
                picking = null
            }
        )
    }
}

@Composable
private fun CategoryCard(
    category: AppCategory,
    vm: AppsViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPick: () -> Unit
) {
    var expanded by remember(category.id) { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(category.title.take(1), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${category.packages.size} برنامه", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "ویرایش") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "حذف") }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                if (category.packages.isEmpty()) {
                    Text(
                        "هنوز برنامه‌ای انتخاب نشده است.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    category.packages.chunked(4).forEach { rowPkgs ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(4) { index ->
                                val pkg = rowPkgs.getOrNull(index)
                                if (pkg == null) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    AppTile(
                                        modifier = Modifier.weight(1f),
                                        packageName = pkg,
                                        label = vm.label(pkg),
                                        onClick = { vm.launch(pkg) },
                                        onRemove = { vm.removeApp(category.id, pkg) }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                FilledTonalButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("انتخاب برنامه‌ها")
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    modifier: Modifier,
    packageName: String,
    label: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(modifier.padding(horizontal = 2.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(packageName, Modifier.size(52.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, maxLines = 2, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)
        ) {
            Icon(Icons.Rounded.Close, "حذف از دسته", Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(128, 128)
                .asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(BitmapPainter(bitmap), null, modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Apps, null)
        }
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("نام دسته") },
                placeholder = { Text("مثلاً کتابخانه") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(enabled = value.isNotBlank(), onClick = { onSave(value) }) { Text("ذخیره") }
        },
        dismissButton = { FilledTonalButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun AppPickerDialog(
    category: AppCategory,
    apps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val selected = remember(category.id, category.packages) {
        mutableStateListOf<String>().apply { addAll(category.packages) }
    }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برنامه‌های ${category.title}") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("جست‌وجوی برنامه") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (checked) selected.remove(app.packageName) else selected.add(app.packageName)
                            }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(app.packageName, Modifier.size(42.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
            Button(onClick = { onSave(selected.toSet()) }) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(6.dp))
                Text("ثبت ${selected.size} برنامه")
            }
        },
        dismissButton = { FilledTonalButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun DasteYarTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
