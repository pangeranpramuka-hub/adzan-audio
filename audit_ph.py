import re
import os

def get_entries(path):
    if not os.path.exists(path): return {}
    content = open(path, 'r', encoding='utf-8').read()
    return dict(re.findall(r'<string name="([^"]+)".*?>(.*?)</string>', content, re.DOTALL))

master_path = 'app/src/main/res/values/strings.xml'
master = get_entries(master_path)

# Regex for Java/Android string format placeholders
ph_pattern = re.compile(r'%(\d+\$)?[-#+ 0,(\[]*\d*(\.\d+)?[diouxXeEfgGaApsbc%]')

langs = [d.replace('values-', '') for d in os.listdir('app/src/main/res') if d.startswith('values-')]

for lang in langs:
    target_path = f'app/src/main/res/values-{lang}/strings.xml'
    target = get_entries(target_path)
    if not target: continue

    errors = []
    for k, mv in master.items():
        if k in target:
            tv = target[k]
            m_ph = ph_pattern.findall(mv)
            t_ph = ph_pattern.findall(tv)
            if len(m_ph) != len(t_ph):
                errors.append((k, mv, tv, len(m_ph), len(t_ph)))

    if errors:
        print(f"--- Language: {lang} ---")
        for k, mv, tv, ml, tl in errors:
            print(f"Key: {k}")
            print(f"  Expected {ml} PH, got {tl}")
            print(f"  Master: {mv.strip()}")
            print(f"  Target: {tv.strip()}")
        print("\n")
