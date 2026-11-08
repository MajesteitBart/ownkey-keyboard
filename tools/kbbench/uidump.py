"""Print or locate nodes from a uiautomator XML dump read on stdin.

usage: uidump.py list [filter]   -> one line per node with text/desc
       uidump.py find <text>     -> "x y" centre of first node whose text/desc equals <text>
"""
import re
import sys
import xml.etree.ElementTree as ET


def nodes(xml_text):
    root = ET.fromstring(xml_text)
    for n in root.iter("node"):
        yield n


def centre(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    mode = sys.argv[1]
    data = sys.stdin.read()
    if mode == "list":
        flt = sys.argv[2].lower() if len(sys.argv) > 2 else ""
        for n in nodes(data):
            t = n.get("text") or ""
            d = n.get("content-desc") or ""
            rid = (n.get("resource-id") or "").split("/")[-1]
            if not (t or d):
                continue
            flags = ("C" if n.get("clickable") == "true" else "") + ("K" if n.get("checked") == "true" else "")
            line = "%-40.40r %-30.30r %-24.24s %s %s" % (t, d, rid, n.get("bounds"), flags)
            if flt in line.lower():
                print(line)
    elif mode == "find":
        want = sys.argv[2].lower()
        for n in nodes(data):
            if (n.get("text") or "").lower() == want or (n.get("content-desc") or "").lower() == want:
                print("%d %d" % centre(n))
                return
        sys.exit(1)


if __name__ == "__main__":
    main()
