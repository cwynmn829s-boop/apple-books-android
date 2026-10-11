package com.cwynmn.books

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.abs

private data class Sample(val title: String, val author: String, val chapters: List<Pair<String, String>>, val color: Long)
private val samples = listOf(
    Sample("山海之间", "林清玄", listOf("序·山路" to "清晨的山路被薄雾轻轻覆盖。远处的溪水穿过石缝，像一封没有寄出的信。行走的人不必急着抵达，风景正在脚下慢慢展开。", "第一章·溪声" to "溪流从松林深处来，带着凉意，也带着春天的消息。我们沿着石阶向上，听见鸟鸣在山谷里回响。"), 0xff526b64),
    Sample("星河来信", "沈星河", listOf("第一章·夜航" to "夜色像深蓝的海。窗外的城市逐渐沉静，只有远方的灯塔仍然闪烁。她拆开那封迟到的信，纸上写着：请抬头看看星星。", "第二章·回声" to "星光越过漫长的距离，落在旧车站的屋檐。每一次告别都藏着重新出发的可能。"), 0xff394b72),
    Sample("纸上江南", "周作人", listOf("一·雨巷" to "雨落在青石板上，巷子深处传来一阵橹声。白墙旁的乌桕树刚抽新叶，春天便在水汽中有了形状。", "二·茶烟" to "小店临河，窗边摆着一壶新茶。茶烟袅袅，谈话声也慢下来。"), 0xff8a654d),
    Sample("慢慢生活", "顾城", listOf("清晨" to "把窗打开，让风进来。桌上有一朵花，一本书，还有尚未写完的句子。生活不需要每一刻都奔向远方。", "黄昏" to "太阳落下时，街灯一盏盏亮起。回家的路并不遥远，只要心里留着一处安静。"), 0xff715a70),
    Sample("海边的灯", "许知远", listOf("潮汐" to "海潮一遍遍抵达岸边，又安静地退去。灯塔守着夜晚，也守着每一个仍在等待的人。", "归航" to "船帆在晨光里升起，海面被染成温柔的银色。"), 0xff397184),
    Sample("四季札记", "木心", listOf("春" to "新芽从枝头探出，雨水让泥土散发出清新的气息。", "夏" to "树荫下的蝉声绵长，午后适合读书与小憩。", "秋与冬" to "落叶回到土地，雪覆盖安静的街道。四季更替，生活仍有新的章节。"), 0xff94724b)
)
private data class ReadBook(val id: String, val title: String, val author: String, val chapters: List<Pair<String, String>>, val color: Long, val progress: Float, val path: String? = null)
private data class Mark(val chapter: Int, val label: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.decorView.systemUiVisibility = (4 or 1024 or 4096); setContent { ReaderApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("reader", 0) }
    var imported by remember { mutableStateOf(prefs.getString("imports", "") ?: "") }
    val extra = remember(imported) { imported.split("\n").filter { it.isNotBlank() }.mapNotNull { path -> runCatching { val f=File(path); ReadBook(path, f.nameWithoutExtension, "本地文件", listOf("正文" to f.readText()), 0xff596a82, 0f, path) }.getOrNull() } }
    var selected by remember { mutableStateOf<ReadBook?>(null) }
    var query by remember { mutableStateOf("") }
    val books = remember(extra) { samples.mapIndexed { i, s -> ReadBook("sample$i", s.title, s.author, s.chapters, s.color, prefs.getFloat("p_sample$i", 0f)) } + extra }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { runCatching { val f=File(context.filesDir, "book_${UUID.randomUUID()}.txt"); context.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use(input::copyTo) }; imported = (imported + "\n" + f.absolutePath).trim(); prefs.edit().putString("imports", imported).apply() } }
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xff101319), surface = Color(0xff191e27), primary = Color(0xffc6a978))) {
        Surface(Modifier.fillMaxSize(), color = Color(0xff101319)) {
            if (selected != null) ReaderScreen(selected!!, prefs, { selected = null }) else {
                Scaffold(containerColor = Color.Transparent, topBar = { Column(Modifier.padding(horizontal=22.dp, vertical=16.dp)) {
                    Text("静阅", color=Color(0xffe9e4da), fontSize=34.sp, fontWeight=FontWeight.Bold)
                    Text("在文字之间，留一段安静", color=Color(0xff999da7), fontSize=14.sp)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(query, { query=it }, Modifier.fillMaxWidth(), singleLine=true, placeholder={Text("搜索书名或作者")}, leadingIcon={Icon(Icons.Default.Search, null)}, colors=OutlinedTextFieldDefaults.colors(unfocusedTextColor=Color.White, focusedTextColor=Color.White))
                } }, floatingActionButton = { FloatingActionButton(onClick={picker.launch(arrayOf("text/plain", "application/octet-stream"))}, containerColor=Color(0xffc6a978), contentColor=Color(0xff141414)) { Icon(Icons.Default.Add, "导入 TXT") } }) { padding ->
                    LazyColumn(Modifier.padding(padding), contentPadding=PaddingValues(start=22.dp,end=22.dp,bottom=100.dp), verticalArrangement=Arrangement.spacedBy(15.dp)) {
                        item { Text("书架 · ${books.size}", color=Color(0xffd8d1c3), fontSize=18.sp, fontWeight=FontWeight.SemiBold) }
                        items(books.filter { it.title.contains(query,true) || it.author.contains(query,true) }) { book ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xff191e27)).clickable { selected=book }.padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                                Box(Modifier.width(82.dp).height(112.dp).shadow(8.dp,RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp)).background(Color(book.color)), contentAlignment=Alignment.Center) { Text(book.title, Modifier.padding(8.dp), color=Color.White, textAlign=TextAlign.Center, fontWeight=FontWeight.Bold) }
                                Column(Modifier.padding(start=16.dp).weight(1f)) { Text(book.title, color=Color(0xfff0eee9), fontSize=17.sp, fontWeight=FontWeight.SemiBold); Text(book.author, color=Color(0xffa6a8af), fontSize=13.sp); Spacer(Modifier.height(15.dp)); LinearProgressIndicator(progress=book.progress.coerceIn(0f,1f), modifier=Modifier.fillMaxWidth(), color=Color(0xffc6a978), trackColor=Color(0xff303640)); Text(if(book.progress>0) "已读 ${(book.progress*100).toInt()}%" else "尚未阅读", color=Color(0xff858a94), fontSize=12.sp, modifier=Modifier.padding(top=5.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(book: ReadBook, prefs: android.content.SharedPreferences, back: () -> Unit) {
    var chapter by remember(book.id) { mutableIntStateOf(prefs.getInt("c_${book.id}", 0).coerceIn(0, book.chapters.lastIndex.coerceAtLeast(0))) }
    var font by remember { mutableFloatStateOf(20f) }
    var line by remember { mutableFloatStateOf(1.65f) }
    var theme by remember { mutableIntStateOf(1) }
    var controls by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val marksKey = "marks_${book.id}"
    var markList by remember(book.id) { mutableStateOf(prefs.getStringSet(marksKey, emptySet())!!.mapNotNull { entry -> entry.split("|", limit=2).takeIf { it.size==2 }?.let { parts -> parts[0].toIntOrNull()?.let { Mark(it, parts[1]) } } }) }
    val colors = listOf(Color(0xfffaf9f6) to Color(0xff25241f), Color(0xffeee4cf) to Color(0xff342d22), Color(0xff202630) to Color(0xffe4e7ec), Color.Black to Color(0xffeeeeee))
    val (paper, ink) = colors[theme]
    val amount by animateFloatAsState(if(dragX<0) (-dragX/600f).coerceIn(0f,.75f) else 0f, label="curl")
    fun move(delta:Int) { chapter=(chapter+delta).coerceIn(0,book.chapters.lastIndex.coerceAtLeast(0)); prefs.edit().putInt("c_${book.id}",chapter).putFloat("p_${book.id}", if(book.chapters.isEmpty()) 0f else chapter.toFloat()/book.chapters.size).apply() }
    Box(Modifier.fillMaxSize().background(paper).pointerInput(chapter) { detectDragGestures(onDragEnd={ if(abs(dragX)>70) move(if(dragX<0) 1 else -1); dragX=0f }, onDrag={ change, delta -> change.consume(); dragX+=delta.x }) }.clickable { controls=!controls }) {
        val pair=book.chapters.getOrNull(chapter) ?: ("正文" to "暂无内容")
        Column(Modifier.fillMaxSize().padding(horizontal=30.dp, vertical=38.dp)) {
            Text(book.title, color=ink.copy(.55f), fontSize=13.sp, modifier=Modifier.fillMaxWidth(), textAlign=TextAlign.Center)
            Spacer(Modifier.height(34.dp)); Text(pair.first, color=ink, fontSize=25.sp, fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(20.dp)); Text(pair.second, color=ink, fontSize=font.sp, lineHeight=(font*line).sp, modifier=Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { Text("${chapter+1} / ${book.chapters.size} 章", color=ink.copy(.6f), fontSize=12.sp); Text("${if(book.chapters.isEmpty()) 0 else (chapter*100/book.chapters.size)}% · ${chapter+1}页", color=ink.copy(.6f), fontSize=12.sp) }
        }
        if(dragX<0) Canvas(Modifier.fillMaxSize()) { val w=size.width*amount; drawRect(Brush.horizontalGradient(listOf(Color.Transparent,Color.Black.copy(.32f),Color.Transparent), startX=size.width-w-20, endX=size.width), topLeft=Offset(size.width-w,0f), size=androidx.compose.ui.geometry.Size(w,size.height)); val curlPath=Path().apply { moveTo(size.width-w,0f); lineTo(size.width,0f); lineTo(size.width,size.height); lineTo(size.width-w,size.height); close() }; drawPath(curlPath, Color.White.copy(.13f)) }
        if(controls) Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xee171b22)).padding(10.dp)) { Row(verticalAlignment=Alignment.CenterVertically) { IconButton(onClick=back) { Icon(Icons.Default.ArrowBack,"返回",tint=Color.White) }; Text(book.title, Modifier.weight(1f), color=Color.White); IconButton(onClick={ val updated=(markList+Mark(chapter,pair.first)).distinct(); markList=updated; prefs.edit().putStringSet(marksKey, updated.map { "${it.chapter}|${it.label}" }.toSet()).apply() }) { Icon(Icons.Default.BookmarkAdd,"书签",tint=Color.White) }; IconButton(onClick={settings=true}) { Icon(Icons.Default.TextFields,"排版",tint=Color.White) } }; Slider(value=chapter.toFloat(), onValueChange={ move(it.toInt()) }, valueRange=0f..book.chapters.lastIndex.coerceAtLeast(1).toFloat()) }
        if(controls) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xee171b22)).padding(14.dp), horizontalArrangement=Arrangement.SpaceBetween) { TextButton(onClick={move(-1)}) { Text("上一页") }; Text("${chapter+1} / ${book.chapters.size}",color=Color.White); TextButton(onClick={move(1)}) { Text("下一页") } }
    }
    if(settings) AlertDialog(onDismissRequest={settings=false}, title={Text("阅读设置")}, text={ Column { Text("字体大小 ${font.toInt()}sp"); Slider(value=font,onValueChange={font=it},valueRange=15f..32f); Text("行间距 ${String.format("%.2f",line)}"); Slider(value=line,onValueChange={line=it},valueRange=1.3f..2.2f); Text("页面主题"); Row { listOf("浅色","纸张","石墨","墨黑").forEachIndexed { i,label -> TextButton(onClick={theme=i}) { Text(label) } } }; Text("本书书签：${markList.joinToString { "第${it.chapter+1}章 ${it.label}" }}") } }, confirmButton={TextButton(onClick={settings=false}) { Text("完成") }})
}
