---
name: file-converter
description: Convert files between formats including CSV, JSON, YAML, XML, Markdown, and image formats.
---

# File Converter

Convert between data, document, and image formats. One-liners for each conversion pair.

## Tool Map

| Domain | Tool | Install |
|---|---|---|
| CSV/JSON/Excel/Parquet | `pandas` | `pip install pandas openpyxl pyarrow` |
| YAML | `pyyaml` | `pip install pyyaml` |
| XML ↔ dict | `xmltodict` | `pip install xmltodict` |
| Any doc format ↔ any | **pandoc** (CLI) | `apt install pandoc` or `pip install pypandoc_binary` |
| Markdown → HTML | `markdown` | `pip install markdown` |
| HTML → Markdown | `markdownify` | `pip install markdownify` |
| .docx read/write | `python-docx` | `pip install python-docx` |
| PDF → text/tables | `pdfplumber` | `pip install pdfplumber` |
| PDF → images | `pdf2image` | `pip install pdf2image` + `apt install poppler-utils` |
| PDF manipulation | `pypdf` | `pip install pypdf` |
| Images | `Pillow` | `pip install Pillow` |
| SVG → PNG | `cairosvg` | `pip install cairosvg` |
| HEIC → JPG | `pillow-heif` | `pip install pillow-heif` |

## Data Formats

```python
import pandas as pd, json, yaml, xmltodict

# --- CSV ↔ JSON ---
pd.read_csv("in.csv").to_json("out.json", orient="records", indent=2)
pd.read_json("in.json").to_csv("out.csv", index=False)

# --- CSV → Excel / Excel → CSV ---
pd.read_csv("in.csv").to_excel("out.xlsx", index=False, engine="openpyxl")
pd.read_excel("in.xlsx", sheet_name="Sheet1").to_csv("out.csv", index=False)

# All sheets: pd.read_excel("in.xlsx", sheet_name=None) → dict of DataFrames

# --- CSV → Parquet (columnar, compressed) ---
pd.read_csv("in.csv").to_parquet("out.parquet", engine="pyarrow", compression="snappy")

# --- YAML ↔ JSON ---
data = yaml.safe_load(open("in.yaml"))          # ALWAYS safe_load, never load()
json.dump(data, open("out.json", "w"), indent=2)
yaml.safe_dump(json.load(open("in.json")), open("out.yaml", "w"), sort_keys=False)

# --- XML ↔ JSON ---
data = xmltodict.parse(open("in.xml").read())
json.dump(data, open("out.json", "w"), indent=2)
open("out.xml", "w").write(xmltodict.unparse(data, pretty=True))

# --- JSONL (one JSON object per line) ---
pd.read_json("in.jsonl", lines=True).to_csv("out.csv", index=False)

```

**Encoding gotchas:**

- `pd.read_csv("f.csv", encoding="utf-8-sig")` strips the BOM that Excel inserts
- Auto-detect: `import chardet; enc = chardet.detect(open("f.csv","rb").read())["encoding"]`
- CSV delimiter sniffing: `pd.read_csv("f.csv", sep=None, engine="python")`

**Nested JSON → flat CSV:**

```python
pd.json_normalize(data, sep=".").to_csv("out.csv", index=False)  # {"a":{"b":1}} → column "a.b"

```

## Document Formats — pandoc is the Swiss Army knife

```bash

# Markdown → PDF (requires LaTeX: apt install texlive-xetex)
pandoc input.md -o output.pdf --pdf-engine=xelatex

# Markdown → DOCX
pandoc input.md -o output.docx

# DOCX → Markdown (extracts images to ./media/)
pandoc input.docx -o output.md --extract-media=.

# HTML → Markdown
pandoc input.html -o output.md -t gfm

# Any → Any (pandoc supports ~40 formats)
pandoc -f docx -t rst input.docx -o output.rst

```

```python

# From Python
import pypandoc
pypandoc.convert_file("in.md", "docx", outputfile="out.docx")

```

**Without pandoc (pure Python):**

```python

# Markdown → HTML
import markdown
html = markdown.markdown(open("in.md").read(), extensions=["tables", "fenced_code", "toc"])

# HTML → Markdown
from markdownify import markdownify
md = markdownify(html, heading_style="ATX")  # ATX = # headers, not underlines

```

## PDF Operations

```python

# --- Extract text + tables ---
import pdfplumber
with pdfplumber.open("in.pdf") as pdf:
    text = "\n".join(p.extract_text() or "" for p in pdf.pages)
    tables = pdf.pages[0].extract_tables()  # list of list-of-rows

# --- PDF → images (one PNG per page) ---
from pdf2image import convert_from_path
for i, img in enumerate(convert_from_path("in.pdf", dpi=200)):
    img.save(f"page_{i+1}.png")

# --- Merge / split / rotate ---
from pypdf import PdfReader, PdfWriter
writer = PdfWriter()
for path in ["a.pdf", "b.pdf"]:
    for page in PdfReader(path).pages:
        writer.add_page(page)
writer.write("merged.pdf")

# Extract pages 2–5
reader = PdfReader("in.pdf")
writer = PdfWriter()
for p in reader.pages[1:5]:
    writer.add_page(p)
writer.write("pages_2-5.pdf")

```

**PDF gotchas:**

- `pdf2image` needs `poppler-utils` installed system-wide (not a pip package)
- Scanned PDFs have no text layer — pdfplumber returns `None`. Use `pytesseract` OCR on pdf2image output.
- `PyPDF2` is deprecated → use `pypdf` (same API, maintained fork)

## Image Formats

```python
from PIL import Image

# --- Basic conversion ---
Image.open("in.png").convert("RGB").save("out.jpg", quality=90)

# convert("RGB") is REQUIRED: JPEG can't store alpha channel, will raise OSError

# --- WebP (best web format) ---
Image.open("in.jpg").save("out.webp", quality=85, method=6)  # method 0-6, 6=best compression

# --- AVIF (smallest, Pillow 11+) ---
Image.open("in.jpg").save("out.avif", quality=75)

# --- HEIC (iPhone photos) → JPG ---
from pillow_heif import register_heif_opener
register_heif_opener()
Image.open("in.heic").convert("RGB").save("out.jpg", quality=90)

# --- SVG → PNG ---
import cairosvg
cairosvg.svg2png(url="in.svg", write_to="out.png", output_width=1024)

# --- Batch convert directory ---
from pathlib import Path
for p in Path("imgs").glob("*.png"):
    Image.open(p).convert("RGB").save(p.with_suffix(".jpg"), quality=85)

```

**Image gotchas:**

- PNG → JPG: **must** `convert("RGB")` first or transparency crashes the save
- `quality` for PNG is meaningless (lossless) — use `optimize=True, compress_level=9`
- Pillow can't open `.svg` natively — use `cairosvg` or `svglib`
- GIF → MP4 is a video operation: `ffmpeg -i in.gif -pix_fmt yuv420p out.mp4`

## Validation

Always verify output:

```python

# Row count parity
assert len(pd.read_csv("out.csv")) == len(pd.read_json("in.json"))

# JSON well-formed
json.load(open("out.json"))

# Image opens
Image.open("out.jpg").verify()

```
