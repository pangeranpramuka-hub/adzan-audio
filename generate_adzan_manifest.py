import os
import json
import hashlib
import re
from datetime import datetime

def calculate_sha256(file_path):
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

def generate_manifest():
    # Folder tempat menyimpan audio
    base_dir = "audio"
    folders = ["normal", "subuh"]
    adhans = []

    if not os.path.exists(base_dir):
        print(f"Error: Folder '{base_dir}' tidak ditemukan.")
        return

    for folder in folders:
        folder_path = os.path.join(base_dir, folder)
        if not os.path.exists(folder_path):
            print(f"Warning: Folder '{folder_path}' tidak ditemukan, melewati...")
            continue

        # Urutkan berdasarkan nama file (otomatis urut jika pakai nomor 01, 02, dst)
        all_files = sorted([f for f in os.listdir(folder_path) if f.endswith(".mp3")])

        for filename in all_files:
            file_path = os.path.join(folder_path, filename)
            file_size = os.path.getsize(file_path)
            sha256 = calculate_sha256(file_path)
            
            # ID menggunakan nama file asli tanpa ekstensi (untuk referensi unik)
            file_id = os.path.splitext(filename)[0].lower()
            
            # Nama Tampilan: Menghilangkan angka dan separator di depan
            # Contoh: "01_madinah_normal" -> "Madinah Normal"
            display_name = os.path.splitext(filename)[0]
            display_name = re.sub(r'^\d+[._-]*', '', display_name) 
            display_name = display_name.replace("_", " ").title().strip()

            adhans.append({
                "id": file_id,
                "name": display_name,
                "type": folder,
                "file": f"{folder}/{filename}",
                "size": file_size,
                "sha256": sha256
            })

    manifest = {
        "version": 1,
        "last_updated": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "adhans": adhans
    }

    # Simpan hasil ke manifest.json
    with open("manifest.json", "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    
    print("-" * 50)
    print(f"BERHASIL! manifest.json dibuat dengan {len(adhans)} file audio.")
    print(f"Update terakhir: {manifest['last_updated']}")
    print("-" * 50)

if __name__ == "__main__":
    generate_manifest()