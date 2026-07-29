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


CARATTERE = re.compile(r"'(\\u[0-9a-fA-F]{4}|\\.|[^'\\\n])'")


def _scorri(testo, svuota_stringhe):
    """Ripassa il sorgente una volta sola, sapendo sempre dentro cosa si trova.

    Due passate indipendenti non possono funzionare, e non è teoria: `arrayOf("*/*")`
    contiene l'apertura di un commento dentro una stringa, e chi toglie prima i commenti
    si mangia tutto fino al `/**` successivo — cioè un pezzo di file scelto a caso, con
    dentro le graffe che chiudevano quello che c'era prima. Il verso opposto sbaglia allo
    stesso modo su una stringa nominata dentro un commento.

    I ritorni a capo di quello che si toglie restano, così i numeri di riga continuano a
    corrispondere al file vero.
    """
    pezzi = []
    i, n = 0, len(testo)
    while i < n:
        due = testo[i:i + 2]
        if due == "//":
            fine = testo.find("\n", i)
            i = n if fine < 0 else fine
        elif due == "/*":
            chiusura = testo.find("*/", i + 2)
            # Un commento mai chiuso arriva a fine file: è come lo legge anche Kotlin.
            fine = n if chiusura < 0 else chiusura + 2
            pezzi.append("\n" * testo.count("\n", i, fine))
            i = fine
        elif testo.startswith('"""', i):
            chiusura = testo.find('"""', i + 3)
            fine = n if chiusura < 0 else chiusura + 3
            if svuota_stringhe:
                pezzi.append('""""""' + "\n" * testo.count("\n", i, fine))
            else:
                pezzi.append(testo[i:fine])
            i = fine
        elif testo[i] == '"':
            j = i + 1
            while j < n and testo[j] != '"' and testo[j] != "\n":
                j += 2 if testo[j] == "\\" else 1
            fine = j + 1 if j < n and testo[j] == '"' else j
            pezzi.append('""' if svuota_stringhe else testo[i:fine])
            i = fine
        elif testo[i] == "`":
            # Nome fra apici inversi: dentro c'è prosa, e nei test è prosa italiana.
            # Va copiata com'è, ma senza guardarci dentro.
            chiusura = testo.find("`", i + 1)
            fine = i + 1 if chiusura < 0 else chiusura + 1
            pezzi.append(testo[i:fine])
            i = fine
        elif testo[i] == "'":
            # Solo la forma esatta di un carattere è un letterale. Un apostrofo in mezzo
            # a una parola — "l'importo", "dell'anno" — è testo, e trattarlo come
            # l'inizio di qualcosa si mangia il resto della riga con le sue graffe.
            carattere = CARATTERE.match(testo, i)
            if carattere:
                pezzi.append("' '" if svuota_stringhe else carattere.group(0))
                i = carattere.end()
            else:
                pezzi.append("'")
                i += 1
        else:
            pezzi.append(testo[i])
            i += 1
    return "".join(pezzi)


def senza_commenti(testo):
    return _scorri(testo, svuota_stringhe=False)


def senza_stringhe(testo):
    """Svuota i letterali di testo, tenendo le virgolette.

    Serve al conteggio delle parentesi: una graffa dentro una stringa non apre niente,
    ma sbilancia il conto e produce un allarme su codice perfettamente valido.
    """
    return _scorri(testo, svuota_stringhe=True)


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


def privati_fuori_posto(sorgenti):
    """Composable privati chiamati da un file che non è quello che li dichiara.

    Kotlin li nasconde al resto del progetto, ma il nome esiste e il controllo delle
    firme lo riconosce lo stesso: senza questo, una chiamata sbagliata passa i controlli
    e si scopre solo in compilazione.
    """
    dichiarati = {}
    pubblici = set()
    for f in sorgenti:
        testo = senza_commenti(f.read_text(encoding="utf-8"))
        for m in re.finditer(r"^(private )?fun\s+(?:<[^>]*>\s+)?([A-Z]\w*)\s*\(", testo, re.M):
            if m.group(1):
                dichiarati.setdefault(m.group(2), set()).add(f.name)
            else:
                pubblici.add(m.group(2))

    problemi = []
    for f in sorgenti:
        testo = senza_stringhe(senza_commenti(f.read_text(encoding="utf-8")))
        for nome, dove in dichiarati.items():
            if nome in pubblici or f.name in dove:
                continue
            m = re.search(r"(?<![\w.])" + nome + r"\s*\(", testo)
            if m:
                riga = testo.count("\n", 0, m.start()) + 1
                problemi.append(
                    f"{f.name}:{riga} chiama {nome}(), che è privata in {', '.join(sorted(dove))}"
                )
    return problemi


def smart_cast_impossibili(sorgenti):
    """`x.tizio != null` seguito da `x.tizio.caio()` sulla stessa proprietà.

    Kotlin non restringe il tipo di una proprietà pubblica dichiarata in un altro
    modulo: non può garantire che fra il controllo di nullità e l'uso resti la stessa.
    Quindi questa forma compila dentro :core e non compila da :app, che è esattamente
    la differenza che qui non si vede.

    Si guarda la forma e non i tipi: cercare i nomi delle proprietà nullabili di :core
    segnalava anche `partenza.dayOfMonth`, che è un LocalDate e non c'entra niente.
    La cura è sempre la stessa: legare a una variabile locale e usare quella.
    """
    problemi = []
    for f in sorgenti:
        # Le stringhe restano: l'uso incriminato sta quasi sempre dentro
        # un'interpolazione, e svuotarle nasconderebbe proprio il caso da trovare.
        testo = senza_commenti(f.read_text(encoding="utf-8"))
        for m in re.finditer(r"(\w+)\.(\w+)\s*!=\s*null", testo):
            ricevente, proprieta = m.group(1), m.group(2)
            # La finestra copre il ramo di un `when` o il corpo di un `if` breve:
            # oltre, il controllo di nullità non governa più l'espressione.
            finestra = testo[m.end():m.end() + 400]
            if re.search(re.escape(f"{ricevente}.{proprieta}") + r"\.\w", finestra):
                riga = testo.count("\n", 0, m.start()) + 1
                problemi.append(
                    f"{f.name}:{riga} controlla '{ricevente}.{proprieta} != null' e poi "
                    f"lo usa col punto secco — lo smart cast non attraversa i moduli, "
                    f"legalo a una variabile"
                )
    return problemi


def main():
    sorgenti = sorted((RADICE / "app/src/main/kotlin").rglob("*.kt"))
    note = firme(sorgenti)
    problemi = controlla(sorgenti, note)

    problemi += privati_fuori_posto(sorgenti)
    problemi += smart_cast_impossibili(sorgenti)

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
