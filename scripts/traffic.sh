#!/usr/bin/env bash
# Usage: ./scripts/traffic.sh <normal|attack> <seconds> [url]
MODE=${1:-normal}
DURATION=${2:-60}
URL=${3:-http://localhost:30080}

VALID='{"transactionId":"TX-%s","amount":%s,"currency":"AED","sourceAccount":"AE070331234567890123456","destinationAccount":"GB29NWBK60161331926819"}'
INVALID='{"transactionId":"TX-%s","amount":%s,"currency":"BTC","sourceAccount":"AE070331234567890123456","destinationAccount":"AE070331234567890123456"}'

end=$((SECONDS + DURATION))
n=0
while [ $SECONDS -lt $end ]; do
  n=$((n + 1))
  if [ "$MODE" = "attack" ]; then bad=$((RANDOM % 10 < 9)); else bad=$((RANDOM % 10 < 1)); fi
  if [ $bad -eq 1 ]; then body=$(printf "$INVALID" "$n" "$((RANDOM % 90000 + 10001))"); else body=$(printf "$VALID" "$n" "$((RANDOM % 5000 + 1)).50"); fi
  curl -s -o /dev/null -X POST "$URL/validate" -H 'Content-Type: application/json' -d "$body"
  sleep 0.2
done
echo "$MODE: sent $n requests"
