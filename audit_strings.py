import re
import os
import glob

def get_entries(path):
    if not os.path.exists(path):
        return None
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Simple regex for name="..."
    strings = re.findall(r'<string name="([^"]+)"(.*?)>(.*?)</string>', content, re.DOTALL)
    arrays = re.findall(r'<string-array name="([^"]+)"(.*?)>(.*?)</string-array>', content, re.DOTALL)
    plurals = re.findall(r'<plurals name="([^"]+)"(.*?)>(.*?)</plurals>', content, re.DOTALL)

    return {
        'strings': {name: (attr, val) for name, attr, val in strings},
        'arrays': {name: (attr, val) for name, attr, val in arrays},
        'plurals': {name: (attr, val) for name, attr, val in plurals}
    }

def count_placeholders(text):
    # Regex for %s, %d, %1$s, etc.
    return re.findall(r'%(\d+\$)?[-#+ 0,(\[]*\d*(\.\d+)?[diouxXeEfgGaApsbc%]', text)

master_path = 'app/src/main/res/values/strings.xml'
master = get_entries(master_path)

res_dir = 'app/src/main/res/values-*'
lang_dirs = glob.glob(res_dir)

report = []

for lang_dir in lang_dirs:
    lang = lang_dir.split('values-')[-1]
    path = os.path.join(lang_dir, 'strings.xml')
    if not os.path.exists(path):
        continue

    actual = get_entries(path)

    missing_strings = [k for k in master['strings'] if k not in actual['strings']]
    placeholder_errors = []

    for name, (attr, master_val) in master['strings'].items():
        if name in actual['strings']:
            actual_val = actual['strings'][name][1]
            master_ph = count_placeholders(master_val)
            actual_ph = count_placeholders(actual_val)

            if len(master_ph) != len(actual_ph):
                placeholder_errors.append(f"{name}: expected {len(master_ph)}, got {len(actual_ph)}")
            else:
                # Check types (roughly)
                for m, a in zip(master_ph, actual_ph):
                    if m[0] != a[0]: # Positional markers
                         placeholder_errors.append(f"{name}: positional mismatch {m[0]} vs {a[0]}")

    missing_arrays = [k for k in master['arrays'] if k not in actual['arrays']]
    missing_plurals = [k for k in master['plurals'] if k not in actual['plurals']]

    report.append({
        'lang': lang,
        'string_count': len(actual['strings']),
        'missing_strings': len(missing_strings),
        'missing_arrays': len(missing_arrays),
        'missing_plurals': len(missing_plurals),
        'placeholder_errors': len(placeholder_errors),
        'status': 'OK' if not missing_strings and not missing_arrays and not missing_plurals and not placeholder_errors else 'ERROR'
    })

print("Language | Strings | Missing | Array Missing | Plural Missing | PH Errors | Status")
print("---------|---------|---------|---------------|----------------|-----------|-------")
for r in report:
    print(f"{r['lang']:8} | {r['string_count']:7} | {r['missing_strings']:7} | {r['missing_arrays']:13} | {r['missing_plurals']:14} | {r['placeholder_errors']:9} | {r['status']}")
