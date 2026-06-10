package com.alquranplus.tools

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.sql.DriverManager
import java.util.concurrent.TimeUnit

/**
 * TOOL VERSI CEPAT (PARALLEL) UNTUK GENERATE DATABASE HADITS
 * Fitur: Resume, Auto-Retry, Null-Safe, Audit Bahasa Valid.
 * 
 * Lokasi Baru: :tools module (Stand-alone JVM Tool)
 */
class HadeethEncGenerator {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) = runBlocking {
            HadeethEncGenerator().run()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://hadeethenc.com/api/v1"
    private val semaphore = Semaphore(3) // Download paralel terbatas agar stabil

    suspend fun run() {
        // Target bahasa yang ingin di-generate ulang (Sinkron dengan GitHub & App: 32 bahasa)
        val languages = listOf(
            "am", "ar", "az", "bn", "bs", "de", "en", "es", "fa", "fr", "ha",
            "hi", "id", "ku", "ml", "ms", "om", "pa", "ps", "pt", "ru", "so", "sq",
            "sw", "ta", "te", "th", "tr", "ur", "uz", "yo", "zh"
        )
        
        println("=== HadeethEnc Generator Turbo (Audited & Improved) ===")
        println("Daftar Bahasa Valid (${languages.size}): ${languages.joinToString(", ")}")
        
        languages.forEachIndexed { index, lang ->
            try {
                println("\n[${index + 1}/${languages.size}] Memproses Bahasa: $lang")
                generateDatabase(lang)
            } catch (e: Exception) {
                println("\n[FATAL ERROR] $lang: ${e.message}")
            }
        }
        println("\n=== SEMUA PROSES SELESAI! ===")
    }

    suspend fun generateDatabase(lang: String) = coroutineScope {
        val dbFileName = "hadeethenc_$lang.db"
        val failedIds = mutableListOf<String>()
        var successCount = 0
        var failedCount = 0
        var skippedCount = 0

        DriverManager.getConnection("jdbc:sqlite:$dbFileName").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { stmt ->
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS hadeeth_enc (id TEXT PRIMARY KEY, title TEXT, hadeeth TEXT, explanation TEXT, attribution TEXT, grade TEXT, reference TEXT, language TEXT)")
            }

            // Fitur Resume: Ambil ID yang sudah ada di database
            val existingIds = mutableSetOf<String>()
            connection.createStatement().use { stmt ->
                stmt.executeQuery("SELECT id FROM hadeeth_enc").use { rs ->
                    while (rs.next()) {
                        existingIds.add(rs.getString("id"))
                    }
                }
            }
            if (existingIds.isNotEmpty()) {
                println("  -> Ditemukan ${existingIds.size} hadits lama. Mode Resume aktif.")
            }

            println("  -> Mengambil daftar kategori...")
            val categoriesJson = fetchUrlWithRetry("$baseUrl/categories/list/?language=$lang")
            if (categoriesJson == null || categoriesJson == "[]" || categoriesJson.isEmpty()) {
                throw Exception("Gagal mengambil kategori atau bahasa '$lang' tidak tersedia di API")
            }
            
            val categories = JSONArray(categoriesJson)
            val uniqueIds = mutableSetOf<String>()

            for (i in 0 until categories.length()) {
                val catId = categories.getJSONObject(i).optString("id", "")
                if (catId.isEmpty()) continue
                
                var page = 1
                while (true) {
                    val listJson = fetchUrlWithRetry("$baseUrl/hadeeths/list/?language=$lang&category_id=$catId&page=$page&per_page=100")
                    if (listJson == null) break
                    
                    val root = JSONObject(listJson)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    for (j in 0 until data.length()) {
                        val item = data.getJSONObject(j)
                        val id = item.optString("id", "")
                        if (id.isNotEmpty()) uniqueIds.add(id)
                    }
                    
                    val meta = root.optJSONObject("meta")
                    val lastPage = meta?.optInt("last_page", 1) ?: 1
                    if (page >= lastPage) break
                    page++
                }
            }

            val totalFound = uniqueIds.size
            val idsToDownload = uniqueIds.filter { it !in existingIds }
            skippedCount = totalFound - idsToDownload.size
            
            println("  -> Total hadits di API: $totalFound")
            println("  -> Akan didownload: ${idsToDownload.size} (Lainnya sudah ada)")

            val mainInsert = connection.prepareStatement("INSERT OR REPLACE INTO hadeeth_enc VALUES (?,?,?,?,?,?,?,?)")
            
            val jobs = idsToDownload.map { id ->
                async {
                    semaphore.withPermit {
                        try {
                            val detail = fetchUrlWithRetry("$baseUrl/hadeeths/one/?id=$id&language=$lang")
                            if (detail != null) {
                                val h = JSONObject(detail)
                                synchronized(mainInsert) {
                                    mainInsert.setString(1, h.optString("id", id))
                                    mainInsert.setString(2, h.optString("title", ""))
                                    mainInsert.setString(3, h.optString("hadeeth", ""))
                                    mainInsert.setString(4, h.optString("explanation", ""))
                                    mainInsert.setString(5, h.optString("attribution", ""))
                                    mainInsert.setString(6, h.optString("grade", ""))
                                    mainInsert.setString(7, h.optString("reference", ""))
                                    mainInsert.setString(8, lang)
                                    mainInsert.executeUpdate()
                                }
                                successCount++
                            } else {
                                synchronized(failedIds) { failedIds.add(id) }
                                failedCount++
                            }
                        } catch (e: Exception) {
                            println("\n     Err ID $id: ${e.message}")
                            synchronized(failedIds) { failedIds.add(id) }
                            failedCount++
                        }
                    }
                }
            }

            var completed = 0
            jobs.forEach { 
                it.await() 
                completed++
                if (completed % 20 == 0 || completed == idsToDownload.size) {
                    print("\r     Progress: $completed/${idsToDownload.size}")
                    connection.commit()
                }
            }
            
            connection.commit()
            println("\n  -> File database: $dbFileName")
            println("     === RINGKASAN AKHIR [$lang] ===")
            println("     - Total hadits ditemukan   : $totalFound")
            println("     - Sudah ada (Skipped)      : $skippedCount")
            println("     - Berhasil didownload      : $successCount")
            println("     - Gagal                    : $failedCount")
            
            if (failedIds.isNotEmpty()) {
                println("     - Daftar ID Gagal          : ${failedIds.joinToString(", ")}")
            }
            println("     ===============================")
        }
    }

    private suspend fun fetchUrlWithRetry(url: String, retries: Int = 3): String? {
        repeat(retries) { attempt ->
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return response.body?.string()
                    } else if (response.code == 404) {
                        return null
                    } else {
                        throw Exception("HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                if (attempt < retries - 1) {
                    val waitTime = 2000L * (attempt + 1)
                    delay(waitTime)
                } else {
                    return null
                }
            }
        }
        return null
    }
}
