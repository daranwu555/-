"""
總經指標教材 — 資料更新腳本
執行: python update_data.py
會從 FRED 與 Yahoo Finance 下載歷史日資料,輸出到 data/data.js,
網頁 (index.html) 直接讀取這個檔案,不需要伺服器。
只使用 Python 標準函式庫。
"""
import csv
import io
import json
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

START = "2000-01-01"
UA = {"User-Agent": "Mozilla/5.0 (macro-course data updater)"}

# key: (來源, 代號, 說明)
SERIES = {
    "vix":    ("fred", "VIXCLS",       "VIX 30 天隱含波動率"),
    "vix3m":  ("fred", "VXVCLS",       "VIX3M 93 天隱含波動率"),
    "hy":     ("fred", "BAMLH0A0HYM2", "美國高收益債 OAS 利差 (%)"),
    "baa":    ("fred", "BAA10Y",       "Moody's Baa 公司債 − 10 年公債 (%)"),
    "real10": ("fred", "DFII10",       "10 年期 TIPS 實質殖利率 (%)"),
    "ffr":    ("fred", "DFF",          "聯邦基金有效利率 (%)"),
    "gold":   ("yahoo", "GC=F",        "黃金期貨 (USD/oz)"),
    "silver": ("yahoo", "SI=F",        "白銀期貨 (USD/oz)"),
    "dxy":    ("yahoo", "DX-Y.NYB",    "美元指數 DXY"),
    "spx":    ("yahoo", "^GSPC",       "S&P 500 指數"),
    "qqq":    ("yahoo", "QQQ",         "QQQ (那斯達克 100,1 倍)"),
    "tqqq":   ("yahoo", "TQQQ",        "TQQQ (那斯達克 100,3 倍)"),
}


def get(url: str) -> bytes:
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            if attempt == 2:
                raise
            print(f"   retry ({e})")
            time.sleep(2)
    raise RuntimeError("unreachable")


def fred(code: str):
    raw = get(f"https://fred.stlouisfed.org/graph/fredgraph.csv?id={code}&cosd={START}")
    rows = csv.reader(io.StringIO(raw.decode("utf-8")))
    next(rows)
    out = []
    for d, v in rows:
        if v in ("", "."):
            continue
        out.append((d, float(v)))
    return out


def yahoo(code: str):
    p1 = int(datetime.fromisoformat(START).replace(tzinfo=timezone.utc).timestamp())
    p2 = int(time.time()) + 86400
    sym = urllib.request.quote(code)
    url = (f"https://query1.finance.yahoo.com/v8/finance/chart/{sym}"
           f"?period1={p1}&period2={p2}&interval=1d&events=split")
    j = json.loads(get(url))["chart"]["result"][0]
    ts = j["timestamp"]
    ind = j["indicators"]
    closes = (ind.get("adjclose") or [{}])[0].get("adjclose") or ind["quote"][0]["close"]
    tz = j["meta"].get("gmtoffset", 0)
    out = {}
    for t, c in zip(ts, closes):
        if c is None:
            continue
        d = datetime.fromtimestamp(t + tz, tz=timezone.utc).strftime("%Y-%m-%d")
        out[d] = c
    return sorted(out.items())


def pack(rows):
    """壓縮成 {start, d: [與前一筆相差天數], v: [數值]},縮小檔案體積。"""
    base = datetime.fromisoformat(rows[0][0])
    prev = 0
    deltas, vals = [], []
    for d, v in rows:
        n = (datetime.fromisoformat(d) - base).days
        deltas.append(n - prev)
        prev = n
        vals.append(round(v, 4))
    return {"start": rows[0][0], "d": deltas, "v": vals}


def main():
    data = {"updated": datetime.now().strftime("%Y-%m-%d %H:%M"), "meta": {}, "series": {}}
    for key, (src, code, label) in SERIES.items():
        print(f"-> {key:7s} {src}:{code}")
        rows = fred(code) if src == "fred" else yahoo(code)
        data["series"][key] = pack(rows)
        data["meta"][key] = {"source": src.upper(), "code": code, "label": label,
                             "first": rows[0][0], "last": rows[-1][0], "n": len(rows)}
        print(f"   {rows[0][0]} ~ {rows[-1][0]}  ({len(rows)} 筆)  最新 {rows[-1][1]:.2f}")
    out = Path(__file__).parent / "data" / "data.js"
    out.parent.mkdir(exist_ok=True)
    js = "window.MACRO_DATA=" + json.dumps(data, ensure_ascii=False, separators=(",", ":")) + ";"
    out.write_text(js, encoding="utf-8")
    print(f"\n完成 → {out} ({out.stat().st_size/1024:.0f} KB)")


if __name__ == "__main__":
    main()
