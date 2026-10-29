"""Parse integer SDK attributes from both supported aapt2 XML dump formats."""
import re


def sdk_levels(manifest):
    values = []
    for attribute in ("minSdkVersion", "targetSdkVersion"):
        pattern = rf"\b{attribute}\(0x[0-9a-fA-F]+\)\s*=\s*(?:\(type 0x10\))?\s*(0x[0-9a-fA-F]+|[0-9]+)"
        matches = re.findall(pattern, manifest)
        if len(matches) != 1:
            raise ValueError(f"Expected one compiled manifest attribute: {attribute}")
        raw = matches[0]
        values.append(int(raw, 16 if raw.startswith("0x") else 10))
    return tuple(values)
