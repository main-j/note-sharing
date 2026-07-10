#!/usr/bin/env python3
"""Build PDF bytes with CJK font support for seed notes."""

from __future__ import annotations

import re
from pathlib import Path

from fpdf import FPDF

FONT_CANDIDATES = [
    Path(r"C:\Windows\Fonts\msyh.ttc"),
    Path(r"C:\Windows\Fonts\msyhbd.ttc"),
    Path(r"C:\Windows\Fonts\simhei.ttf"),
    Path(r"C:\Windows\Fonts\simsun.ttc"),
    Path(r"C:\Windows\Fonts\arialuni.ttf"),
    Path("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"),
    Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
]

_resolved_font: Path | None = None


def resolve_cjk_font() -> Path:
    global _resolved_font
    if _resolved_font is not None:
        return _resolved_font
    for candidate in FONT_CANDIDATES:
        if candidate.exists():
            _resolved_font = candidate
            return candidate
    raise RuntimeError(
        "No CJK font found. Install a Chinese font (e.g. Microsoft YaHei / SimHei) "
        "or place a .ttf/.ttc under C:\\Windows\\Fonts"
    )


def _clean_pdf_line(text: str, max_len: int = 120) -> str:
    line = re.sub(r"^#+\s*", "", text).strip()
    line = re.sub(r"[*_`>\-]", "", line).strip()
    return line[:max_len]


def paragraphs_from_markdown(markdown: str, max_lines: int = 24) -> list[str]:
    lines: list[str] = []
    in_code = False
    for raw in markdown.splitlines():
        if raw.strip().startswith("```"):
            in_code = not in_code
            continue
        if in_code:
            continue
        cleaned = _clean_pdf_line(raw)
        if cleaned:
            lines.append(cleaned)
        if len(lines) >= max_lines:
            break
    return lines or ["计算机学习笔记"]


def build_pdf_bytes(title: str, paragraphs: list[str]) -> bytes:
    font_path = resolve_cjk_font()
    pdf = FPDF(format="A4")
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.add_page()
    pdf.add_font("CJK", "", str(font_path))
    pdf.set_font("CJK", size=16)
    pdf.multi_cell(0, 10, title)
    pdf.ln(4)
    pdf.set_font("CJK", size=12)
    for paragraph in paragraphs:
        pdf.multi_cell(0, 8, paragraph)
        pdf.ln(2)
    out = pdf.output()
    return out if isinstance(out, (bytes, bytearray)) else out.encode("latin-1")
