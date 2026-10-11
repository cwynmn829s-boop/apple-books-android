package com.cwynmn.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

object BookParser {
    data class Parsed(
        val title: String,
        val author: String,
        val chapters: List<Pair<String, String>>
    )

    fun import(context: Context, uri: Uri): Book {
        val name = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Imported book"
        val ext = name.substringAfterLast('.', "txt").lowercase()
        val dest = File(context.filesDir, "${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected file" }.use {
                it.copyTo(dest.outputStream())
            }
        }
        val parsed = if (ext == "epub") {
            parseEpub(dest)
        } else if (ext == "txt") {
            parseTxt(dest, name)
        } else {
            error("Please choose an EPUB or TXT file")
        }
        val chapters = JSONArray().apply {
            parsed.chapters.forEach {
                put(JSONObject().put("title", it.first).put("text", it.second))
            }
        }.toString()
        val colors = listOf(0xff715a70, 0xff526c68, 0xff8a654d, 0xff394b72, 0xffaa8069)
        return Book(
            UUID.randomUUID().toString(),
            parsed.title.ifBlank { name.substringBeforeLast('.') },
            parsed.author,
            dest.absolutePath,
            colors.random(),
            chapters
        )
    }

    private fun parseTxt(file: File, filename: String): Parsed {
        val bytes = file.readBytes()
        val text = runCatching { bytes.toString(Charsets.UTF_8) }
            .getOrElse { bytes.toString(Charset.forName("GBK")) }
            .removePrefix("\uFEFF")
        val regex = Regex("(?m)^\\s*((?:第\\s*[一二三四五六七八九十百零〇0-9]+\\s*[章节回卷].*)|(?:Chapter\\s+\\d+.*))$")
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) {
            return Parsed(filename.substringBeforeLast('.'), "", listOf("正文" to text))
        }
        val result = mutableListOf<Pair<String, String>>()
        if (matches.first().range.first > 0) {
            result += "前言" to text.substring(0, matches.first().range.first).trim()
        }
        matches.forEachIndexed { i, match ->
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            result += match.value.trim() to text.substring(match.range.last + 1, end).trim()
        }
        return Parsed(filename.substringBeforeLast('.'), "", result)
    }

    private fun parseEpub(file: File): Parsed {
        ZipFile(file).use { zip ->
            fun read(path: String) = zip.getInputStream(
                zip.getEntry(path) ?: error("Missing EPUB entry: $path")
            ).bufferedReader(Charsets.UTF_8).readText()

            val container = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(zip.getInputStream(zip.getEntry("META-INF/container.xml")))
            val opfPath = container.getElementsByTagName("rootfile")
                .item(0).attributes.getNamedItem("full-path").nodeValue
            val base = opfPath.substringBeforeLast('/', "")
            val opf = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(zip.getInputStream(zip.getEntry(opfPath)))
            val meta = opf.getElementsByTagName("dc:title")
            val title = if (meta.length > 0) meta.item(0).textContent else file.nameWithoutExtension
            val authors = opf.getElementsByTagName("dc:creator")
            val author = if (authors.length > 0) authors.item(0).textContent else ""
            val items = opf.getElementsByTagName("item")
            val hrefs = mutableMapOf<String, String>()
            for (i in 0 until items.length) {
                val node = items.item(i)
                hrefs[node.attributes.getNamedItem("id").nodeValue] =
                    node.attributes.getNamedItem("href").nodeValue
            }
            val refs = opf.getElementsByTagName("itemref")
            val chapters = mutableListOf<Pair<String, String>>()
            for (i in 0 until refs.length) {
                val id = refs.item(i).attributes.getNamedItem("idref").nodeValue
                val href = hrefs[id] ?: continue
                val path = listOf(base, href).filter { it.isNotBlank() }.joinToString("/")
                val doc = Jsoup.parse(read(path))
                val heading = doc.selectFirst("h1,h2,title")?.text()?.takeIf { it.isNotBlank() }
                    ?: "第${i + 1}章"
                chapters += heading to doc.body().text()
            }
            return Parsed(
                title,
                author,
                chapters.ifEmpty { listOf("正文" to "无法读取书籍章节。") }
            )
        }
    }
}
