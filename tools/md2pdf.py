"""Render this assessment's Markdown as a printable PDF.

    python tools/md2pdf.py ASSESSMENT.md Assessment-Questions.pdf
    python tools/md2pdf.py SOLUTIONS.md Assessment-Solutions.pdf "... - Solutions"

The optional third argument is the running title in the page footer.

Needs reportlab (`pip install reportlab`) and Arial + Consolas, which ship with
Windows. On macOS or Linux, point FONT_DIR and the filenames below at any
Unicode TTF family you have (DejaVu Sans / DejaVu Sans Mono work).

It handles the Markdown subset these documents use: ATX headings, paragraphs,
bullet and numbered lists, pipe tables with alignment, fenced code blocks,
blockquotes, horizontal rules, and inline bold / italic / code. Each `##`
section starts a new page, except one that directly follows a `#` heading --
that way a part title shares a page with its first section instead of sitting
alone on a near-empty one. A `<!-- pagebreak -->` line forces a break anyway;
any other HTML comment is dropped.
"""

import html
import os
import re
import sys

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    HRFlowable,
    KeepTogether,
    PageBreak,
    PageTemplate,
    Paragraph,
    Preformatted,
    Spacer,
    Table,
    TableStyle,
)

SRC = sys.argv[1]
DEST = sys.argv[2]
RUNNING_TITLE = sys.argv[3] if len(sys.argv) > 3 \
    else "Software Engineering Assessment — Java"

FONT_DIR = r"C:\Windows\Fonts"
BODY, BODY_B, BODY_I, BODY_BI = "Body", "Body-B", "Body-I", "Body-BI"
MONO, MONO_B = "Mono", "Mono-B"

for name, filename in [
    (BODY, "arial.ttf"),
    (BODY_B, "arialbd.ttf"),
    (BODY_I, "ariali.ttf"),
    (BODY_BI, "arialbi.ttf"),
    (MONO, "consola.ttf"),
    (MONO_B, "consolab.ttf"),
]:
    pdfmetrics.registerFont(TTFont(name, os.path.join(FONT_DIR, filename)))

pdfmetrics.registerFontFamily(BODY, normal=BODY, bold=BODY_B, italic=BODY_I, boldItalic=BODY_BI)
pdfmetrics.registerFontFamily(MONO, normal=MONO, bold=MONO_B, italic=MONO, boldItalic=MONO_B)

INK = colors.HexColor("#15171c")
MUTED = colors.HexColor("#5c6370")
ACCENT = colors.HexColor("#1d4ed8")
RULE = colors.HexColor("#d3d7de")
CODE_BG = colors.HexColor("#f5f6f8")
HEAD_BG = colors.HexColor("#eceef2")
ZEBRA = colors.HexColor("#fafbfc")
QUOTE_BG = colors.HexColor("#fdf6e6")
QUOTE_BAR = colors.HexColor("#d9a520")

PAGE = A4
MARGIN_X = 18 * mm
MARGIN_TOP = 18 * mm
MARGIN_BOTTOM = 18 * mm
CONTENT_W = PAGE[0] - 2 * MARGIN_X

S = {
    "h1": ParagraphStyle("h1", fontName=BODY_B, fontSize=19, leading=23, textColor=INK,
                         spaceBefore=0, spaceAfter=2),
    "h2": ParagraphStyle("h2", fontName=BODY_B, fontSize=13.5, leading=17, textColor=ACCENT,
                         spaceBefore=14, spaceAfter=5),
    "h3": ParagraphStyle("h3", fontName=BODY_B, fontSize=11, leading=14, textColor=INK,
                         spaceBefore=11, spaceAfter=3),
    "body": ParagraphStyle("body", fontName=BODY, fontSize=9.8, leading=14.2, textColor=INK,
                           spaceBefore=0, spaceAfter=7, alignment=TA_LEFT),
    "bullet": ParagraphStyle("bullet", fontName=BODY, fontSize=9.8, leading=14.2, textColor=INK,
                             spaceBefore=0, spaceAfter=4, leftIndent=13, bulletIndent=2),
    "quote": ParagraphStyle("quote", fontName=BODY, fontSize=9.4, leading=13.6, textColor=INK,
                            spaceBefore=0, spaceAfter=0),
    "code": ParagraphStyle("code", fontName=MONO, fontSize=8.4, leading=11.6, textColor=INK,
                           spaceBefore=0, spaceAfter=0),
    "th": ParagraphStyle("th", fontName=BODY_B, fontSize=9, leading=12.4, textColor=INK),
    "thr": ParagraphStyle("thr", fontName=BODY_B, fontSize=9, leading=12.4, textColor=INK,
                          alignment=TA_RIGHT),
    "td": ParagraphStyle("td", fontName=BODY, fontSize=9, leading=12.4, textColor=INK),
    "tdr": ParagraphStyle("tdr", fontName=BODY, fontSize=9, leading=12.4, textColor=INK,
                          alignment=TA_RIGHT),
}

# Box-drawing and the arrowhead are absent from Arial/Consolas; the diagram
# reads the same in ASCII.
GLYPH_FIX = {"\u2500": "-", "\u25b6": ">", "\u2212": "\u2013"}


def fix_glyphs(text):
    for bad, good in GLYPH_FIX.items():
        text = text.replace(bad, good)
    return text


def inline(text):
    """Markdown inline formatting -> reportlab mini-HTML."""
    text = fix_glyphs(text)
    spans = []

    def stash(match):
        spans.append(match.group(1))
        return "\x00%d\x00" % (len(spans) - 1)

    text = re.sub(r"`([^`]+)`", stash, text)
    text = html.escape(text, quote=False)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"(?<![\w*])\*([^*\n]+?)\*(?![\w*])", r"<i>\1</i>", text)

    def restore(match):
        code = html.escape(spans[int(match.group(1))], quote=False)
        return ('<font face="%s" size="8.8" backColor="#eef0f4">\u2009%s\u2009</font>'
                % (MONO, code))

    return re.sub(r"\x00(\d+)\x00", restore, text)


def code_block(lines):
    body = Preformatted("\n".join(fix_glyphs(l) for l in lines) or " ", S["code"])
    table = Table([[body]], colWidths=[CONTENT_W])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), CODE_BG),
        ("BOX", (0, 0), (-1, -1), 0.5, RULE),
        ("LINEBEFORE", (0, 0), (0, -1), 2.2, ACCENT),
        ("LEFTPADDING", (0, 0), (-1, -1), 9),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ]))
    return [table, Spacer(1, 8)]


def quote_block(lines):
    paragraphs = [Paragraph(inline(l), S["quote"]) for l in " ".join(lines).split("\n")]
    table = Table([[paragraphs]], colWidths=[CONTENT_W])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), QUOTE_BG),
        ("LINEBEFORE", (0, 0), (0, -1), 2.2, QUOTE_BAR),
        ("LEFTPADDING", (0, 0), (-1, -1), 9),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ]))
    return [table, Spacer(1, 8)]


def split_row(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def table_block(rows):
    header = split_row(rows[0])
    aligns = ["right" if c.strip().endswith(":") else "left" for c in split_row(rows[1])]
    body = [split_row(r) for r in rows[2:]]
    cols = len(header)
    aligns = (aligns + ["left"] * cols)[:cols]

    def cell(text, is_header, align):
        key = ("th" if is_header else "td") + ("r" if align == "right" else "")
        return Paragraph(inline(text), S[key])

    data = [[cell(c, True, aligns[i]) for i, c in enumerate(header)]]
    for row in body:
        row = (row + [""] * cols)[:cols]
        data.append([cell(c, False, aligns[i]) for i, c in enumerate(row)])

    # Width each column by its widest raw cell, then scale to the text column.
    weights = []
    for i in range(cols):
        longest = max([len(header[i])] + [len(r[i]) if i < len(r) else 0 for r in body])
        weights.append(max(longest, 4) ** 0.78)
    total = sum(weights)
    widths = [CONTENT_W * w / total for w in weights]

    table = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
    style = [
        ("BACKGROUND", (0, 0), (-1, 0), HEAD_BG),
        ("LINEBELOW", (0, 0), (-1, 0), 0.7, RULE),
        ("BOX", (0, 0), (-1, -1), 0.5, RULE),
        ("INNERGRID", (0, 0), (-1, -1), 0.3, RULE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 4.5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4.5),
    ]
    for i in range(1, len(data)):
        if i % 2 == 0:
            style.append(("BACKGROUND", (0, i), (-1, i), ZEBRA))
    table.setStyle(TableStyle(style))
    return [table, Spacer(1, 9)]


def parse(markdown):
    lines = markdown.replace("\r\n", "\n").split("\n")
    story = []
    i = 0
    seen_h1 = False
    # True when the next flowable already lands at the top of a page, so a
    # following `##` must not break again: right after a `#` heading (the part
    # title then shares a page with its first section instead of sitting alone
    # on a near-empty one) and right after a forced break.
    fresh_page = False

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if stripped.startswith("```"):
            block = []
            i += 1
            while i < len(lines) and not lines[i].strip().startswith("```"):
                block.append(lines[i])
                i += 1
            i += 1
            story += code_block(block)
            continue

        if not stripped:
            i += 1
            continue

        # `<!-- pagebreak -->` forces one; any other HTML comment is dropped.
        if stripped.startswith("<!--") and stripped.endswith("-->"):
            if stripped[4:-3].strip() == "pagebreak":
                story.append(PageBreak())
                fresh_page = True
            i += 1
            continue

        if stripped == "---":
            story.append(Spacer(1, 2))
            story.append(HRFlowable(width="100%", thickness=0.6, color=RULE,
                                    spaceBefore=2, spaceAfter=10))
            i += 1
            continue

        heading = re.match(r"^(#{1,3})\s+(.*)$", stripped)
        if heading:
            level, text = len(heading.group(1)), heading.group(2)
            if level == 1:
                if seen_h1:
                    story.append(PageBreak())
                seen_h1 = True
                story.append(Paragraph(inline(text), S["h1"]))
                story.append(HRFlowable(width="100%", thickness=1.4, color=ACCENT,
                                        spaceBefore=4, spaceAfter=12))
                fresh_page = True
            elif level == 2:
                if story and not fresh_page and not isinstance(story[-1], PageBreak):
                    story.append(PageBreak())
                fresh_page = False
                story.append(Paragraph(inline(text), S["h2"]))
            else:
                story.append(Paragraph(inline(text), S["h3"]))
            i += 1
            continue

        if stripped.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(lines[i])
                i += 1
            if len(rows) >= 2:
                story += table_block(rows)
            continue

        if stripped.startswith(">"):
            block = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                block.append(lines[i].strip()[1:].strip())
                i += 1
            story += quote_block(block)
            continue

        item = re.match(r"^(\s*)(?:([-*])|(\d+)\.)\s+(.*)$", line)
        if item:
            indent, dash, number, text = item.groups()
            chunks = [text]
            i += 1
            # Fold continuation lines that are indented under the marker.
            while i < len(lines):
                nxt = lines[i]
                if not nxt.strip() or re.match(r"^\s*(?:[-*]|\d+\.)\s+", nxt) \
                        or nxt.strip().startswith(("#", "|", "```", ">")) \
                        or not nxt.startswith(" "):
                    break
                chunks.append(nxt.strip())
                i += 1
            bullet = "%s." % number if number else "\u2022"
            style = ParagraphStyle(
                "li", parent=S["bullet"],
                leftIndent=13 + len(indent) // 2 * 11,
                bulletIndent=2 + len(indent) // 2 * 11,
                bulletFontName=BODY_B if number else BODY,
            )
            story.append(Paragraph(inline(" ".join(chunks)), style, bulletText=bullet))
            continue

        chunks = [stripped]
        i += 1
        while i < len(lines):
            nxt = lines[i].strip()
            if not nxt or nxt.startswith(("#", "|", "```", ">", "---")) \
                    or re.match(r"^(?:[-*]|\d+\.)\s+", nxt):
                break
            chunks.append(nxt)
            i += 1
        story.append(Paragraph(inline(" ".join(chunks)), S["body"]))

    return story


def decorate(canvas, doc):
    canvas.saveState()
    canvas.setFont(BODY, 7.6)
    canvas.setFillColor(MUTED)
    canvas.drawString(MARGIN_X, MARGIN_BOTTOM - 9 * mm, RUNNING_TITLE)
    canvas.drawRightString(PAGE[0] - MARGIN_X, MARGIN_BOTTOM - 9 * mm,
                           "Page %d" % canvas.getPageNumber())
    canvas.setStrokeColor(RULE)
    canvas.setLineWidth(0.4)
    canvas.line(MARGIN_X, MARGIN_BOTTOM - 6.5 * mm,
                PAGE[0] - MARGIN_X, MARGIN_BOTTOM - 6.5 * mm)
    canvas.restoreState()


def build():
    source = open(SRC, encoding="utf-8").read()
    doc = BaseDocTemplate(
        DEST, pagesize=PAGE,
        leftMargin=MARGIN_X, rightMargin=MARGIN_X,
        topMargin=MARGIN_TOP, bottomMargin=MARGIN_BOTTOM,
        title=RUNNING_TITLE, author="Assessment",
        subject="CodeSignal-style Java software engineering assessment",
    )
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="main",
                  leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
    doc.addPageTemplates([PageTemplate(id="page", frames=[frame], onPage=decorate)])
    doc.build(parse(source))
    print("wrote", DEST)


build()
