# -*- coding: utf-8 -*-
"""Извлечение «констант» (всех final-полей и enum-констант) из каждого .java-модуля
программы AutoTestGenerator.

Модуль = файл .java. Для каждого модуля собираются:
  * enum-константы (если тип — перечисление);
  * поля final (static final и обычные final-поля экземпляра).

javalang не понимает синтаксис Java 14+ (стрелочный switch, текстовые блоки, record),
который встречается в телах методов, поэтому используется собственный сканер: лексер
строк/символов/текстовых блоков/комментариев + стек контекстов фигурных скобок, который
пропускает тела методов и инициализаторов, но сохраняет объявления полей уровня класса
и список enum-констант.

Экспортирует MODULES — список кортежей (package, filename, classname, kind, constants),
где constants — список словарей {'name','enum','static','value'} в порядке объявления.
"""
import os, re, glob

SRC_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                        "src", "main", "java", "ru", "autotestgen")
IDENT = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")
PKG_ORDER = ["ui", "parser", "model", "generator", "data", "common"]


# --------------------------------------------------------------------------- лексер
def lex(src):
    """Заменяет строковые/символьные литералы и текстовые блоки на плейсхолдеры
    \x00N\x00 (без скобок/точек с запятой внутри) и удаляет комментарии.
    Возвращает (sanitized, tokens) — tokens[N] — исходный текст литерала."""
    out = []
    toks = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        two = src[i:i + 2]
        three = src[i:i + 3]
        if three == '"""':                                   # текстовый блок
            j = src.find('"""', i + 3)
            j = (j + 3) if j != -1 else n
            toks.append(src[i:j]); out.append(f"\x00{len(toks) - 1}\x00"); i = j
        elif c == '"':                                       # строковый литерал
            j = i + 1
            while j < n and src[j] != '"':
                j += 2 if src[j] == '\\' else 1
            j += 1
            toks.append(src[i:j]); out.append(f"\x00{len(toks) - 1}\x00"); i = j
        elif c == "'":                                       # символьный литерал
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == '\\' else 1
            j += 1
            toks.append(src[i:j]); out.append(f"\x00{len(toks) - 1}\x00"); i = j
        elif two == '//':                                    # строчный комментарий
            j = src.find('\n', i); i = j if j != -1 else n
        elif two == '/*':                                    # блочный комментарий
            j = src.find('*/', i + 2); i = (j + 2) if j != -1 else n
        else:
            out.append(c); i += 1
    return "".join(out), toks


def detok(s, toks):
    return re.sub(r"\x00(\d+)\x00", lambda m: toks[int(m.group(1))], s)


def match_brace(s, i):
    """i указывает на '{'; вернуть индекс парной '}'. Строки уже вырезаны лексером."""
    depth = 0
    while i < len(s):
        if s[i] == '{':
            depth += 1
        elif s[i] == '}':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(s) - 1


def _strip_annotations(decl):
    return re.sub(r"@[\w.]+\s*(\([^()]*\))?", " ", decl)


def _split_top_eq(decl):
    """Разбить объявление по первому '=' на нулевой глубине скобок (не ==/<=/>=/!=)."""
    depth = 0
    for k, ch in enumerate(decl):
        if ch in "([<":
            depth += 1
        elif ch in ")]>":
            depth -= 1
        elif ch == '=' and depth == 0:
            prv = decl[k - 1] if k else ''
            nxt = decl[k + 1] if k + 1 < len(decl) else ''
            if prv not in "=!<>" and nxt != '=':
                return decl[:k], decl[k + 1:]
    return decl, None


def _field_from_member(decl, toks):
    """decl — текст члена класса (без ';'), содержащий 'final'. Если это поле —
    вернуть [(name, is_static, value)], иначе [] (метод и т.п.)."""
    left, right = _split_top_eq(decl)
    if '(' in _strip_annotations(left):          # есть параметры → метод, не поле
        return []
    words = IDENT.findall(_strip_annotations(left))
    if 'final' not in words:
        return []
    is_static = 'static' in words
    value = detok(right, toks).strip() if right else None
    # одиночный декларатор (множественные — редкость в этом коде)
    name = words[-1] if words else None
    if not name or name in ("final", "static"):
        return []
    return [(name, is_static, value)]


# --------------------------------------------------------------- разбор enum-констант
def _parse_enum_constants(body):
    """body — содержимое тела enum (между {...}), строки уже вырезаны.
    Вернуть (список имён констант, остаток_тела_после_';' для разбора полей)."""
    depth = 0
    end = len(body)
    for k, ch in enumerate(body):
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == ';' and depth == 0:
            end = k
            break
    head, rest = body[:end], body[end + 1:] if end < len(body) else ""
    names, seg, depth = [], [], 0
    for ch in head:
        if ch in "([{":
            depth += 1; seg.append(ch)
        elif ch in ")]}":
            depth -= 1; seg.append(ch)
        elif ch == ',' and depth == 0:
            ids = IDENT.findall("".join(seg)); names.append(ids[0]) if ids else None
            seg = []
        else:
            seg.append(ch)
    ids = IDENT.findall("".join(seg))
    if ids:
        names.append(ids[0])
    return names, rest


# --------------------------------------------------------------------------- сканер
def _scan_type_body(body, toks, is_enum, out):
    """Разобрать тело типа: собрать enum-константы и final-поля в out (по порядку)."""
    if is_enum:
        names, body = _parse_enum_constants(body)
        for nm in names:
            out.append({"name": nm, "enum": True, "static": True, "value": None})
    i, n, buf = 0, len(body), []
    while i < n:
        c = body[i]
        if c == '{':
            j = match_brace(body, i)
            header = "".join(buf)
            hwords = IDENT.findall(_strip_annotations(header))
            ht = next((w for w in ("class", "interface", "enum", "record")
                       if w in hwords), None)
            _, eqr = _split_top_eq(header)
            if ht:                                   # вложенный тип — разобрать рекурсивно
                _scan_type_body(body[i + 1:j], toks, ht == "enum", out)
                buf = []
            elif eqr is not None:                    # инициализатор поля {…}=array
                buf.append(body[i:j + 1])            # сохранить как часть объявления
            else:                                    # тело метода/конструктора/блока
                buf = []
            i = j + 1
            continue
        if c == ';':
            decl = "".join(buf).strip()
            if "final" in IDENT.findall(_strip_annotations(decl)):
                for nm, st, val in _field_from_member(decl, toks):
                    out.append({"name": nm, "enum": False, "static": st, "value": val})
            buf = []
            i += 1
            continue
        if c == '}':
            buf = []
            i += 1
            continue
        buf.append(c)
        i += 1


def extract_file(path):
    src = open(path, encoding="utf-8").read()
    s, toks = lex(src)
    # выйти на тело верхнего типа
    m = re.search(r"\b(class|interface|enum|record)\s+([A-Za-z_$][\w$]*)", s)
    kind = m.group(1) if m else "class"
    name = m.group(2) if m else os.path.splitext(os.path.basename(path))[0]
    constants = []
    bo = s.find('{', m.end() if m else 0)
    if bo != -1:
        be = match_brace(s, bo)
        _scan_type_body(s[bo + 1:be], toks, kind == "enum", constants)
    pkg = os.path.basename(os.path.dirname(path))
    return pkg, os.path.basename(path), name, kind, constants


def _collect():
    files = sorted(glob.glob(os.path.join(SRC_ROOT, "**", "*.java"), recursive=True))
    mods = [extract_file(p) for p in files]
    mods.sort(key=lambda m: (PKG_ORDER.index(m[0]) if m[0] in PKG_ORDER else 99, m[1]))
    return mods


MODULES = _collect()


# --------------------------------------------------------------------------- самопроверка
if __name__ == "__main__":
    total_f = sum(len(m[4]) for m in MODULES)
    print(f"Файлов: {len(MODULES)} | всего констант (final-полей+enum): {total_f}")
    with_c = sum(1 for m in MODULES if m[4])
    print(f"С константами: {with_c} | без констант: {len(MODULES) - with_c}\n")
    for pkg, fn, nm, kind, cs in MODULES:
        names = ", ".join(("⟨" + c["name"] + "⟩" if c["enum"] else c["name"]) for c in cs)
        print(f"  [{pkg}] {fn} ({kind} {nm}): {names if names else '— нет констант'}")
    print("\n--- эталоны ---")
    chk = {m[1]: [c["name"] for c in m[4]] for m in MODULES}
    for fn, exp in [("XmlNamespaces.java", {"NS_E", "NS_E3", "NS_MD"}),
                    ("TestDataFactory.java", {"DATE_FORMAT", "DATE_TIME_FORMAT", "MSK", "RND"}),
                    ("EntityKind.java", {"PRIMARY", "CHILD", "REFERENCE_DICTIONARY"}),
                    ("AttrType.java", {"STRING", "DECIMAL", "DATE", "DATETIME"}),
                    ("ModifyType.java", {"INSERT", "UPDATE", "DELETE", "LOGICAL_EDIT", "ARCHIVE", "code"}),
                    ("DatabaseConnection.java", {"DEFAULT_URL", "url"})]:
        got = set(chk.get(fn, []))
        print(("OK  " if got == exp else "FAIL") + f" {fn}: got={sorted(got)} exp={sorted(exp)}")
