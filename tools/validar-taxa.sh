#!/usr/bin/env bash
# Valida a taxa comercial (DEC-0001) contra preço real de mercado.
#
# Para cada linha do CSV:
#   1. publica a tarifa líquida no broker (PUSH, via /admin/rooms/{id}/rates);
#   2. cria uma reserva no serviço de reservas e lê o total que o hóspede pagaria;
#   3. cancela a reserva (devolve o estoque) — a validação não deixa lixo;
#   4. compara com o preço público do mesmo hotel/datas e calcula o desvio.
#
# Uso:
#   ./tools/validar-taxa.sh cotacoes.csv
#   ./tools/validar-taxa.sh --dry-run cotacoes.csv   # não chama serviços; calcula pela regra
#
# CSV (cabeçalho obrigatório):
#   hotelId,roomId,ratePlanId,checkIn,checkOut,adults,children,netAdult,netChild,marketPrice,source,quotedAt
#
# netAdult/netChild  = tarifa LÍQUIDA por noite (o que o hotel cobra de nós)
# marketPrice        = preço TOTAL do período que o concorrente publica (o que o hóspede paga lá)
set -euo pipefail

BROKER_URL="${BROKER_URL:-http://localhost:8081}"
API_URL="${API_URL:-http://localhost:8080}"
ADMIN_KEY="${BROKER_ADMIN_KEY:-admin-key-dev}"
READ_KEY="${BROKER_READ_KEY:-read-key-dev}"
THRESHOLD="10"

DRY_RUN=0
if [[ "${1:-}" == "--dry-run" ]]; then DRY_RUN=1; shift; fi
CSV="${1:?informe o arquivo CSV}"

fail() { echo "erro: $*" >&2; exit 1; }

token() {
    curl -sf -X POST "$API_URL/auth" -H 'Content-Type: application/json' \
        -d '{"user":"usuario","passwd":"senha"}' | jq -r .token
}

# Regra do ADR-004 em awk: diária por pax arredondada (HALF_UP) e então somada.
local_total() {
    local netAdult="$1" netChild="$2" adults="$3" children="$4" nights="$5" feeBps="$6"
    awk -v a="$netAdult" -v c="$netChild" -v ad="$adults" -v ch="$children" \
        -v n="$nights" -v bps="$feeBps" 'BEGIN {
            f = 1 + bps/10000
            adult = int(a*f*100 + 0.5)/100
            child = int(c*f*100 + 0.5)/100
            printf "%.2f", (adult*ad + child*ch) * n
        }'
}

TOKEN=""
if [[ $DRY_RUN -eq 0 ]]; then
    command -v jq >/dev/null || fail "jq é necessário no modo real (ou use --dry-run)"
    TOKEN="$(token)"
fi

printf "%-22s %-12s %12s %12s %8s  %s\n" "hotel/quarto" "período" "nosso" "mercado" "desvio" "fonte"
printf '%.0s─' {1..92}; echo

WORST=0
while IFS=, read -r hotelId roomId ratePlanId checkIn checkOut adults children netAdult netChild marketPrice source quotedAt; do
    [[ "$hotelId" == "hotelId" || -z "${hotelId:-}" ]] && continue

    nights=$(python3 -c "from datetime import date;print((date.fromisoformat('$checkOut')-date.fromisoformat('$checkIn')).days)")

    if [[ $DRY_RUN -eq 0 ]]; then
        curl -sf -X PUT "$BROKER_URL/admin/rooms/$roomId/rates" \
            -H "X-Api-Key: $ADMIN_KEY" -H 'Content-Type: application/json' \
            -d "{\"ratePlanId\":$ratePlanId,\"currency\":\"BRL\",\"from\":\"$checkIn\",\"to\":\"$(date -d "$checkOut -1 day" +%F 2>/dev/null || python3 -c "from datetime import date,timedelta;print(date.fromisoformat('$checkOut')-timedelta(days=1))")\",
                 \"adultAmount\":$netAdult,\"childAmount\":$netChild}" >/dev/null \
            || fail "falha ao publicar tarifa no broker (room $roomId)"

        key="validacao-$(date +%s%N)-$roomId"
        created=$(curl -sf -X POST "$API_URL/api/v1/bookings" \
            -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
            -H "Idempotency-Key: $key" \
            -d "{\"hotelId\":$hotelId,\"roomId\":$roomId,\"ratePlanId\":$ratePlanId,\"checkIn\":\"$checkIn\",\"checkOut\":\"$checkOut\",\"adults\":$adults,\"children\":$children}") \
            || fail "falha ao reservar (hotel $hotelId / room $roomId)"
        ours=$(jq -r .totalAmount <<<"$created")
        bookingId=$(jq -r .id <<<"$created")
        curl -sf -X POST "$API_URL/api/v1/bookings/$bookingId/cancel" -H "Authorization: Bearer $TOKEN" >/dev/null \
            || echo "aviso: não foi possível cancelar $bookingId (estoque ocupado)" >&2
    else
        ours=$(local_total "$netAdult" "$netChild" "$adults" "$children" "$nights" 500)
    fi

    read -r delta sign <<<"$(awk -v o="$ours" -v m="$marketPrice" 'BEGIN{d=(o-m)/m*100; printf "%.1f %s", (d<0?-d:d), (d<0?"-":"+")}')"
    sign_delta="$sign$delta"
    printf "%-22s %-12s %12s %12s %9s  %s\n" \
        "$hotelId/$roomId" "${checkIn:5}+${nights}n" "$ours" "$marketPrice" "${sign_delta}%" "$source"

    WORST=$(awk -v w="$WORST" -v d="$delta" 'BEGIN{print (d>w?d:w)}')
done < "$CSV"

echo
awk -v w="$WORST" -v t="$THRESHOLD" 'BEGIN{
    printf "maior desvio absoluto: %.1f%% (limiar: %s%%)\n", w, t
    if (w > t) print "VEREDITO: reabrir a DEC-0001 — fora da faixa";
    else print "VEREDITO: manter 5% — dentro da faixa";
}'
[[ $DRY_RUN -eq 1 ]] && echo "(dry-run: o total foi calculado pela regra do ADR-004, não pelo serviço)"
