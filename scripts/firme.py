#!/usr/bin/env python3
"""Controlla che le chiamate ai composable interni combacino con le firme.

Serve perché il modulo :app non si compila ovunque, e questo è l'unico tipo di
errore che si può verificare leggendo i sorgenti: il compilatore lo trova subito,
ma solo su una macchina che riesce a scaricare le dipendenze di Android.

Due controlli, entrambi nati da errori veri:

  - lambda finale su un parametro che non è una lambda. Se `modifier` viene
    dichiarato dopo `onCambia`, la graffa finale si attacca a `modifier` e il
    messaggio del compilatore parla di Modifier quando il problema è l'ordine.

  - argomento posizionale che sembra un callback ma finisce su un parametro che
    non lo è. `Azione("Elimina", rosso, onRimuovi)` mette una lambda dove la
    firma vuole un Boolean.
"""
import re
import sys
from pathlib import Path

RADICE = Path(__file__).resolve().parent.parent


def dividi(testo):
    """Spezza una lista di argomenti sulle virgole di primo livello."""
    pezzi, profondita, corrente = [], 0, ""
    for i, ch in enumerate(testo):
        # `<` e `>` delimitano i generici, ma `->` non è una parentesi chiusa:
        # contarla come tale sfasa la profondità e fonde tutti i parametri
        # successivi al primo che è una lambda.
        freccia = ch == ">" and i > 0 and testo[i - 1] == "-"
        if ch in "([{<":
            profondita += 1
        elif ch in ")]}" or (ch == ">" and not freccia):
            profondita -= 1
        if ch == "," and profondita == 0:
            pezzi.append(corrente.strip())
            corrente = ""
        else:
            corrente += ch
    if corrente.strip():
        pezzi.append(corrente.strip())
    return pezzi


def senza_commenti(testo):
    testo = re.sub(r"/\*.*?\*/", "", testo, flags=re.S)
    return re.sub(r"//[^\n]*", "", testo)


def senza_stringhe(testo):
    """Svuota i letterali di testo, tenendo le virgolette.

    Serve al conteggio delle parentesi: una graffa dentro una stringa non apre niente,
    ma sbilancia il conto e produce un allarme su codice perfettamente valido.
    """
    testo = re.sub(r'""".*?"""', '""""""', testo, flags=re.S)
    testo = re.sub(r'"(\\.|[^"\\\n])*"', '""', testo)
    return re.sub(r"'(\\.|[^'\\\n])'", "' '", testo)


def sbilanciamenti(testo):
    """Le parentesi che non tornano, ignorando commenti e stringhe."""
    pulito = senza_stringhe(senza_commenti(testo))
    fuori = []
    for apre, chiude, nome in (("{", "}", "graffe"), ("(", ")", "tonde")):
        a, c = pulito.count(apre), pulito.count(chiude)
        if a != c:
            fuori.append(f"{a} {nome} aperte, {c} chiuse")
    return fuori


def corpo(testo, apertura):
    """Il contenuto fra la parentesi che apre in `apertura` e la sua chiusura."""
    profondita = 0
    for i in range(apertura, len(testo)):
        if testo[i] == "(":
            profondita += 1
        elif testo[i] == ")":
            profondita -= 1
            if profondita == 0:
                return testo[apertura + 1:i], i
    return None, len(testo)


def firme(sorgenti):
    trovate = {}
    for f in sorgenti:
        testo = senza_commenti(f.read_text(encoding="utf-8"))
        for m in re.finditer(r"\bfun\s+(?:<[^>]*>\s+)?([A-Z]\w*)\s*\(", testo):
            dentro, _ = corpo(testo, m.end() - 1)
            if dentro is None:
                continue
            parametri = []
            for p in dividi(dentro):
                nome = p.split(":")[0].strip()
                tipo = p.split(":", 1)[1] if ":" in p else ""
                dichiarazione = tipo.split("=")[0]
                parametri.append({
                    "nome": nome,
                    "lambda": "->" in dichiarazione,
                    # Una lambda @Composable viene chiamata dentro la composizione e può
                    # leggere il tema; una normale no, e leggerlo lì non compila.
                    "componibile": "@Composable" in dichiarazione,
                    "default": "=" in tipo,
                })
            trovate[m.group(1)] = {"parametri": parametri, "file": f.name}
    return trovate


def controlla(sorgenti, note):
    problemi = []
    for f in sorgenti:
        grezzo = f.read_text(encoding="utf-8")
        testo = senza_commenti(grezzo)
        for m in re.finditer(r"(?<![\w.])([A-Z]\w*)\s*\(", testo):
            nome = m.group(1)
            if nome not in note:
                continue
            dentro, fine = corpo(testo, m.end() - 1)
            if dentro is None:
                continue
            parametri = note[nome]["parametri"]
            riga = testo.count("\n", 0, m.start()) + 1
            dove = f"{f.name}:{riga} {nome}(…)"

            # È la dichiarazione, non una chiamata. Il generico va previsto:
            # prima del nome può esserci "fun " oppure "fun <T> ".
            if re.search(r"\bfun\s+(<[^>]*>\s*)?$", testo[:m.start()]):
                continue

            posizionali = [a for a in dividi(dentro) if not re.match(r"^\w+\s*=[^=]", a)]

            # lambda finale: si attacca all'ultimo parametro
            dopo = testo[fine + 1:fine + 4].lstrip()
            if dopo.startswith("{"):
                if not parametri or not parametri[-1]["lambda"]:
                    ultimo = parametri[-1]["nome"] if parametri else "(nessuno)"
                    problemi.append(
                        f"{dove}: lambda finale, ma l'ultimo parametro è "
                        f"'{ultimo}' e non è una funzione"
                    )
                elif len(posizionali) >= len(parametri):
                    problemi.append(f"{dove}: troppi argomenti prima della lambda finale")

            # tema letto dentro una lambda che non è @Composable
            for arg in dividi(dentro):
                nominato = re.match(r"^(\w+)\s*=\s*\{(.*)\}\s*$", arg, re.S)
                if not nominato:
                    continue
                parametro = next(
                    (p for p in parametri if p["nome"] == nominato.group(1)), None
                )
                if not parametro or not parametro["lambda"] or parametro["componibile"]:
                    continue
                letto = re.search(r"\b(extra|MaterialTheme)\s*\.", nominato.group(2))
                if letto:
                    problemi.append(
                        f"{dove}: '{parametro['nome']}' non è @Composable, ma la lambda "
                        f"legge {letto.group(1)} — vanno letti fuori e catturati"
                    )

            # un callback finito su un parametro che non lo è
            for i, arg in enumerate(posizionali):
                if i >= len(parametri):
                    problemi.append(f"{dove}: {len(posizionali)} argomenti posizionali "
                                    f"per {len(parametri)} parametri")
                    break
                pare_callback = re.match(r"^on[A-Z]\w*$", arg) or arg.startswith("{")
                if pare_callback and not parametri[i]["lambda"]:
                    problemi.append(
                        f"{dove}: '{arg}' finisce su '{parametri[i]['nome']}', "
                        f"che non è una funzione"
                    )
    return problemi


def main():
    sorgenti = sorted((RADICE / "app/src/main/kotlin").rglob("*.kt"))
    note = firme(sorgenti)
    problemi = controlla(sorgenti, note)

    tutti = sorgenti + sorted((RADICE / "core/src").rglob("*.kt"))
    for f in tutti:
        for guaio in sbilanciamenti(f.read_text(encoding="utf-8")):
            problemi.append(f"{f.name}: {guaio}")
    for p in problemi:
        print(f"  ✗ {p}")
    print(f"\n{len(note)} firme, {len(problemi)} problemi")
    return 1 if problemi else 0


if __name__ == "__main__":
    sys.exit(main())
