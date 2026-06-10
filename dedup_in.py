import re
import os

path = 'app/src/main/res/values-in/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
seen_keys = set()
pattern = re.compile(r'name="([^"]+)"')

for line in lines:
    match = pattern.search(line)
    if match:
        key = match.group(1)
        if key in seen_keys:
            print(f"Removing duplicate key: {key}")
            continue
        seen_keys.add(key)
    new_lines.append(line)

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
