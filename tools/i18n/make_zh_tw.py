#!/usr/bin/env python3
"""Writes values-zh-rTW from values-zh-rCN: OpenCC s2twp, then Taiwan usage OpenCC misses.

Usage (from the repo root): pip install opencc-python-reimplemented && python3 tools/i18n/make_zh_tw.py

Rerun after changing any zh-CN string, then read the diff: the glossary covers this app's words,
not every phrase.
"""
import re
from pathlib import Path

import opencc

RES = Path("app/src/main/res")

# Mainland terms OpenCC leaves, in this app's own words.
GLOSSARY = [
    ("三陣列", "三數組"),
    ("格里", "格裡"),
    ("賬號", "帳號"),
    ("再幹淨", "再乾淨"),
    ("覆盤", "復盤"),
    ("這周", "本週"),
    ("撤銷", "復原"),
    ("網路訪問", "網路連線"),
    ("開源庫", "開源函式庫"),
    ("開源許可證", "開放原始碼授權條款"),
    ("許可證", "授權條款"),
    ("開源許可", "開放原始碼授權"),
    ("應用語言", "應用程式語言"),
    ("本應用使用", "本 App 使用"),
    ("統計", "數據分析"),
    ("擦除", "清除"),
    ("本子", "筆記本"),
    ("用時", "花費時間"),
]


def convert(text: str, cc: opencc.OpenCC) -> str:
    text = cc.convert(text)
    for old, new in GLOSSARY:
        text = text.replace(old, new)
    # In Taiwan 列 is a row and 行 a column, the reverse of mainland usage.
    return re.sub(r"(\$d )([行列])", lambda m: m.group(1) + ("列" if m.group(2) == "行" else "行"), text)


def main() -> None:
    cc = opencc.OpenCC("s2twp")
    out = RES / "values-zh-rTW"
    out.mkdir(exist_ok=True)
    for source in sorted((RES / "values-zh-rCN").glob("*.xml")):
        (out / source.name).write_text(convert(source.read_text(encoding="utf-8"), cc), encoding="utf-8")


if __name__ == "__main__":
    main()
