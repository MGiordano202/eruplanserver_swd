#!/usr/bin/env bash
# Verifica JML con OpenJML (Lab 5): delombok + ESC + RAC sulle classi annotate.
# Uso (dalla root del progetto, Git Bash o Linux/macOS):
#   scripts/jml/verify.sh          # ESC e RAC
#   scripts/jml/verify.sh esc      # solo ESC
#   scripts/jml/verify.sh rac      # solo RAC
# Richiede openjml nel PATH. Log in target/jml/.
set -euo pipefail

MODE="${1:-all}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

# Separatore del classpath: ';' su Windows (Git Bash), ':' altrove
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';' ;; *) SEP=':' ;; esac

OUT=target/jml
DELOMBOK=target/delombok
mkdir -p "$OUT"

echo "== 1. Compilazione e classpath Maven"
./mvnw -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile="$OUT/cp.txt"
CP="$(cat "$OUT/cp.txt")${SEP}target/classes"

echo "== 2. Delombok in $DELOMBOK"
LOMBOK_JAR="$(tr "$SEP" '\n' < "$OUT/cp.txt" | grep -m1 'lombok-[0-9.]*\.jar')"
rm -rf "$DELOMBOK"
java -jar "$LOMBOK_JAR" delombok src/main/java -d "$DELOMBOK" -c "$CP" > /dev/null

# Classi con specifiche JML
PKG="$DELOMBOK/it/unisa/eruplanserver/IS"
FILES=(
  "$PKG/Utility/Validator.java"
  "$PKG/Utility/Utility.java"
  "$PKG/Entity/GPE/Punto.java"
  "$PKG/Entity/GPE/ZonaSicura.java"
  "$PKG/Entity/GNF/NucleoFamiliareEntity.java"
)

OJ_OPTS=(-cp "$CP" -sourcepath "$DELOMBOK")
# Entity: i campi (e i getter generati da Lombok) possono essere null, come nel codice reale
NULLABLE=(--nullable-by-default)

if [[ "$MODE" == all || "$MODE" == esc ]]; then
  echo "== 3. ESC (log: $OUT/esc.log, un log per gruppo in $OUT/esc/)"
  mkdir -p "$OUT/esc"
  V="$PKG/Utility/Validator.java"

  # ESC dimostra un metodo alla volta su un solo thread: i gruppi girano in parallelo.
  # Validator e Utility usano il default JML (riferimenti non null) perché i parametri che
  # possono essere null sono annotati /*@ nullable @*/; le entity usano --nullable-by-default.
  esc() {
    local name="$1"; shift
    local start=$SECONDS
    openjml -esc "${OJ_OPTS[@]}" --timeout 30 "$@" > "$OUT/esc/$name.log" 2>&1 || true
    echo "TEMPO $((SECONDS - start))s" >> "$OUT/esc/$name.log"
  }
  esc validator-zone      --method 'creaZoneSicure' "$V" &
  esc validator-pericolo  --method 'creaZonaPericolo,checkConnessione' "$V" &
  esc validator-regex     --method 'corrisponde,isNomeValid,isCodiceFiscaleValid,isSessoValid,isPasswordValid,isCognomeValid,isNomePianoValid,isIndirizzoValid,isCodiceFiscaleCharactersValid' "$V" &
  esc validator-semplici  --method 'isTroppoCorto,isTroppoLungo,isCodiceFiscaleLengthValid,isAssistenzaDefinita,isMinorenneDefinito,isDataNascitaValid,isDataNascitaFormatoValid' "$V" &
  esc utility             "$PKG/Utility/Utility.java" &
  esc punto-zonasicura    "${NULLABLE[@]}" "$PKG/Entity/GPE/Punto.java" "$PKG/Entity/GPE/ZonaSicura.java" &
  # toString escluso: Lombok lo genera per entity e builder senza specifiche JML (fuori ambito)
  esc nucleo              "${NULLABLE[@]}" --exclude toString "$PKG/Entity/GNF/NucleoFamiliareEntity.java" &
  wait

  : > "$OUT/esc.log"
  for f in "$OUT"/esc/*.log; do
    n="$(grep -c 'verify: The prover' "$f" || true)"
    printf '%-22s %3s segnalazioni  %s\n' "$(basename "$f" .log)" "$n" "$(grep TEMPO "$f")"
    { echo "######## $(basename "$f" .log)"; cat "$f"; } >> "$OUT/esc.log"
  done
fi

if [[ "$MODE" == all || "$MODE" == rac ]]; then
  echo "== 4. RAC (log: $OUT/rac.log)"
  rm -rf "$OUT/rac" && mkdir -p "$OUT/rac"
  openjml -rac -d "$OUT/rac" "${NULLABLE[@]}" "${OJ_OPTS[@]}" "${FILES[@]}"
  JMLRUNTIME="$(dirname "$(command -v openjml)")/jmlruntime.jar"
  # Su Windows java.exe non capisce i percorsi /c/...: conversione in C:/...
  if command -v cygpath > /dev/null; then JMLRUNTIME="$(cygpath -m "$JMLRUNTIME")"; fi
  RAC_CP="$OUT/rac${SEP}${JMLRUNTIME}${SEP}${CP}"
  javac --release 21 -d "$OUT/rac" -cp "$RAC_CP" scripts/jml/JmlRacDriver.java
  # openjml-run usa la JDK di OpenJML, che contiene le classi di supporto del RAC
  "$(dirname "$(command -v openjml)")/openjml-run" -cp "$RAC_CP" JmlRacDriver > "$OUT/rac.log" 2>&1 || true
  cat "$OUT/rac.log"
fi
