#!/usr/bin/env bash
# Controlli veloci sui sorgenti Kotlin, da eseguire prima di ogni commit.
#
# Non sostituiscono il compilatore: servono per i guasti che il compilatore vede
# tardi o male, e per quelli che una modifica automatica può introdurre in silenzio.
set -u
cd "$(dirname "$0")/.."

problemi=0
segnala() { echo "  ✗ $*"; problemi=$((problemi + 1)); }

file=$(find app core -name "*.kt")

echo "import duplicati"
for f in $file; do
    d=$(grep "^import " "$f" | sort | uniq -d)
    [ -n "$d" ] && segnala "$f: $(echo "$d" | tr '\n' ' ')"
done

# Le parentesi si contano in scripts/firme.py, che sa togliere commenti e stringhe:
# contarle qui col solo tr faceva scattare l'allarme su una graffa dentro un letterale.

# Una modifica automatica sbagliata — un ciclo che itera sui caratteri invece che
# sulle righe, un append dentro un loop — duplica un blocco migliaia di volte. Il
# risultato ha le parentesi perfettamente bilanciate e passa ogni controllo di
# forma: l'unico segnale è che lo stesso blocco compare troppe volte.
echo "blocchi ripetuti"
for f in $file; do
    ripetuta=$(grep -v "^\s*$" "$f" | awk 'length($0) > 40' | sort | uniq -c | sort -rn | head -1)
    quante=$(echo "$ripetuta" | awk '{print $1}')
    [ "${quante:-0}" -gt 15 ] && segnala "$f: una riga si ripete $quante volte"
done

echo "dimensioni plausibili"
for f in $file; do
    r=$(wc -l < "$f")
    [ "$r" -gt 1200 ] && segnala "$f: $r righe, controlla che non sia duplicato"
done

# Il primo carattere di un file Kotlin è sempre 'p' di package o '/' di commento.
echo "intestazioni dei file"
for f in $file; do
    grep -q "^package " "$f" || segnala "$f: manca la dichiarazione package"
done

echo "firme dei composable"
if command -v python3 >/dev/null 2>&1; then
    python3 scripts/firme.py | sed 's/^  ✗/  ✗/' | grep "✗" && problemi=$((problemi + 1))
else
    echo "  (saltato: serve python3)"
fi

echo
if [ "$problemi" -eq 0 ]; then
    echo "tutto a posto: $(echo "$file" | wc -w) file controllati"
else
    echo "$problemi problemi trovati"
    exit 1
fi
