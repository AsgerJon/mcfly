#!/usr/bin/env bash
# unitTAS: runs the in-game tests in a real client (see UNITTAS.md).
#   ./unittas.sh                     every suite
#   ./unittas.sh storage             one suite (several: storage,light)
#   ./unittas.sh storage BelowZero   only the tests whose Class.method name contains "BelowZero"
#   UNITTAS_TASK=<task> ./unittas.sh         another Gradle run task than runClient
#   UNITTAS_RUN_DIR=run/client ./unittas.sh  the client's folder (the run task's working directory)
# Results: unittas-results/<time>/ (report.txt, screenshots/, gradle.log); unittas-results/latest points there.
# A run whose login stalls starts again, twice at most, each try in its own folder.
set -u
cd "$(dirname "$0")" || exit 1
ROOT=$(pwd)
SUITES=${1:-}
FILTER=${2:-}
TASK=${UNITTAS_TASK:-runClient}
# the tries before this one (the script counts them itself)
RETRY=${UNITTAS_RETRY:-0}
STAMP=$(date +%Y-%m-%d_%H-%M-%S)
RESULTS="$ROOT/unittas-results/$STAMP"
REQUEST_DIR="$ROOT/${UNITTAS_RUN_DIR:-run/client}/unittas"
REQUEST="$REQUEST_DIR/request.properties"

mkdir -p "$RESULTS" "$REQUEST_DIR"
ln -sfn "$STAMP" "$ROOT/unittas-results/latest"
{
	echo "suites=$SUITES"
	echo "filter=$FILTER"
	echo "results=$RESULTS"
} > "$REQUEST"
# the game uses the request up when it starts; this is for a run that never got that far
trap 'rm -f "$REQUEST"' EXIT

START=$(date +%s)
clock() {
	local elapsed=$(( $(date +%s) - START ))
	printf '%02d:%02d' $((elapsed / 60)) $((elapsed % 60))
}

echo "unitTAS: suites ${SUITES:-all}${FILTER:+, only '$FILTER'}; results in unittas-results/$STAMP"
./gradlew --console=plain "$TASK" > "$RESULTS/gradle.log" 2>&1 &
GRADLE=$!

SPIN='|/-\'
spin=0
shown=0
show_new_progress() {
	[ -f "$RESULTS/progress.txt" ] || return
	local total
	total=$(wc -l < "$RESULTS/progress.txt")
	if [ "$total" -gt "$shown" ]; then
		printf '\r\033[K'
		tail -n +$((shown + 1)) "$RESULTS/progress.txt" | head -n $((total - shown)) | while IFS= read -r line; do
			printf '[%s] %s\n' "$(clock)" "$line"
		done
		shown=$total
	fi
}

while kill -0 "$GRADLE" 2>/dev/null; do
	show_new_progress
	if [ -f "$RESULTS/progress.txt" ]; then
		status="game running"
	else
		status=$(grep -E '^> Task ' "$RESULTS/gradle.log" 2>/dev/null | tail -n 1)
		status=${status:-starting Gradle}
	fi
	printf '\r\033[K[%s] %s %s' "$(clock)" "${SPIN:$((spin++ % 4)):1}" "$status"
	sleep 0.5
done
wait "$GRADLE"
CODE=$?
show_new_progress
printf '\r\033[K'

# 1.7.10's login stalls now and then, without a word (the game gives up after 30 s): start the game again
if [ ! -f "$RESULTS/report.txt" ] && grep -qF "the test world didn't load (the player never logged in)" "$RESULTS/progress.txt" 2>/dev/null \
	&& [ "$RETRY" -lt 2 ]; then
	echo "The player never logged in; starting the game again (try $((RETRY + 2)) of 3)"
	export UNITTAS_RETRY=$((RETRY + 1))
	exec "$ROOT/$(basename "$0")" "$@"
fi

if [ ! -f "$RESULTS/report.txt" ]; then
	echo "No report: the game ended before the tests finished (Gradle exit $CODE). The end of gradle.log:"
	tail -n 25 "$RESULTS/gradle.log"
	exit 1
fi
echo
# the report without its "ok" lines; the whole one is in the file
grep -v ' \.\.\. ok (' "$RESULTS/report.txt"
echo
echo "Full report: unittas-results/latest/report.txt  (took $(clock))"
tail -n 1 "$RESULTS/report.txt" | grep -q '^OK' && exit 0 || exit 1
