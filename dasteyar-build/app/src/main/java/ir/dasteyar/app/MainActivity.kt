package ir.dasteyar.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

data class AppEditTarget(val categoryId: String, val packageName: String)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("dasteyar", Context.MODE_PRIVATE)

    val categories = mutableStateListOf<AppCategory>()
    val aliases = mutableStateMapOf<String, String>()

    var installedApps by mutableStateOf<List<LaunchableApp>>(emptyList())
        private set

    init {
        load()
    }

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
        updateCategory(id) { it.copy(title = value) }
    }

    fun deleteCategory(id: String) {
        categories.removeAll { it.id == id }
        save()
    }

    fun setApps(id: String, packages: List<String>) {
        updateCategory(id) { it.copy(packages = packages.distinct()) }
    }

    fun removeApp(id: String, pkg: String) {
        updateCategory(id) { category ->
            category.copy(packages = category.packages.filterNot { it == pkg })
        }
    }

    fun renameApp(pkg: String, customName: String) {
        val value = customName.trim()
        val original = label(pkg)
        if (value.isBlank() || value == original) {
            aliases.remove(pkg)
        } else {
            aliases[pkg] = value
        }
        save()
    }

    fun launch(pkg: String) {
        val app = getApplication<Application>()
        app.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }
    }

    fun label(pkg: String): String =
        installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg

    fun displayName(pkg: String): String = aliases[pkg]?.takeIf { it.isNotBlank() } ?: label(pkg)

    private fun updateCategory(id: String, transform: (AppCategory) -> AppCategory) {
        val index = categories.indexOfFirst { it.id == id }
        if (index < 0) return
        categories[index] = transform(categories[index])
        save()
    }

    private fun save() {
        val categoriesArray = JSONArray()
        categories.forEach { category ->
            val packages = JSONArray()
            category.packages.forEach { packages.put(it) }
            categoriesArray.put(
                JSONObject()
                    .put("id", category.id)
                    .put("title", category.title)
                    .put("packages", packages)
            )
        }

        val aliasesObject = JSONObject()
        aliases.forEach { (pkg, value) -> aliasesObject.put(pkg, value) }

        prefs.edit()
            .putString("categories", categoriesArray.toString())
            .putString("aliases", aliasesObject.toString())
            .apply()
    }

    private fun load() {
        val rawCategories = prefs.getString("categories", null)
        if (rawCategories != null) {
            runCatching {
                val array = JSONArray(rawCategories)
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

        val rawAliases = prefs.getString("aliases", null)
        if (rawAliases != null) {
            runCatching {
                val obj = JSONObject(rawAliases)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.optString(key)
                    if (value.isNotBlank()) aliases[key] = value
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DasteYarApp(vm: AppsViewModel = viewModel()) {
    var addCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<AppCategory?>(null) }
    var deletingCategory by remember { mutableStateOf<AppCategory?>(null) }
    var pickingCategory by remember { mutableStateOf<AppCategory?>(null) }
    var editingApp by remember { mutableStateOf<AppEditTarget?>(null) }

    LaunchedEffect(Unit) { vm.refreshApps() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "دسته‌یار",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "برنامه‌هایت را به سبک خودت بچین",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addCategoryDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("دسته جدید", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (vm.categories.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                onCreate = { addCategoryDialog = true }
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 92.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.categories, key = { it.id }) { category ->
                    CategoryColumn(
                        modifier = Modifier.width(216.dp).fillMaxHeight(),
                        category = category,
                        vm = vm,
                        onRenameCategory = { editingCategory = category },
                        onDeleteCategory = { deletingCategory = category },
                        onPickApps = { pickingCategory = category },
                        onEditApp = { pkg ->
                            editingApp = AppEditTarget(category.id, pkg)
                        }
                    )
                }
            }
        }
    }

    if (addCategoryDialog) {
        CategoryDialog(
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
        CategoryDialog(
            title = "ویرایش نام دسته",
            initial = category.title,
            onDismiss = { editingCategory = null },
            onSave = {
                vm.renameCategory(category.id, it)
                editingCategory = null
            }
        )
    }

    deletingCategory?.let { category ->
        ConfirmDeleteDialog(
            title = "حذف دسته «${category.title}»؟",
            body = "فقط این دسته از دسته‌یار حذف می‌شود؛ هیچ برنامه‌ای از گوشی پاک نخواهد شد.",
            onDismiss = { deletingCategory = null },
            onConfirm = {
                vm.deleteCategory(category.id)
                deletingCategory = null
            }
        )
    }

    pickingCategory?.let { category ->
        AppPickerDialog(
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
        AppNameDialog(
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
private fun EmptyState(modifier: Modifier = Modifier, onCreate: () -> Unit) {
    Box(modifier.padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(86.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Apps, contentDescription = null, Modifier.size(42.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "اولین ستونت را بساز",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "مثلاً کتابخانه، کافی‌نت یا کار؛ بعد برنامه‌های مربوط را داخل همان ستون قرار بده.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ساخت دسته")
            }
        }
    }
}

@Composable
private fun CategoryColumn(
    modifier: Modifier,
    category: AppCategory,
    vm: AppsViewModel,
    onRenameCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onPickApps: () -> Unit,
    onEditApp: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${category.packages.size} برنامه",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "گزینه‌های دسته")
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
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "هنوز برنامه‌ای در این ستون نیست",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(category.packages, key = { it }) { pkg ->
                        AppRow(
                            packageName = pkg,
                            displayName = vm.displayName(pkg),
                            onLaunch = { vm.launch(pkg) },
                            onRename = { onEditApp(pkg) },
                            onRemove = { vm.removeApp(category.id, pkg) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            FilledTonalButton(
                onClick = onPickApps,
                modifier = Modifier.fillMaxWidth().padding(10.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("افزودن برنامه", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AppRow(
    packageName: String,
    displayName: String,
    onLaunch: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLaunch),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName, Modifier.size(48.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                text = displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "ویرایش برنامه")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("ویرایش نام نمایشی") },
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
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(128, 128)
                    .asImageBitmap()
            }.getOrNull()
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
            modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Apps, contentDescription = null, Modifier.size(24.dp))
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
        title = { Text(title, fontWeight = FontWeight.Black) },
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
            Button(enabled = value.isNotBlank(), onClick = { onSave(value) }) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun AppNameDialog(
    originalName: String,
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نام نمایشی برنامه", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(
                    "این تغییر فقط داخل دسته‌یار نمایش داده می‌شود و نام برنامه اصلی گوشی تغییر نمی‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("نام دلخواه") },
                    supportingText = { Text("نام اصلی: $originalName") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(value) }) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onConfirm) { Text("حذف") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun AppPickerDialog(
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
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب برنامه‌های ${category.title}", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("جست‌وجوی برنامه") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selected.remove(app.packageName)
                                    else selected.add(app.packageName)
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(app.packageName, Modifier.size(42.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                app.label,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selected.remove(app.packageName)
                                    else selected.add(app.packageName)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected.toList()) }) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("ثبت ${selected.size} برنامه")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

private fun createVazirTypography(): Typography {
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
private fun DasteYarTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFFAEC6FF),
            onPrimary = Color(0xFF08235A),
            primaryContainer = Color(0xFF173A72),
            onPrimaryContainer = Color(0xFFD9E2FF),
            secondary = Color(0xFFC4C7D0),
            secondaryContainer = Color(0xFF43464E),
            background = Color(0xFF101116),
            surface = Color(0xFF181A20),
            surfaceVariant = Color(0xFF2A2D35),
            outline = Color(0xFF8E909A)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF355C9A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCE6FF),
            onPrimaryContainer = Color(0xFF0B2B5A),
            secondary = Color(0xFF5A6070),
            secondaryContainer = Color(0xFFE4E7EF),
            background = Color(0xFFF6F7FB),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF0F2F7),
            outline = Color(0xFF767984)
        )
    }

    val typography = remember { createVazirTypography() }
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        content = content
    )
}
