#!/bin/bash

set -o pipefail

failures=0
numeric='^[0-9]+$'
if [[ "$1" =~ $numeric ]]; then
    runs="$1"
else
    runs=1
fi

FLAGS=(
    "-Dheadless=true"
)

if command -v ansi2txt >/dev/null; then
    FLAGS+=("-Dstyle.color=always")
    PIPECLEANER=ansi2txt
else
    PIPECLEANER=cat
fi

DIR="$(dirname "$(readlink -f "$0")")"

runcount=0
while [ "${runcount}" -lt ${runs} ]; do
    logfile="$DIR/target/cryostat-unittests-$(date -Iminutes).log"
    mvn "${FLAGS[@]}" surefire:test |& tee >($PIPECLEANER > "${logfile}")
    if [ "$?" -ne 0 ]; then
        failures=$((failures+1))
    fi
    runcount=$((runcount+1))
done

echo
echo "########################"
echo "Test runs completed"
echo "Failures: ${failures}"
echo "Runs: ${runcount}/${runs}"

exit ${failures}
