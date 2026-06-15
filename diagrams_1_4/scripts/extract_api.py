# -*- coding: utf-8 -*-
"""Извлечение структуры классов из исходного кода (AST, javalang).

Проходит по всем .java в src/main/java/ru/autotestgen, для каждого типа
(класс/интерфейс/перечисление, включая вложенные) собирает поля, конструкторы и
методы с модификаторами доступа, типами, параметрами и Javadoc.

Гарантирует, что члены берутся из деклараций AST, а не из строк генерируемого
кода внутри тел методов. Результат -> /tmp/api.json + сводка членов на класс.
"""
import os, re, json, glob
import javalang

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
SRC = os.path.join(ROOT, "src", "main", "java", "ru", "autotestgen")
OUT = "/tmp/api.json"

PKG_ORDER = ["model", "parser", "generator", "data", "common", "ui"]

ACCESS_ORDER = ["public", "protected", "private", "static", "final", "abstract",
                "default", "synchronized", "native"]


def neutralize(src):
    """Заменяет текстовые блоки (\"\"\"...\"\"\") на пустую строку, сохраняя
    обычные строки, символы и комментарии. Нужно, т.к. javalang (Java 8) не
    понимает text blocks."""
    out = []; i = 0; n = len(src)
    while i < n:
        c = src[i]
        if c == "/" and src[i + 1:i + 2] == "/":
            j = src.find("\n", i); j = n if j < 0 else j
            out.append(src[i:j]); i = j; continue
        if c == "/" and src[i + 1:i + 2] == "*":
            j = src.find("*/", i + 2); j = n if j < 0 else j + 2
            out.append(src[i:j]); i = j; continue
        if c == '"' and src[i:i + 3] == '"""':
            j = i + 3
            while j < n:
                if src[j] == "\\": j += 2; continue
                if src[j] == '"' and src[j:j + 3] == '"""': j += 3; break
                j += 1
            out.append('""'); i = j; continue
        if c == '"':
            j = i + 1
            while j < n:
                if src[j] == "\\": j += 2; continue
                if src[j] == '"': j += 1; break
                if src[j] == "\n": break
                j += 1
            out.append(src[i:j]); i = j; continue
        if c == "'":
            j = i + 1
            while j < n:
                if src[j] == "\\": j += 2; continue
                if src[j] == "'": j += 1; break
                if src[j] == "\n": break
                j += 1
            out.append(src[i:j]); i = j; continue
        out.append(c); i += 1
    return "".join(out)


def _tokenize(src):
    """Грубая токенизация кода (после neutralize): пропускает пробелы и
    комментарии, строки/символы сворачивает в один токен. Возвращает список
    (текст, начало, конец)."""
    toks = []; i = 0; n = len(src)
    while i < n:
        c = src[i]
        if c in " \t\r\n": i += 1; continue
        if c == "/" and src[i + 1:i + 2] == "/":
            j = src.find("\n", i); i = n if j < 0 else j; continue
        if c == "/" and src[i + 1:i + 2] == "*":
            j = src.find("*/", i + 2); i = n if j < 0 else j + 2; continue
        if c == '"':
            j = i + 1
            while j < n:
                if src[j] == "\\": j += 2; continue
                if src[j] == '"': j += 1; break
                j += 1
            toks.append(('"S"', i, j)); i = j; continue
        if c == "'":
            j = i + 1
            while j < n:
                if src[j] == "\\": j += 2; continue
                if src[j] == "'": j += 1; break
                j += 1
            toks.append(("'C'", i, j)); i = j; continue
        if c.isalpha() or c in "_$":
            j = i
            while j < n and (src[j].isalnum() or src[j] in "_$"): j += 1
            toks.append((src[i:j], i, j)); i = j; continue
        if c.isdigit():
            j = i
            while j < n and (src[j].isalnum() or src[j] in "._"): j += 1
            toks.append((src[i:j], i, j)); i = j; continue
        if c == "-" and src[i + 1:i + 2] == ">":
            toks.append(("->", i, i + 2)); i += 2; continue
        toks.append((c, i, i + 1)); i += 1; continue
    return toks


def blank_bodies(src):
    """Выносит наружу тела методов/конструкторов/лямбд/анонимных классов:
    содержимое каждого блока { ... }, открывающая скобка которого следует за ')'
    или '->' (либо за throws-списком после ')'), заменяется на пусто. Так из
    исходника удаляются switch-выражения и прочая «начинка» тел, мешающая
    javalang, но сигнатуры, поля и javadoc сохраняются."""
    toks = _tokenize(src); N = len(toks)

    def is_body_open(k):
        if k == 0: return False
        prev = toks[k - 1][0]
        if prev == ")" or prev == "->": return True
        j = k - 1
        while j >= 0:
            tj = toks[j][0]
            if tj == "throws": break
            if tj == "." or tj == "," or tj.isidentifier(): j -= 1; continue
            break
        return j >= 1 and toks[j][0] == "throws" and toks[j - 1][0] == ")"

    repls = []; k = 0
    while k < N:
        if toks[k][0] == "{" and is_body_open(k):
            depth = 1; j = k + 1
            while j < N and depth > 0:
                if toks[j][0] == "{": depth += 1
                elif toks[j][0] == "}": depth -= 1
                j += 1
            repls.append((toks[k][2], toks[j - 1][1])); k = j
        else:
            k += 1
    if not repls: return src
    out = []; pos = 0
    for a, b in repls:
        out.append(src[pos:a]); pos = b
    out.append(src[pos:])
    return "".join(out)


def access_str(mods):
    mods = set(mods or [])
    present = [m for m in ACCESS_ORDER if m in mods]
    if not any(a in mods for a in ("public", "protected", "private")):
        present = ["пакетный"] + present
    return " ".join(present) if present else "пакетный"


def arg_to_str(a):
    pt = getattr(a, "pattern_type", None)
    if pt == "?":
        return "?"
    if pt in ("extends", "super"):
        return "? " + pt + " " + (type_to_str(a.type) if a.type else "")
    return type_to_str(a.type) if getattr(a, "type", None) else "?"


def type_to_str(t):
    if t is None:
        return "void"
    s = t.name
    if getattr(t, "arguments", None):
        s += "<" + ", ".join(arg_to_str(a) for a in t.arguments) + ">"
    if getattr(t, "sub_type", None):
        s += "." + type_to_str(t.sub_type)
    dims = getattr(t, "dimensions", None)
    if dims:
        s += "[]" * len(dims)
    return s


def clean_doc(doc):
    if not doc:
        return ""
    t = re.sub(r"^/\*+", "", doc)
    t = re.sub(r"\*+/\s*$", "", t)
    out = []
    for ln in t.splitlines():
        ln = ln.strip()
        if ln.startswith("*"):
            ln = ln[1:].strip()
        out.append(ln)
    text = " ".join(x for x in out if x)
    text = re.split(r"\s*@(param|return|returns|throws|exception|see|author|since|deprecated)\b",
                    text)[0]
    return text.strip()


def param_str(p):
    ts = type_to_str(p.type)
    if getattr(p, "varargs", False):
        ts += "..."
    return {"type": ts, "name": p.name}


def collect_type(decl, pkg, outer, out_list):
    kind = ("interface" if isinstance(decl, javalang.tree.InterfaceDeclaration)
            else "enum" if isinstance(decl, javalang.tree.EnumDeclaration)
            else "class")
    qualified = (outer + "." + decl.name) if outer else decl.name

    extends = None
    if getattr(decl, "extends", None):
        ext = decl.extends
        extends = type_to_str(ext[0]) if isinstance(ext, list) else type_to_str(ext)
    implements = [type_to_str(i) for i in (getattr(decl, "implements", None) or [])]

    rec = {
        "package": pkg, "name": decl.name, "qualified": qualified, "kind": kind,
        "access": access_str(decl.modifiers), "extends": extends,
        "implements": implements, "doc": clean_doc(getattr(decl, "documentation", None)),
        "fields": [], "methods": [], "enum_constants": [], "nested": [],
    }

    # тело: для enum члены лежат в decl.body.declarations + .constants
    body = decl.body
    members = body
    if kind == "enum":
        for c in (body.constants or []):
            rec["enum_constants"].append(
                {"name": c.name, "doc": clean_doc(getattr(c, "documentation", None))})
        members = body.declarations or []

    for m in members:
        if isinstance(m, javalang.tree.FieldDeclaration):
            for v in m.declarators:
                init = None
                if v.initializer is not None:
                    init = type(v.initializer).__name__
                rec["fields"].append({
                    "access": access_str(m.modifiers),
                    "type": type_to_str(m.type) + ("[]" * len(v.dimensions) if v.dimensions else ""),
                    "name": v.name, "init": init,
                    "doc": clean_doc(getattr(m, "documentation", None)),
                })
        elif isinstance(m, javalang.tree.ConstructorDeclaration):
            rec["methods"].append({
                "access": access_str(m.modifiers), "returns": "—",
                "name": m.name, "params": [param_str(p) for p in m.parameters],
                "throws": list(m.throws or []), "ctor": True,
                "doc": clean_doc(getattr(m, "documentation", None)),
            })
        elif isinstance(m, javalang.tree.MethodDeclaration):
            rec["methods"].append({
                "access": access_str(m.modifiers),
                "returns": type_to_str(m.return_type),
                "name": m.name, "params": [param_str(p) for p in m.parameters],
                "throws": list(m.throws or []), "ctor": False,
                "doc": clean_doc(getattr(m, "documentation", None)),
            })
        elif isinstance(m, (javalang.tree.ClassDeclaration,
                            javalang.tree.InterfaceDeclaration,
                            javalang.tree.EnumDeclaration)):
            rec["nested"].append(m.name)
            collect_type(m, pkg, qualified, out_list)

    out_list.append(rec)


def main():
    by_pkg = {p: [] for p in PKG_ORDER}
    files = sorted(glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True))
    total_files = 0
    for f in files:
        pkg = os.path.basename(os.path.dirname(f))
        with open(f, encoding="utf-8") as fh:
            src = fh.read()
        try:
            tree = javalang.parse.parse(blank_bodies(neutralize(src)))
        except Exception as e:
            print(f"!! НЕ РАЗОБРАН {os.path.relpath(f, SRC)}: {type(e).__name__}")
            raise
        flat = []
        for t in tree.types:
            collect_type(t, pkg, None, flat)
        by_pkg.setdefault(pkg, []).extend(flat)
        total_files += 1

    # упорядочить пакеты и классы внутри (верхнеуровневые по имени, вложенные следом)
    result = {}
    for pkg in PKG_ORDER:
        types = by_pkg.get(pkg, [])
        types.sort(key=lambda r: (r["qualified"].split(".")[0].lower(),
                                  r["qualified"].count("."), r["qualified"].lower()))
        result[pkg] = types

    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(result, fh, ensure_ascii=False, indent=1)

    # сводка для проверки
    print(f"Файлов разобрано: {total_files}")
    grand_types = grand_f = grand_m = 0
    for pkg in PKG_ORDER:
        types = result[pkg]
        print(f"\n=== {pkg} ({len(types)} типов) ===")
        for r in types:
            nf, nm = len(r["fields"]), len(r["methods"])
            grand_types += 1; grand_f += nf; grand_m += nm
            ec = f", констант={len(r['enum_constants'])}" if r["kind"] == "enum" else ""
            print(f"  {r['kind']:9} {r['qualified']:30} поля={nf:2} методы={nm:2}{ec}")
    print(f"\nИТОГО: типов={grand_types}, полей={grand_f}, методов/конструкторов={grand_m}")


if __name__ == "__main__":
    main()
