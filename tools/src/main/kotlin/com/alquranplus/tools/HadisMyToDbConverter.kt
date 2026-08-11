package com.alquranplus.tools

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.sql.DriverManager

/**
 * TOOL UNTUK KONVERSI JSON HADIS KE SQLITE DATABASE (.db)
 * 
 * Tool ini akan mengubah 9 file JSON hasil download tadi menjadi file .db 
 * yang kompatibel dengan sistem "HadeethEnc" di aplikasi.
 * Ini membuat aplikasi jadi sangat cepat dan hemat memori.
 */
class HadisMyToDbConverter {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            HadisMyToDbConverter().run()
        }
    }

    private val targetFiles = listOf(
        mapOf("json" to "hadis_bukhari.json", "db" to "hadismy_bukhari.db", "name" to "Sahih Bukhari"),
        mapOf("json" to "hadis_muslim.json", "db" to "hadismy_muslim.db", "name" to "Sahih Muslim"),
        mapOf("json" to "hadis_abudaud.json", "db" to "hadismy_abudaud.db", "name" to "Sunan Abu Daud"),
        mapOf("json" to "hadis_tirmidzi.json", "db" to "hadismy_tirmidzi.db", "name" to "Sunan Tirmidzi"),
        mapOf("json" to "hadis_nasai.json", "db" to "hadismy_nasai.db", "name" to "Sunan Nasai"),
        mapOf("json" to "hadis_ibnumajah.json", "db" to "hadismy_ibnumajah.db", "name" to "Sunan Ibnu Majah"),
        mapOf("json" to "hadis_darimi.json", "db" to "hadismy_darimi.db", "name" to "Sunan Darimi"),
        mapOf("json" to "hadis_malik.json", "db" to "hadismy_malik.db", "name" to "Muwatta Malik"),
        mapOf("json" to "hadis_ahmad.json", "db" to "hadismy_ahmad.db", "name" to "Musnad Ahmad")
    )

    fun run() {
        println("=== Hadis.My JSON to SQLite Converter ===")
        
        val manifestList = mutableListOf<JSONObject>()

        for (target in targetFiles) {
            val jsonPath = target["json"]!!
            val dbPath = target["db"]!!
            val bookName = target["name"]!!

            val jsonFile = File(jsonPath)
            if (!jsonFile.exists()) {
                println("[SKIP] File $jsonPath tidak ditemukan.")
                continue
            }

            println("\n> Mengonversi: $bookName...")
            try {
                val jsonArray = JSONArray(jsonFile.readText())
                createDatabase(dbPath, jsonArray)
                
                val file = File(dbPath)
                val sha256 = calculateSHA256(file)
                
                manifestList.add(JSONObject().apply {
                    put("language_code", "ms")
                    put("language_name", "Bahasa Melayu")
                    put("book_name", bookName)
                    put("file_name", dbPath)
                    put("sha256", sha256)
                    put("file_size", file.length())
                    put("last_updated", java.time.LocalDate.now().toString())
                })
                
                println("  [OK] Tersimpan: $dbPath (${jsonArray.length()} hadis)")
            } catch (e: Exception) {
                println("  [ERR] Gagal mengonversi $bookName: ${e.message}")
            }
        }

        if (manifestList.isNotEmpty()) {
            generateManifest(manifestList)
        }
        
        println("\n=== SEMUA PROSES SELESAI ===")
    }

    private fun createDatabase(dbPath: String, data: JSONArray) {
        val file = File(dbPath)
        if (file.exists()) file.delete()

        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { stmt ->
                // Gunakan skema yang sama dengan HadeethEnc agar kode import di app bisa dipakai ulang
                stmt.executeUpdate("CREATE TABLE hadeeth_enc (id INTEGER PRIMARY KEY, title TEXT, hadeeth TEXT, explanation TEXT, attribution TEXT, grade TEXT, reference TEXT, language TEXT)")
            }

            val pstmt = connection.prepareStatement("INSERT OR IGNORE INTO hadeeth_enc VALUES (?,?,?,?,?,?,?,?)")
            var inserted = 0
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val id = item.optInt("id", i + 1)
                
                pstmt.setInt(1, id)
                pstmt.setString(2, "") // Title kosong
                pstmt.setString(3, item.optString("arab", ""))
                pstmt.setString(4, item.optString("melayu", item.optString("indonesia", "")))
                pstmt.setString(5, "") // Attribution
                pstmt.setString(6, "") // Grade
                pstmt.setString(7, "Sumber: hadis.my")
                pstmt.setString(8, "ms")
                
                val result = pstmt.executeUpdate()
                if (result > 0) inserted++
            }
            connection.commit()
            
            // Jalankan VACUUM untuk mengecilkan ukuran file secara maksimal
            connection.createStatement().use { it.execute("VACUUM") }

            println("  [OK] Tersimpan: $dbPath ($inserted hadis unik dari ${data.length()} total)")
        }
    }

    private fun generateManifest(list: List<JSONObject>) {
        val manifest = JSONObject()
        manifest.put("version", System.currentTimeMillis())
        manifest.put("database_version", "1.0.0")
        manifest.put("minimum_app_version", 1)
        
        val globalConfig = JSONObject()
        globalConfig.put("primary_cdn", "https://raw.githubusercontent.com/USERNAME_ANDA/REPO_NAME/main/")
        globalConfig.put("fallback_url", "https://raw.githubusercontent.com/USERNAME_ANDA/REPO_NAME/main/")
        manifest.put("global_config", globalConfig)
        
        val dbArray = JSONArray()
        list.forEach { dbArray.put(it) }
        manifest.put("databases", dbArray)

        File("manifest_hadismy.json").writeText(manifest.toString(4))
        println("\n> File manifest_hadismy.json berhasil dibuat!")
        println("  Silakan ganti 'USERNAME_ANDA' dan 'REPO_NAME' di dalam file manifest tersebut.")
    }

    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
