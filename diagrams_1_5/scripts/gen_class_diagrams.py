# -*- coding: utf-8 -*-
"""Диаграммы классов (Graphviz, HTML-метки) для каждого пакета: исходная,
уточнённая, детальная. Все три показывают ОДИН набор из N классов пакета
(счётчик не меняется). Детальная: все поля «±имя: Тип» и все методы
«±имя(параметры): Тип», каждый на отдельной строке (методичка Рис.7.20).
Межпакетные типы помечаются «Пакет::Класс». Ч/Б, шрифт Liberation Serif."""
import subprocess, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import apimodel as M
import scenarios as S

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"


def scen_class(pkg, key):
    """Имя класса пакета по ключу объекта сценария (внешние/край -> None)."""
    if key == S.EDGE:
        return None
    lbl = S.label_of(pkg, key)
    if "::" in lbl:
        return None
    return lbl.lstrip(":")


def scenario_assoc(pkg):
    """Внутрипакетные направленные пары из сценариев взаимодействия (ассоциации)."""
    pairs, seen = [], set()
    for flow in S.SC.get(pkg, {}).get("flows", {}).values():
        for frm, to, _, _ in flow:
            a, b = scen_class(pkg, frm), scen_class(pkg, to)
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
    txt = f"{M.vis(f['access'])} {f['name']}: {esc(t)}"
    return f"<U>{txt}</U>" if M.is_static(f["access"]) else txt


def mth_line(m, idx, pkg):
    ps = ", ".join(f"{p['name']}: {esc(M.xref(p['type'], idx, pkg))}" for p in m["params"])
    ret = "" if m["ctor"] else f": {esc(M.xref(m['returns'], idx, pkg))}"
    txt = f"{M.vis(m['access'])} {esc(m['name'])}({ps}){ret}"
    if "abstract" in m["access"].split() or m["ctor"] is False and False:
        txt = f"<I>{txt}</I>"
    return f"<U>{txt}</U>" if M.is_static(m["access"]) else txt


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
    nested = {(s, d) for k, s, d, sm, dm in rels if k == "nest"}
    out = []
    for k, s, d, sm, dm in rels:
        if k == "comp" and (s, d) in nested:
            continue  # вложенность важнее композиции-дубля
        out.append((k, s, d, sm, dm))
    return out


# уточнённая/детальная: типизированные связи (обобщение ▷, агрегация ◇, композиция ◆, вложенность)
EDGE = {
    "gen":  'dir=forward, arrowhead=empty, arrowtail=none, style=solid',
    "agg":  'dir=both, arrowhead=vee, arrowtail=odiamond, style=solid',
    "comp": 'dir=both, arrowhead=vee, arrowtail=diamond, style=solid',
    "nest": 'dir=back, arrowhead=none, arrowtail=odot, style=solid',
    "assoc": 'dir=forward, arrowhead=vee, arrowtail=none, style=solid',
}
# исходная: только простые направленные линии (без ромбов/треугольников, без кратностей)
EDGE_PLAIN = 'dir=forward, arrowhead=vee, arrowtail=none, style=solid'


# число столбцов сетки для детальной диаграммы (баланс ширины/высоты)
COLS = {"ui": 3, "parser": 3, "model": 5, "generator": 4, "data": 3, "common": 3}


def build(pkg, api, idx, kind):
    """kind: 'source' | 'refined' | 'detailed'."""
    types = api[pkg]
    detailed = (kind == "detailed")
    rels = dedup_rels(M.relations(pkg, api, idx))
    # добавить ассоциации из сценариев взаимодействия (связи, которых нет по полям)
    existing = {(s, d) for _, s, d, _, _ in rels} | {(d, s) for _, s, d, _, _ in rels}
    for a, b in scenario_assoc(pkg):
        if (a, b) not in existing and (b, a) not in existing:
            rels.append(("assoc", a, b, "", "")); existing.add((a, b))
    nodes = []
    for r in types:
        nm = r["name"].replace(".", "_")
        nodes.append(f'  {nm} [label={class_label(r, idx, pkg, detailed)}];')
    edges = []
    for k, s, d, sm, dm in rels:
        s2, d2 = s.replace(".", "_"), d.replace(".", "_")
        cons = ", constraint=false" if detailed else ""
        if kind == "source":
            edges.append(f'  {s2} -> {d2} [{EDGE_PLAIN}{cons}];')
        else:
            lbl = ""
            if sm:
                lbl += f', taillabel="{sm}"'
            if dm:
                lbl += f', headlabel="{dm}"'
            edges.append(f'  {s2} -> {d2} [{EDGE[k]}{lbl}{cons}];')
    grid = []
    if detailed:
        K = COLS.get(pkg, 4)
        names = [r["name"].replace(".", "_") for r in types]
        rows = [names[i:i + K] for i in range(0, len(names), K)]
        for row in rows:
            grid.append("  {rank=same; " + "; ".join(row) + ";}")
            for a, b in zip(row, row[1:]):
                grid.append(f"  {a} -> {b} [style=invis];")
        for r0, r1 in zip(rows, rows[1:]):
            grid.append(f"  {r0[0]} -> {r1[0]} [style=invis];")
    dot = f'''digraph {pkg}_{kind} {{
  bgcolor=white; rankdir=TB; nodesep=0.5; ranksep=0.7;
  fontname="{FONT}";
  node [shape=plaintext, fontname="{FONT}", fontsize=12, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, labeldistance=1.4, labelfontsize=9];
{chr(10).join(nodes)}
{chr(10).join(grid)}
{chr(10).join(edges)}
}}'''
    return dot


def main():
    api = M.load_api(); idx = M.build_index(api)
    for pkg in M.PKG_ORDER:
        for kind in ("source", "refined", "detailed"):
            render(build(pkg, api, idx, kind), f"cls_{pkg}_{kind}")
    print("class diagrams done")


if __name__ == "__main__":
    main()
