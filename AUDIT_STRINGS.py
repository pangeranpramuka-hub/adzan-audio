import os
import re
import xml.etree.ElementTree as ET

def audit_strings():
    base_path = 'app/src/main/res/'
    en_file = os.path.join(base_path, 'values/strings.xml')

    if not os.path.exists(en_file):
        print("Base strings.xml not found")
        return

    # Parse base strings
    tree = ET.parse(en_file)
    root = tree.getroot()
    en_strings = {}
    for string in root.findall('string'):
        name = string.get('name')
        translatable = string.get('translatable', 'true')
        if translatable == 'true':
            en_strings[name] = string.text

    locales = [d for d in os.listdir(base_path) if d.startswith('values-')]
    locales.sort()

    print(f"{'Locale':<10} | {'Untranslated Count':<20} | {'Status'}")
    print("-" * 50)

    for locale in locales:
        loc_file = os.path.join(base_path, locale, 'strings.xml')
        if not os.path.exists(loc_file):
            continue

        try:
            loc_tree = ET.parse(loc_file)
            loc_root = loc_tree.getroot()
            loc_strings = {s.get('name'): s.text for s in loc_root.findall('string')}

            untranslated = []
            for name, en_val in en_strings.items():
                if name in loc_strings:
                    loc_val = loc_strings[name]
                    if loc_val == en_val and en_val is not None and len(en_val.strip()) > 0:
                        # Simple check: if it's exactly the same as English
                        untranslated.append(name)

            status = "OK" if not untranslated else f"{len(untranslated)} residues"
            print(f"{locale:<10} | {len(untranslated):<20} | {status}")

            # If many residues, show first few
            # if untranslated:
            #     print(f"   Example: {untranslated[:5]}")

        except Exception as e:
            print(f"{locale:<10} | Error: {str(e)}")

if __name__ == "__main__":
    audit_strings()
