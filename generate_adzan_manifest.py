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
    base_dir = "audio"
    folders = ["normal", "subuh"]
    adhans = []

    if not os.path.exists(base_dir):
        print(f"Error: Folder '{base_dir}' tidak ditemukan.")
        return

    for folder in folders:
        folder_path = os.path.join(base_dir, folder)
        if not os.path.exists(folder_path):
            continue

        # Gunakan sorting standar (akan otomatis urut jika pakai nomor seperti 01, 02, dst)
        all_files = sorted([f for f in os.listdir(folder_path) if f.endswith(".mp3")])

        for filename in all_files:
            file_path = os.path.join(folder_path, filename)
            file_size = os.path.getsize(file_path)
            sha256 = calculate_sha256(file_path)

            # ID tetap menggunakan nama file asli (tanpa ekstensi)
            file_id = os.path.splitext(filename)[0].lower()

            # Untuk Nama Tampilan, kita bersihkan angka di depan (misal: "01_madinah" jadi "Madinah")
            display_name = os.path.splitext(filename)[0]
            display_name = re.sub(r'^\d+[._-]*', '', display_name) # Hapus angka dan separator di depan
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

    with open("manifest.json", "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    print(f"Berhasil membuat manifest.json dengan {len(adhans)} file audio.")
    print("Urutan file diatur berdasarkan nama file di folder (alphabetical/numerical).")

if __name__ == "__main__":
    generate_manifest()
