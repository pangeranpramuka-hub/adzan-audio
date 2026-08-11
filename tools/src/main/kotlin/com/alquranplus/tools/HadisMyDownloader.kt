package com.alquranplus.tools

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * TOOL RESUME-ABLE & MULTI-KEY UNTUK DOWNLOAD HADIS
 */
class HadisMyDownloader {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) = runBlocking {
            HadisMyDownloader().run()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://service.hadis.my/api/v1"
    
    // MASUKKAN SEMUA API KEY ANDA DI SINI UNTUK ROTASI OTOMATIS
    private val apiKeys = mutableListOf(
        "HADIS_DFB5D0E4-579E-4C32-BDE0-6062C4EC4024",
        "HADIS_341796EF-C60A-433D-89EB-93F4C0679976",
        "HADIS_F88DF64F-A16B-42ED-BC45-18E6EF3A962B",
        "HADIS_8073B824-DEE1-42EF-AABF-FA27253F4F6C",
        "HADIS_8A0237A5-A54A-4C54-8EDE-19944B409971",
        "HADIS_41B4792F-11E2-489B-AD2A-9346BD44B8B2",
        "HADIS_381AA34B-3B60-4B21-9FD7-DE1ED180ADD5",
        "HADIS_5AAC2B59-19D2-40E9-82D1-B24A87095993",
        "HADIS_1C3C3B7B-C08D-4CEC-8DE2-FADA65E3AAD2",
        "HADIS_9C03F317-A976-4BCC-ACCB-2952AE42819C",
        "HADIS_AE39D87A-361C-47DF-996C-17D6BFA24E52",
        "HADIS_2B9196B0-81BB-47AA-8627-71DCFDE092F6",
        "HADIS_5FCC8F23-ED8B-4371-B505-FD92C74B5A28",
        "HADIS_DEDA6B30-981C-4CA0-9A72-522069887C87",
        "HADIS_664CBDD3-073C-4AA3-98A9-2A271C7D5E43",
        "HADIS_6E616228-E8AB-4C16-A4AB-1D2A375C6A1E",
        "HADIS_A0870D90-4E65-4B71-9498-DCD10142D27C",
        "HADIS_628BC87A-12DA-4224-9886-BAD3EABF1FD0",
        "HADIS_FE9B1456-5FC2-4390-8B09-AACCD5B633CE",
        "HADIS_6D5EBE4E-68E4-4962-B40A-CC0BCBA25F60",
        "HADIS_7A4F6230-DD6D-46DE-A900-E37F53C03297",
        "HADIS_34070486-DEB9-4EA7-BB8B-5B519CB34AD1",
        "HADIS_2D0986AD-EEBC-4110-8C40-2064A0B3D901",
        "HADIS_7D0E72BF-A951-4DC0-A617-44BBA87AF830"
    )
    private var currentKeyIndex = 0
    private val semaphore = Semaphore(2) // Kecepatan rendah agar tidak cepat kena limit

    suspend fun run() = coroutineScope {
        println("=== Hadis.My Downloader - Mode Lanjut (Resume) ===")
        
        val targets = listOf(
            mapOf("name" to "Sahih Bukhari", "slug" to "bukhari", "file" to "hadis_bukhari.json"),
            mapOf("name" to "Sahih Muslim", "slug" to "muslim", "file" to "hadis_muslim.json"),
            mapOf("name" to "Sunan Abu Dawud", "slug" to "abu-daud", "file" to "hadis_abudaud.json"),
            mapOf("name" to "Sunan Tirmidzi", "slug" to "tirmidzi", "file" to "hadis_tirmidzi.json"),
            mapOf("name" to "Sunan An-Nasai", "slug" to "nasai", "file" to "hadis_nasai.json"),
            mapOf("name" to "Sunan Ibnu Majah", "slug" to "ibnu-majah", "file" to "hadis_ibnumajah.json"),
            mapOf("name" to "Sunan Ad-Darimi", "slug" to "darimi", "file" to "hadis_darimi.json"),
            mapOf("name" to "Muwatta Malik", "slug" to "malik", "file" to "hadis_malik.json"),
            mapOf("name" to "Musnad Ahmad", "slug" to "ahmad", "file" to "hadis_ahmad.json")
        )

        for (target in targets) {
            val name = target["name"]!!
            val slug = target["slug"]!!
            val fileName = target["file"]!!
            
            println("\n> Memproses Koleksi: $name")
            
            // Muat data lama jika ada (Resume)
            val existingData = loadExistingData(fileName)
            if (existingData.isNotEmpty()) {
                println("  [RESUME] Ditemukan ${existingData.size} hadis lama di $fileName. Melanjutkan...")
            }

            val endpoint = findCorrectEndpoint(slug)
            if (endpoint != null) {
                val collectionHadiths = downloadAllHadiths(endpoint, slug, existingData)
                if (collectionHadiths.isNotEmpty()) {
                    saveToJson(collectionHadiths, fileName)
                }
            } else {
                println(" [!] Tidak dapat menemukan endpoint valid untuk $slug (Mungkin semua Key limit)")
            }
        }

        println("\n\n=== SELESAI SEMUA ===")
    }

    private fun loadExistingData(fileName: String): List<JSONObject> {
        val file = File(fileName)
        if (!file.exists()) return emptyList()
        return try {
            val jsonArray = JSONArray(file.readText())
            val list = mutableListOf<JSONObject>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getJSONObject(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun findCorrectEndpoint(slug: String): String? {
        val potentials = listOf("$baseUrl/collections/$slug/hadis", "$baseUrl/collections/$slug")
        for (url in potentials) {
            val resp = fetchJson(url)
            if (resp != null && resp.optBoolean("success", false)) return url
        }
        return null
    }

    private suspend fun downloadAllHadiths(url: String, slug: String, existing: List<JSONObject>): List<JSONObject> = coroutineScope {
        val hadiths = existing.toMutableList()
        val firstPage = fetchJson("$url?page=1") ?: return@coroutineScope hadiths

        val dataObj = firstPage.optJSONObject("data")
        val meta = firstPage.optJSONObject("meta") ?: dataObj?.optJSONObject("meta") ?: firstPage.optJSONObject("pagination")
        val totalPages = meta?.optInt("last_page", 1) ?: 1
        val perPage = 20 // Server default
        
        val startPage = (hadiths.size / perPage) + 1
        
        if (startPage > totalPages) {
            println("  [INFO] Koleksi $slug sudah lengkap (${hadiths.size} hadis).")
            return@coroutineScope hadiths
        }

        println("  [INFO] Progress: $startPage s/d $totalPages halaman.")

        val jobs = (startPage..totalPages).map { p ->
            async {
                semaphore.withPermit {
                    val res = fetchJson("$url?page=$p")
                    if (res != null) {
                        val pageData = extractData(res)
                        synchronized(hadiths) {
                            for (j in 0 until pageData.length()) {
                                // Hindari duplikat ID jika ada
                                hadiths.add(pageData.getJSONObject(j))
                            }
                        }
                        print("\r  Progress [$slug]: ${hadiths.size} hadis terunduh...")
                    }
                }
            }
        }
        jobs.awaitAll()
        
        println("\r  Selesai mengunduh ${hadiths.size} hadis dari $slug.             ")
        hadiths
    }

    private fun extractData(json: JSONObject): JSONArray {
        val directDataArr = json.optJSONArray("data")
        if (directDataArr != null) return directDataArr
        val dataObj = json.optJSONObject("data") ?: return JSONArray()
        return dataObj.optJSONArray("hadiths") ?: dataObj.optJSONArray("hadis") ?: dataObj.optJSONArray("data") ?: JSONArray()
    }

    private suspend fun fetchJson(url: String): JSONObject? {
        while (currentKeyIndex < apiKeys.size) {
            val apiKey = apiKeys[currentKeyIndex]
            val request = Request.Builder()
                .url(url)
                .addHeader("X-API-Key", apiKey)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()

            val result = withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            JSONObject(response.body?.string() ?: "")
                        } else if (response.code == 429) {
                            println("\n [!] Key #${currentKeyIndex + 1} LIMIT. Mencoba Key berikutnya...")
                            currentKeyIndex++
                            null
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    delay(2000)
                    null
                }
            }
            if (result != null || currentKeyIndex >= apiKeys.size) return result
        }
        return null
    }

    private fun saveToJson(list: List<JSONObject>, fileName: String) {
        try {
            val jsonArray = JSONArray()
            list.forEach { jsonArray.put(it) }
            File(fileName).writeText(jsonArray.toString(4))
            println("SIMPAN BERHASIL: $fileName (${list.size} hadis)")
        } catch (e: Exception) {
            println("[ERROR] Gagal simpan: ${e.message}")
        }
    }
}
