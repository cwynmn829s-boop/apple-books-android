package com.cwynmn.books

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.room.Room
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { BooksApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksApp() {
    val context = LocalContext.current
    val dao = remember { BooksDb.get(context).books() }
    val books by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("书库") }
    var active by remember { mutableStateOf<Book?>(null) }
    var search by remember { mutableStateOf("") }
    var listMode by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { dao.put(BookParser.import(context, uri)) }
                    .onFailure {
                        android.widget.Toast.makeText(
                            context,
                            it.message ?: "导入失败",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xff8b3a3a),
            background = Color(0xfffaf9f6),
            surface = Color(0xfffaf9f6)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xfffaf9f6)) {
            if (active != null) {
                Reader(active!!, dao, { active = null })
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(Color(0xeefaf9f6))
                                .border(.5.dp, Color(0x33888888))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(
                                    start = 22.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 10.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (tab == "书库") "书库" else tab,
                                    Modifier.weight(1f),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (tab == "书库") {
                                    IconButton(onClick = { sort = !sort }) {
                                        Icon(Icons.Default.Sort, "排序")
                                    }
                                    IconButton(onClick = { listMode = !listMode }) {
                                        Icon(
                                            if (listMode) Icons.Default.GridView else Icons.Default.ViewList,
                                            "视图"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            picker.launch(
                                                arrayOf(
                                                    "application/epub+zip",
                                                    "text/plain",
                                                    "application/octet-stream"
                                                )
                                            )
                                        }
                                    ) { Icon(Icons.Default.Add, "导入图书") }
                                }
                            }
                            if (sort) {
                                Row(
                                    Modifier.padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("标题", "最近添加").forEach {
                                        TextButton(onClick = { sort = false }) { Text(it) }
                                    }
                                }
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xeefaf9f6),
                            tonalElevation = 0.dp
                        ) {
                            listOf(
                                "阅读中" to Icons.Default.AutoStories,
                                "书库" to Icons.Default.BookmarkBorder,
                                "书店" to Icons.Default.Storefront,
                                "搜索" to Icons.Default.Search
                            ).forEach { (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == label,
                                    onClick = { tab = label },
                                    icon = { Icon(icon, label) },
                                    label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xff9b4a44),
                                        indicatorColor = Color(0x229b4a44)
                                    )
                                )
                            }
                        }
                    }
                ) { padding ->
                    when (tab) {
                        "搜索" -> Column(Modifier.padding(padding).padding(22.dp)) {
                            OutlinedTextField(
                                search,
                                { search = it },
                                Modifier.fillMaxWidth(),
                                label = { Text("搜索书库") },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
                            Spacer(Modifier.height(12.dp))
                            BookGrid(
                                books.filter {
                                    it.title.contains(search, true) || it.author.contains(search, true)
                                },
                                listMode,
                                { active = it }
                            )
                        }
                        "阅读中" -> Column(
                            Modifier.padding(padding).fillMaxSize().padding(22.dp)
                        ) {
                            Text("继续阅读", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(18.dp))
                            BookGrid(books.filter { it.progress > 0 }, false, { active = it })
                        }
                        "书店" -> Column(
                            Modifier.padding(padding).fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Storefront, null, Modifier.size(54.dp), tint = Color.Gray)
                            Text("书店", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("本地阅读空间 · 书店暂不提供在线购买", color = Color.Gray)
                        }
                        else -> BookGrid(books, listMode, { active = it }, Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

@Composable
fun BookGrid(books: List<Book>, list: Boolean, onOpen: (Book) -> Unit, modifier: Modifier = Modifier) {
    if (books.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("书架还是空的", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("点右上角 + 导入 EPUB 或 TXT", color = Color.Gray)
        }
        return
    }
    if (list) {
        LazyColumn(modifier) {
            items(books) { book ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(book) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(book, 72.dp)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(book.title, fontWeight = FontWeight.SemiBold)
                        Text(book.author.ifBlank { "未知作者" }, color = Color.Gray)
                    }
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            items(books) { book: Book ->
                Column(Modifier.clickable { onOpen(book) }) {
                    Cover(book, 155.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(book.title, maxLines = 2, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        book.author.ifBlank { "未知作者" },
                        maxLines = 1,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun Cover(book: Book, height: Dp) {
    Box(
        Modifier.width(height * .68f).height(height).shadow(8.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp)).background(Color(book.coverColor))
            .border(1.dp, Color.White.copy(.2f), RoundedCornerShape(4.dp))
            .clickable { }.padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxHeight().width(5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(.35f), Color.White.copy(.12f), Color.Transparent)
                    )
                )
        )
        Text(
            book.title,
            Modifier.padding(start = 5.dp),
            color = Color.White,
            fontSize = if (height > 100) 16.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 4,
            lineHeight = 20.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Reader(book: Book, dao: BookDao, onBack: () -> Unit) {
    val chapters = remember(book) {
        runCatching { JSONArray(book.chaptersJson) }.getOrElse { JSONArray() }.let { array ->
            (0 until array.length()).map { index ->
                array.getJSONObject(index).let { it.getString("title") to it.getString("text") }
            }
        }
    }
    var chapter by remember(book) {
        mutableIntStateOf(book.chapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
    }
    var page by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf("") }
    var theme by remember { mutableIntStateOf(0) }
    var font by remember { mutableFloatStateOf(20f) }
    var progress by remember { mutableFloatStateOf(book.progress) }
    var drag by remember { mutableFloatStateOf(0f) }
    val bg = listOf(Color(0xfffdfcf9), Color(0xfff1e8d7), Color(0xff222222), Color.Black)[theme]
    val fg = if (theme < 2) Color(0xff292822) else Color(0xffeeeeea)

    fun move(delta: Int) {
        if (chapters.isEmpty()) return
        if (delta > 0) {
            if (chapter < chapters.lastIndex) chapter++ else page++
        } else {
            if (page > 0) page-- else if (chapter > 0) chapter--
        }
        progress = ((chapter + page * .01f) / chapters.size).coerceIn(0f, 1f)
    }

    LaunchedEffect(chapter, progress) { dao.progress(book.id, progress, chapter) }
    Box(
        Modifier.fillMaxSize().background(bg)
            .pointerInput(chapter, page) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, delta -> drag += delta },
                    onDragEnd = {
                        if (abs(drag) > 48) move(if (drag < 0) 1 else -1)
                        drag = 0f
                    }
                )
            }
            .clickable { hud = !hud }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 38.dp)) {
            Text(
                chapters.getOrNull(chapter)?.first ?: book.title,
                fontSize = 14.sp,
                color = fg.copy(.55f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(35.dp))
            Text(
                (chapters.getOrNull(chapter)?.second ?: "").ifBlank { "　" },
                fontSize = font.sp,
                lineHeight = (font * 1.8f).sp,
                color = fg,
                textAlign = TextAlign.Justify,
                modifier = Modifier.weight(1f).fillMaxWidth().clickable { move(1) }
                    .verticalScroll(rememberScrollState())
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${chapter + 1} / ${chapters.size} 章", fontSize = 12.sp, color = fg.copy(.5f))
                Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = fg.copy(.5f))
            }
        }
        AnimatedVisibility(
            hud,
            Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xeefaf9f6)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                Text(book.title, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Medium)
                IconButton(onClick = { sheet = "目录" }) {
                    Icon(Icons.Default.BookmarkBorder, "目录")
                }
            }
        }
        AnimatedVisibility(
            hud,
            Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            Column(
                Modifier.padding(20.dp).clip(RoundedCornerShape(24.dp))
                    .background(Color(0xf5ffffff))
                    .border(.7.dp, Color(0x22808080), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TextButton(onClick = { sheet = "目录" }) { Text("目录") }
                    TextButton(onClick = { sheet = "主题与排版" }) { Text("主题与排版") }
                    Text("${(progress * 100).toInt()}%", fontSize = 12.sp)
                }
                Slider(
                    value = progress,
                    onValueChange = {
                        progress = it
                        chapter = (it * chapters.size).toInt()
                            .coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
                    },
                    valueRange = 0f..1f
                )
            }
        }
        if (!hud) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable { move(-1) })
                Box(Modifier.weight(1f).fillMaxHeight().clickable { move(1) })
            }
        }
    }
    if (sheet.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { sheet = "" }, containerColor = bg) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(sheet, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = fg)
                Spacer(Modifier.height(12.dp))
                if (sheet == "目录") {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(chapters.size) { index ->
                            TextButton(
                                onClick = {
                                    chapter = index
                                    page = 0
                                    sheet = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    chapters[index].first,
                                    Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start,
                                    color = fg
                                )
                            }
                        }
                    }
                } else {
                    listOf("浅色", "羊皮纸", "深色", "纯黑").forEachIndexed { index, label ->
                        TextButton(onClick = { theme = index }) {
                            Text(if (theme == index) "✓  $label" else label, color = fg)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("字体大小", color = fg)
                        Spacer(Modifier.width(16.dp))
                        TextButton(onClick = { font = (font - 1).coerceAtLeast(15f) }) {
                            Text("A−")
                        }
                        TextButton(onClick = { font = (font + 1).coerceAtMost(32f) }) {
                            Text("A＋")
                        }
                    }
                    Text("行间距 1.8", color = fg, fontSize = 14.sp)
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}
