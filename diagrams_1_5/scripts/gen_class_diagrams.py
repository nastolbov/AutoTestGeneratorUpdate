# -*- coding: utf-8 -*-
"""Диаграммы классов (Graphviz, HTML-метки) для каждого пакета: исходная,
уточнённая, детальная. Все три показывают ОДИН набор из N классов пакета
(счётчик не меняется). Детальная: все поля «±имя: Тип» и все методы
«±имя(параметры): Тип», каждый на отдельной строке (методичка Рис.7.20).
Межпакетные типы помечаются «Пакет::Класс». Ч/Б, шрифт Liberation Serif."""
import subprocess, os, sys, re, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import apimodel as M
import scenarios as S

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"


def extid(lbl):
    return "ext_" + re.sub(r"\W", "_", lbl)


def map_key_to_node(pkg, key, ext_set):
    """Ключ объекта сценария -> id узла диаграммы (свой класс или внешний бокс common)."""
    if key == S.EDGE:
        return None
    lbl = S.label_of(pkg, key)
    if "::" in lbl:
        return extid(lbl) if lbl in ext_set else None
    return lbl.lstrip(":").replace(".", "_")


def scenario_edges(pkg, ext_set):
    """Направленные пары из сценариев (свои-свои, для common — и свои-внешние)."""
    pairs, seen = [], set()
    for flow in S.SC.get(pkg, {}).get("flows", {}).values():
        for frm, to, _, _ in flow:
            a = map_key_to_node(pkg, frm, ext_set); b = map_key_to_node(pkg, to, ext_set)
            if a and b and a != b and (a, b) not in seen:
                seen.add((a, b)); pairs.append((a, b))
    return pairs


def render(dot, name):
    p = f"/tmp/{name}.dot"
    open(p, "w", encoding="utf-8").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o", f"{OUT}/{name}.png"], check=True)
    print("OK", name)


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def stereotype(r):
    if r["kind"] == "interface":
        return "«interface»"
    if r["kind"] == "enum":
        return "«enumeration»"
    if "abstract" in r["access"].split():
        return "«abstract»"
    return None


def fld_line(f, idx, pkg):
    t = M.xref(f["type"], idx, pkg)
    return f"{M.vis(f['access'])} {f['name']}: {esc(t)}"


def mth_line(m, idx, pkg):
    ps = ", ".join(f"{p['name']}: {esc(M.xref(p['type'], idx, pkg))}" for p in m["params"])
    ret = "" if m["ctor"] else f": {esc(M.xref(m['returns'], idx, pkg))}"
    txt = f"{M.vis(m['access'])} {esc(m['name'])}({ps}){ret}"
    if "abstract" in m["access"].split():
        txt = f"<I>{txt}</I>"
    return txt


def compartment(lines):
    if not lines:
        return '<TR><TD ALIGN="LEFT" BALIGN="LEFT" HEIGHT="6"> </TD></TR>'
    body = '<BR ALIGN="LEFT"/>'.join(lines) + '<BR ALIGN="LEFT"/>'
    return f'<TR><TD ALIGN="LEFT" BALIGN="LEFT">{body}</TD></TR>'


def class_label(r, idx, pkg, detailed):
    st = stereotype(r)
    head = (f'{st}<BR/>' if st else '') + f'<B>{esc(r["name"])}</B>'
    rows = [f'<TR><TD ALIGN="CENTER">{head}</TD></TR>']
    if detailed:
        rows.append(compartment([fld_line(f, idx, pkg) for f in r["fields"]]))
        rows.append(compartment([mth_line(m, idx, pkg) for m in M.visible_methods(r)]))
    else:
        # исходная/уточнённая — имя + ДВА пустых компартмента (атрибуты, операции)
        rows.append(compartment([]))
        rows.append(compartment([]))
    table = ('<<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" CELLPADDING="4">'
             + "".join(rows) + "</TABLE>>")
    return table


def dedup_rels(rels):
    diamond = {(s, d) for k, s, d, sm, dm in rels if k in ("agg", "comp")}
    out = []
    for k, s, d, sm, dm in rels:
        if k == "nest" and ((s, d) in diamond or (d, s) in diamond):
            continue  # уже есть ромбовая связь по полю — вложенность не дублируем
        out.append((k, s, d, sm, dm))
    return out


# уточнённая/детальная: типизированные связи (обобщение ▷, агрегация ◇, композиция ◆).
# Вложенность (nest) и ассоциация — простой направленной стрелкой (без «кружка»).
EDGE = {
    "gen":  'dir=forward, arrowhead=empty, arrowtail=none, style=solid',
    "agg":  'dir=both, arrowhead=vee, arrowtail=odiamond, style=solid',
    "comp": 'dir=both, arrowhead=vee, arrowtail=diamond, style=solid',
    "nest": 'dir=forward, arrowhead=vee, arrowtail=none, style=solid',
    "assoc": 'dir=forward, arrowhead=vee, arrowtail=none, style=solid',
}
# исходная: только простые направленные линии (без ромбов/треугольников, без кратностей)
EDGE_PLAIN = 'dir=forward, arrowhead=vee, arrowtail=none, style=solid'


# число столбцов сетки для детальной диаграммы (баланс ширины/высоты)
COLS = {"ui": 3, "parser": 3, "model": 5, "generator": 4, "data": 3, "common": 3}


def ext_label(lbl):
    """Бокс внешнего класса (только common): метка «Пакет::Класс», пустые компартменты."""
    rows = [f'<TR><TD ALIGN="CENTER">{esc(lbl)}</TD></TR>', compartment([]), compartment([])]
    return ('<<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" CELLPADDING="4">'
            + "".join(rows) + "</TABLE>>")


def build(pkg, api, idx, kind):
    """kind: 'source' | 'refined' | 'detailed'."""
    types = api[pkg]
    detailed = (kind == "detailed")
    rels = dedup_rels(M.relations(pkg, api, idx))
    ext_labels = S.external_labels(pkg)      # непусто только для common
    ext_set = set(ext_labels)

    nodes = []
    for r in types:
        nodes.append(f'  {r["name"].replace(".", "_")} [label={class_label(r, idx, pkg, detailed)}];')
    for lbl in ext_labels:
        nodes.append(f'  {extid(lbl)} [label={ext_label(lbl)}];')

    cons = ", constraint=false" if detailed else ""
    edges, present = [], set()
    for k, s, d, sm, dm in rels:
        s2, d2 = s.replace(".", "_"), d.replace(".", "_")
        present.add((s2, d2)); present.add((d2, s2))
        if kind == "source":
            edges.append(f'  {s2} -> {d2} [{EDGE_PLAIN}{cons}];')
        else:
            lbl = (f', taillabel="{sm}"' if sm else "") + (f', headlabel="{dm}"' if dm else "")
            edges.append(f'  {s2} -> {d2} [{EDGE[k]}{lbl}{cons}];')
    # ассоциации из сценариев (свои-свои; для common — и свои-внешние)
    for a, b in scenario_edges(pkg, ext_set):
        if (a, b) not in present and (b, a) not in present:
            present.add((a, b)); present.add((b, a))
            style = EDGE_PLAIN if kind == "source" else EDGE["assoc"]
            edges.append(f'  {a} -> {b} [{style}{cons}];')

    grid = []
    if detailed:
        all_ids = [r["name"].replace(".", "_") for r in types] + [extid(l) for l in ext_labels]
        K = COLS.get(pkg, 4)
        rows = [all_ids[i:i + K] for i in range(0, len(all_ids), K)]
        for row in rows:
            grid.append("  {rank=same; " + "; ".join(row) + ";}")
            for a, b in zip(row, row[1:]):
                grid.append(f"  {a} -> {b} [style=invis];")
        for r0, r1 in zip(rows, rows[1:]):
            grid.append(f"  {r0[0]} -> {r1[0]} [style=invis];")

    dot = f'''digraph {pkg}_{kind} {{
  bgcolor=white; rankdir=TB; nodesep=0.6; ranksep=0.8;
  fontname="{FONT}";
  node [shape=plaintext, fontname="{FONT}", fontsize=12, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, labeldistance=1.5, labelfontsize=9];
{chr(10).join(nodes)}
{chr(10).join(grid)}
{chr(10).join(edges)}
}}'''
    return dot


def main():
    api = M.load_api(); idx = M.build_index(api)
    counts = {}
    for pkg in M.PKG_ORDER:
        counts[pkg] = {}
        for r in api[pkg]:
            counts[pkg][r["qualified"]] = {"fields": len(r["fields"]),
                                           "methods": len(M.visible_methods(r))}
        for lbl in S.external_labels(pkg):
            counts[pkg][lbl] = {"external": True}
        for kind in ("source", "refined", "detailed"):
            render(build(pkg, api, idx, kind), f"cls_{pkg}_{kind}")
    json.dump(counts, open("/tmp/cls_counts.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("class diagrams done")


if __name__ == "__main__":
    main()
