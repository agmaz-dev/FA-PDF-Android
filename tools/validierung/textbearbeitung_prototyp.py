"""Referenz-Prototyp des ContentRewriter-Algorithmus (TJ-Kompensation) zur Validierung."""
import pikepdf, subprocess, re
from reportlab.pdfgen import canvas
from reportlab.pdfbase.pdfmetrics import getFont

# 1) Testbrief erzeugen: Zeichen- und Wortabstand gesetzt, damit die Kompensation wirklich geprüft wird
c = canvas.Canvas("orig.pdf", pagesize=(595, 842))
t = c.beginText(72, 700); t.setFont("Helvetica", 12); t.setCharSpace(0.4); t.setWordSpace(1.5)
t.textLine("Sehr geehrte Frau Muster, der Termin am 15. Mai 2026 bleibt bestehen.")
t.textLine("Bitte bringen Sie alle Unterlagen mit.")
c.drawText(t); c.save()

def words(path):
    out = subprocess.run(["pdftotext", "-bbox", path, "-"], capture_output=True, text=True).stdout
    return [(m.group(5), round(float(m.group(1)),2), round(float(m.group(2)),2)) for m in
            re.finditer(r'xMin="([\d.]+)" yMin="([\d.]+)" xMax="([\d.]+)" yMax="([\d.]+)">([^<]*)</word>', out)]

font = getFont("Helvetica")
def w0(ch): return font.face.getCharWidthNormalized(ch) if hasattr(font.face,'getCharWidthNormalized') else font.stringWidth(ch, 1000)/1000.0

pdf = pikepdf.open("orig.pdf")
page = pdf.pages[0]
ops = pikepdf.parse_content_stream(page)
tfs = tc = tw = 0.0
new_ops = []
removed = False
target = "Mai"
for operands, op in ops:
    name = str(op)
    if name == "Tf": tfs = float(operands[1])
    if name == "Tc": tc = float(operands[0])
    if name == "Tw": tw = float(operands[0])
    if name in ("Tj", "'") and not removed:
        s = bytes(operands[-1])
        idx = s.find(target.encode())
        if idx >= 0:
            remove = set(range(idx, idx + len(target)))
            arr, kept, pending = [], b"", 0.0
            for i, b in enumerate(s):
                ch = chr(b)
                adv = w0(ch) * 1000 + (tc + (tw if ch == " " else 0.0)) * 1000 / tfs
                if i in remove:
                    if kept: arr.append(pikepdf.String(kept)); kept = b""
                    pending -= adv
                else:
                    if pending and not kept: arr.append(pending); pending = 0.0
                    kept += bytes([b])
            if kept: arr.append(pikepdf.String(kept))
            if name == "'": new_ops.append(([], pikepdf.Operator("T*")))
            new_ops.append(([pikepdf.Array(arr)], pikepdf.Operator("TJ")))
            removed = True
            continue
    new_ops.append((operands, op))
page.Contents = pdf.make_stream(pikepdf.unparse_content_stream(new_ops))
pdf.save("removed.pdf")

before, after = words("orig.pdf"), words("removed.pdf")
print("Vorher :", " ".join(w for w,_,_ in before))
print("Nachher:", " ".join(w for w,_,_ in after))
assert all(w != "Mai" for w, _, _ in after), "Wort noch vorhanden!"
# Alle übrigen Wörter müssen exakt an derselben Stelle stehen
bpos = {(w, x, y) for w, x, y in before if w != "Mai"}
apos = {(w, x, y) for w, x, y in after}
moved = bpos - apos
print("Verschobene Wörter:", moved or "keine")
assert not moved
print("OK: Glyphen entfernt, alle übrigen Zeichen unverändert positioniert")

# 2) Neuen Text an derselben Grundlinie setzen (wie TextEditEngine: bestehenden Inhalt in q/Q kapseln)
pdf = pikepdf.open("removed.pdf"); page = pdf.pages[0]
mai = [x for w, x, y in before if w == "Mai"][0]
# Grundlinie der ersten Zeile = 700 (aus der Erzeugung)
old = page.Contents.read_bytes()
page.Contents = pdf.make_stream(b"q\n" + old + b"\nQ\nBT /F1 12 Tf 0.4 Tc 1 0 0 1 %.3f 700 Tm (Juni) Tj ET\n" % mai)
pdf.save("edited.pdf")
txt = subprocess.run(["pdftotext", "-layout", "edited.pdf", "-"], capture_output=True, text=True).stdout
print(txt.strip().splitlines()[0])
assert "15. Juni 2026" in " ".join(txt.split()), "Ersetzung nicht korrekt im Textfluss"
assert "Mai" not in txt
print("OK: Ersetzter Text liegt im Textfluss an der richtigen Stelle, Originalwort ist nicht mehr in der Datei")
