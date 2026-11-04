#!/usr/bin/env bash
# Builds a company_app box from a fresh Ubuntu VM and proves a box matches the expected state.
#   replicate.sh check [--strict]   read-only: one PASS, WARN or FAIL per item; exit 1 on FAIL
#                                   (--strict: WARN fails too)
#   replicate.sh plan               read-only: what apply would do on this box
#   replicate.sh apply              fixes each failing agent item, then rechecks it; refuses the prod VPS
# Items marked "user" need wizard.sh. Prints variable names only, never values.
set -uo pipefail
export PATH=$PATH:/usr/sbin:/sbin

HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(cd "$HERE/../.." && pwd)
FILES=$HERE/files

PROD_HOSTNAME=company-app-vps
PROD_TAILSCALE_IP=100.84.96.51
MOUNT=/mnt/data
MIN_VOLUME_GB=100
SWAP_MB=8192
IDLE_TIMEOUT=${IDLE_TIMEOUT:-90s}
COOLIFY_VERSION=4.5.3
COOLIFY_PORTS="8000 8080 6001 6002"
USER_TIMERS="company-app-alert company-app-deploy-gate company-app-db-backup company-app-perf company-app-probe"
APP_ENV_NAMES="APP_HOST APP_PORT AUTH_DUMMY_PASSWORD DB_HOST DB_PORT JWT_AUDIENCE JWT_ISSUER JWT_SECRET NTFY_TOPIC NTFY_URL POSTGRES_DB POSTGRES_PASSWORD POSTGRES_USER SMTP_FROM SMTP_HOST SMTP_PASSWORD SMTP_PORT SMTP_USERNAME"
ENV_FILES="alerts:NTFY_URL,NTFY_TOPIC backup:R2_ACCOUNT_ID,R2_BUCKET,R2_ACCESS_KEY_ID,R2_SECRET_ACCESS_KEY,BACKUP_PASSPHRASE deploy:COOLIFY_TOKEN,COOLIFY_URL,COOLIFY_APP_UUID probe:PROBE_USER,PROBE_PASS,PROBE_DB_CONTAINER"

# name|owner|what the item is. Order is the build order.
ITEMS=(
  "os|note|Ubuntu 24.04 on arm64, Oracle A1.Flex"
  "packages|agent|git, curl, gpg, nftables, ufw, python3, gh, node 20+, rclone"
  "swap|agent|8G swapfile"
  "volume|agent|150G block volume at $MOUNT, ext4, in fstab with nofail"
  "data_dirs|agent|$MOUNT/backups and $MOUNT/pg-telemetry"
  "sshd|agent|keys only, no passwords"
  "tailscale|user|tailscale installed and logged in"
  "docker_daemon|agent|log limits and address pool"
  "coolify|agent|Coolify running"
  "ufw|agent|deny by default; 80, 443, tailscale; 22 only for Coolify's bridges"
  "ports_lock|agent|Coolify ports $COOLIFY_PORTS unreachable from the public interface"
  "nft_cve|agent|CVE-2026-98432 rule live and enabled at boot"
  "kernel_fix|agent|kernel-fix-check timer"
  "heartbeat|agent|idle-heartbeat cron"
  "proxy|user|proxy keeps idleTimeout $IDLE_TIMEOUT and HTTP/3, in file, Coolify and the live container"
  "postgres|user|Postgres 18 private, telemetry tablespace on $MOUNT, pg_stat_statements"
  "app|user|Coolify app from master, port 3023, every env var name, /health UP"
  "env_files|user|~/.config/company-app-*.env hold every name, mode 600"
  "ops_files|agent|ops scripts and units match the repo"
  "timers|agent|linger, user timers, log follower"
  "backup_recent|note|nightly backup succeeded within 26h"
)

S="sudo -n"
DETAIL=""
detail() { DETAIL="$*"; }

coolify_sql() { docker exec coolify-db psql -U coolify -d coolify -Atc "$1" 2>/dev/null; }
db_uuid() { echo "${REPLICA_DB_UUID:-$(coolify_sql "select uuid from standalone_postgresqls order by id limit 1")}"; }
app_uuid() { echo "${REPLICA_APP_UUID:-$(coolify_sql "select uuid from applications order by id limit 1")}"; }
app_container() { docker ps -q --filter "label=coolify.applicationUuid=$(app_uuid)" | head -1; }
running() { [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" == true ]]; }

# Fills a shipped file's @...@ fields with this box's ids.
render() {
  local db app; db=$(db_uuid); app=$(app_uuid)
  sed -e "s/@DB_UUID@/$db/g" -e "s/@DB_PREFIX@/${db:0:7}/g" -e "s/@APP_PREFIX@/${app:0:8}/g" "$1"
}

# dest<TAB>source for every file a box must hold; sources are the repo's own copies.
home_files() {
  local src
  (cd "$FILES/home" && find . -type f | sort) | while read -r src; do
    printf '%s\t%s\n' "$HOME/${src#./}" "$FILES/home/${src#./}"
  done
  local p=$REPO/tools/perf d=$HOME/.local/share/company-app-perf u=$HOME/.config/systemd/user
  printf '%s\t%s\n' \
    "$HOME/.local/bin/perf-record" "$p/perf-record" "$HOME/.local/bin/perf-report" "$p/perf-report" \
    "$u/company-app-perf.service" "$p/company-app-perf.service" "$u/company-app-perf.timer" "$p/company-app-perf.timer" \
    "$d/probe.sh" "$p/probe.sh" "$d/probe.mjs" "$p/probe.mjs" "$d/perf-slow" "$p/perf-slow" \
    "$d/check-latency.py" "$p/check-latency.py" "$d/limits.json" "$p/limits.json" \
    "$u/company-app-probe.service" "$p/systemd/company-app-probe.service" "$u/company-app-probe.timer" "$p/systemd/company-app-probe.timer"
}

# Compares a live file with its source; a root-only file needs sudo to read.
same_as() { # dest source
  cmp -s <(render "$2") <($S cat "$1" 2>/dev/null)
}
root_file() { echo "$FILES/root$1"; }
install_root() { # dest mode
  $S install -D -m "$2" -o root -g root "$(root_file "$1")" "$1"
}

chk_os() {
  . /etc/os-release
  detail "$PRETTY_NAME $(dpkg --print-architecture)"
  [[ "$VERSION_ID" == 24.04 && "$(dpkg --print-architecture)" == arm64 ]] || return 2
}

chk_packages() {
  local c missing=""
  for c in git curl gpg nft ufw python3 gh node jq; do command -v "$c" >/dev/null || missing+=" $c"; done
  [[ -x $HOME/.local/bin/rclone ]] || missing+=" rclone"
  [[ -z "$missing" ]] || { detail "missing:$missing"; return 1; }
  local major; major=$(node -p 'process.versions.node.split(".")[0]')
  ((major >= 20)) || { detail "node $major is older than 20"; return 1; }
}
fix_packages() {
  $S apt-get update -qq && $S env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq git curl gnupg nftables ufw python3 gh jq unzip || return 1
  if ! command -v node >/dev/null || (($(node -p 'process.versions.node.split(".")[0]') < 20)); then
    curl -fsSL https://deb.nodesource.com/setup_24.x | $S bash - && $S apt-get install -y -qq nodejs || return 1
  fi
  if [[ ! -x $HOME/.local/bin/rclone ]]; then
    local t; t=$(mktemp -d)
    curl -fsSL -o "$t/r.zip" https://downloads.rclone.org/rclone-current-linux-arm64.zip && unzip -q "$t/r.zip" -d "$t" || return 1
    install -D -m 755 "$t"/rclone-*/rclone "$HOME/.local/bin/rclone"; rm -rf "$t"
  fi
}

chk_swap() {
  local kb; kb=$(awk '$1=="/swapfile"{print $3}' /proc/swaps)
  [[ -n "$kb" ]] || { detail "no /swapfile active"; return 1; }
  grep -q '^/swapfile ' /etc/fstab || { detail "/swapfile not in fstab"; return 1; }
  detail "$((kb / 1024))M"
}
fix_swap() {
  $S fallocate -l "${SWAP_MB}M" /swapfile && $S chmod 600 /swapfile && $S mkswap /swapfile >/dev/null && $S swapon /swapfile || return 1
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' | $S tee -a /etc/fstab >/dev/null
}

chk_volume() {
  findmnt -n "$MOUNT" >/dev/null || { detail "$MOUNT is not mounted"; return 1; }
  local gb; gb=$(($(df -B1G --output=size "$MOUNT" | tail -1)))
  ((gb >= MIN_VOLUME_GB)) || { detail "${gb}G is under ${MIN_VOLUME_GB}G"; return 1; }
  [[ "$(findmnt -n -o FSTYPE "$MOUNT")" == ext4 ]] || { detail "not ext4"; return 1; }
  grep -E "[[:space:]]$MOUNT[[:space:]]" /etc/fstab | grep -q nofail || { detail "no nofail entry in fstab"; return 1; }
  detail "${gb}G"
}
# Formats only a whole disk with no filesystem or partitions, and only when exactly one exists.
fix_volume() {
  local disks n dev uuid
  disks=$(lsblk -bdnpo NAME,TYPE,SIZE | awk -v min=$((MIN_VOLUME_GB * 1024 * 1024 * 1024)) '$2=="disk" && $3>=min {print $1}')
  n=0
  for d in $disks; do
    [[ -z "$($S blkid "$d")" && $(lsblk -n "$d" | wc -l) == 1 ]] && { dev=$d; n=$((n + 1)); }
  done
  ((n == 1)) || { detail "found $n blank disks of ${MIN_VOLUME_GB}G or more, need exactly 1"; return 1; }
  $S mkfs.ext4 -q "$dev" || return 1
  uuid=$($S blkid -s UUID -o value "$dev")
  $S mkdir -p "$MOUNT"
  echo "UUID=$uuid $MOUNT ext4 defaults,_netdev,nofail 0 2" | $S tee -a /etc/fstab >/dev/null
  $S mount "$MOUNT"
}

chk_data_dirs() {
  [[ "$($S stat -c '%u %a' "$MOUNT/pg-telemetry" 2>/dev/null)" == "70 700" ]] || { detail "pg-telemetry must be uid 70, mode 700"; return 1; }
  [[ "$(stat -c %U "$MOUNT/backups" 2>/dev/null)" == "$(id -un)" ]] || { detail "backups must belong to $(id -un)"; return 1; }
}
fix_data_dirs() {
  $S install -d -o "$(id -un)" -g "$(id -gn)" "$MOUNT/backups" && $S install -d -o 70 -g 70 -m 700 "$MOUNT/pg-telemetry"
}

chk_sshd() {
  local t; t=$($S sshd -T 2>/dev/null)
  grep -qx 'passwordauthentication no' <<<"$t" || { detail "passwords allowed"; return 1; }
  grep -qx 'kbdinteractiveauthentication no' <<<"$t" || { detail "keyboard-interactive allowed"; return 1; }
  grep -qx 'pubkeyauthentication yes' <<<"$t" || { detail "keys not allowed"; return 1; }
  grep -qE '^permitrootlogin (no|without-password|prohibit-password)$' <<<"$t" || { detail "root may log in with a password"; return 1; }
}
# sshd keeps the first value it reads, so the 10- prefix beats the cloud image's files.
fix_sshd() {
  printf 'PermitRootLogin prohibit-password\nPasswordAuthentication no\nKbdInteractiveAuthentication no\nPubkeyAuthentication yes\n' |
    $S tee /etc/ssh/sshd_config.d/10-replica.conf >/dev/null
  $S sshd -t && $S systemctl reload ssh
}

chk_tailscale() {
  command -v tailscale >/dev/null || { detail "not installed"; return 1; }
  [[ "$(tailscale status --json 2>/dev/null | jq -r .BackendState)" == Running ]] || { detail "not logged in"; return 1; }
  detail "$(tailscale ip -4 | head -1)"
}
fix_tailscale() { command -v tailscale >/dev/null || curl -fsSL https://tailscale.com/install.sh | $S sh; }

chk_docker_daemon() {
  same_as /etc/docker/daemon.json "$(root_file /etc/docker/daemon.json)" || { detail "differs from the repo"; return 1; }
}
fix_docker_daemon() {
  install_root /etc/docker/daemon.json 644 || return 1
  ! systemctl is-active -q docker || $S systemctl restart docker
}

chk_coolify() {
  docker ps >/dev/null 2>&1 || { detail "cannot reach docker; if $(id -un) was just added to its group, log in again and rerun"; return 1; }
  running coolify || { detail "coolify container not running"; return 1; }
  local img; img=$(docker inspect -f '{{.Config.Image}}' coolify)
  detail "$img"
  [[ "$img" == *":$COOLIFY_VERSION" ]] || return 2
}
fix_coolify() {
  curl -fsSL https://cdn.coollabs.io/coolify/install.sh | $S bash
  $S usermod -aG docker "$(id -un)"
}

chk_ufw() {
  local s; s=$($S ufw status verbose 2>/dev/null)
  grep -q '^Status: active' <<<"$s" || { detail "inactive"; return 1; }
  grep -q 'deny (incoming)' <<<"$s" || { detail "incoming not denied by default"; return 1; }
  local want bridge="br-$(docker network inspect coolify -f '{{.Id}}' 2>/dev/null | cut -c1-12)"
  for want in '^Anywhere on tailscale0 +ALLOW IN' '^80/tcp +ALLOW IN' '^443/tcp +ALLOW IN' '^41641/udp +ALLOW IN' \
    '^22/tcp on docker0 +ALLOW IN' "^22/tcp on $bridge +ALLOW IN"; do
    grep -qE "$want" <<<"$s" || { detail "no rule $want"; return 1; }
  done
  if grep -qE '^22(/tcp)? +ALLOW IN' <<<"$s"; then detail "22 open to the internet"; return 1; fi
}
# Runs after tailscale, so SSH over the tailnet survives the public 22 closing.
fix_ufw() {
  chk_tailscale || { detail "refusing: tailscale is not up and ufw would close public SSH"; return 1; }
  local bridge="br-$(docker network inspect coolify -f '{{.Id}}' | cut -c1-12)"
  $S ufw default deny incoming && $S ufw default allow outgoing && $S ufw default deny routed || return 1
  $S ufw allow in on tailscale0 && $S ufw allow 80/tcp && $S ufw allow 443/tcp && $S ufw allow 41641/udp || return 1
  $S ufw allow in on docker0 to any port 22 proto tcp comment 'coolify localhost SSH' || return 1
  $S ufw allow in on "$bridge" to any port 22 proto tcp comment 'coolify localhost SSH' || return 1
  $S ufw --force enable
}

# A box with no lock rules is only as closed as its cloud firewall, which no check on the box can see.
chk_ports_lock() {
  local rules port open="" missing=""
  rules=$($S iptables -S DOCKER-USER 2>/dev/null)
  for port in $COOLIFY_PORTS; do
    grep -q "ctorigdstport $port .*-j DROP" <<<"$rules" || missing+=" $port"
    ss -ltnH "sport = :$port" | grep -q . && open+=" $port"
  done
  [[ -n "$missing" ]] || { detail "dropped on the public interface"; return 0; }
  [[ -n "$open" ]] || { detail "nothing listens on $COOLIFY_PORTS"; return 0; }
  detail "listening on all addresses:$open; no host rule, so only the cloud firewall closes them. Prove it from outside: nc -zv <public-ip> 8000"
  return 2
}
fix_ports_lock() {
  install_root /usr/local/sbin/coolify-ports-lock 755 && install_root /etc/systemd/system/coolify-ports-lock.service 644 || return 1
  $S systemctl daemon-reload && $S systemctl enable --now coolify-ports-lock.service
}

chk_nft_cve() {
  same_as /etc/nft-cve-2026-98432.nft "$(root_file /etc/nft-cve-2026-98432.nft)" || { detail "rule file differs"; return 1; }
  same_as /etc/systemd/system/nft-cve-2026-98432.service "$(root_file /etc/systemd/system/nft-cve-2026-98432.service)" || { detail "unit differs"; return 1; }
  systemctl is-enabled -q nft-cve-2026-98432 || { detail "not enabled at boot"; return 1; }
  $S nft list table ip cve98432 2>/dev/null | grep -q 'ip hdrlength != 5 .*drop' || { detail "rule not live"; return 1; }
}
fix_nft_cve() {
  install_root /etc/nft-cve-2026-98432.nft 644 && install_root /etc/systemd/system/nft-cve-2026-98432.service 644 || return 1
  $S systemctl daemon-reload && $S systemctl enable --now nft-cve-2026-98432
}

chk_kernel_fix() {
  local f
  for f in /usr/local/sbin/kernel-fix-check /etc/systemd/system/kernel-fix-check.service /etc/systemd/system/kernel-fix-check.timer; do
    same_as "$f" "$(root_file "$f")" || { detail "$f differs"; return 1; }
  done
  systemctl is-active -q kernel-fix-check.timer || { detail "timer not active"; return 1; }
  systemctl is-enabled -q kernel-fix-check.timer || { detail "timer not enabled"; return 1; }
}
fix_kernel_fix() {
  install_root /usr/local/sbin/kernel-fix-check 755 && install_root /etc/systemd/system/kernel-fix-check.service 644 &&
    install_root /etc/systemd/system/kernel-fix-check.timer 644 || return 1
  $S systemctl daemon-reload && $S systemctl enable --now kernel-fix-check.timer
}

chk_heartbeat() { same_as /etc/cron.d/idle-heartbeat "$(root_file /etc/cron.d/idle-heartbeat)" || { detail "cron differs"; return 1; }; }
fix_heartbeat() { install_root /etc/cron.d/idle-heartbeat 644; }

chk_proxy() {
  local f=/data/coolify/proxy/docker-compose.yml want="--entrypoints.https.transport.respondingTimeouts.idleTimeout=$IDLE_TIMEOUT"
  $S grep -qF -- "$want" "$f" || { detail "$f lacks idleTimeout=$IDLE_TIMEOUT"; return 1; }
  $S grep -qF -- '--entrypoints.https.http3' "$f" || { detail "$f lacks http3"; return 1; }
  $S grep -qF -- "'443:443/udp'" "$f" || { detail "$f does not publish 443/udp"; return 1; }
  docker inspect coolify-proxy -f '{{json .Args}}' 2>/dev/null | grep -qF -- "$want" || { detail "live proxy lacks idleTimeout=$IDLE_TIMEOUT; restart it"; return 1; }
  # Coolify rewrites the file from its database when it restarts the proxy.
  [[ "$(coolify_sql "select count(*) from servers where proxy::text like '%idleTimeout=$IDLE_TIMEOUT%'")" -ge 1 ]] ||
    { detail "Coolify's saved proxy config lacks idleTimeout=$IDLE_TIMEOUT"; return 1; }
}

chk_postgres() {
  local c; c=$(db_uuid)
  [[ -n "$c" ]] || { detail "no Postgres in Coolify"; return 1; }
  running "$c" || { detail "container not running"; return 1; }
  [[ "$(docker inspect -f '{{.Config.Image}}' "$c")" == postgres:18-* ]] || { detail "not postgres 18"; return 1; }
  [[ "$(docker inspect -f '{{json .HostConfig.PortBindings}}' "$c")" == "{}" ]] || { detail "published on the host"; return 1; }
  ss -ltnH 'sport = :5432' | grep -qE '(0\.0\.0\.0|\[::\]|\*):5432' && { detail "5432 listens publicly"; return 1; }
  docker inspect -f '{{range .Mounts}}{{.Source}}={{.Destination}} {{end}}' "$c" | grep -q "$MOUNT/pg-telemetry=/mnt/telemetry" || { detail "telemetry mount missing"; return 1; }
  local q='select (select count(*) from pg_tablespace where spcname=$$telemetry$$) || $$ $$ || (select count(*) from pg_extension where extname=$$pg_stat_statements$$)'
  [[ "$(docker exec "$c" sh -c "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -Atc '$q'" 2>/dev/null)" == "1 1" ]] ||
    { detail "telemetry tablespace or pg_stat_statements missing"; return 1; }
}

chk_app() {
  local row names missing="" n
  row=$(coolify_sql "select git_repository||'|'||git_branch||'|'||build_pack||'|'||ports_exposes||'|'||dockerfile_location from applications order by id limit 1")
  [[ "$row" == "jsongalvez/company_app|master|dockerfile|3023|/backend/Dockerfile" ]] || { detail "Coolify app settings differ: ${row:-none}"; return 1; }
  names=$(coolify_sql "select distinct key from environment_variables where not is_preview")
  for n in $APP_ENV_NAMES; do grep -qx "$n" <<<"$names" || missing+=" $n"; done
  [[ -z "$missing" ]] || { detail "env names missing:$missing"; return 1; }
  local c ip; c=$(app_container)
  [[ -n "$c" ]] || { detail "app container not running"; return 1; }
  ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' "$c" | awk '{print $1}')
  curl -sf -m5 "http://$ip:3023/health" | grep -q '"UP"' || { detail "/health not UP"; return 1; }
  detail "$(coolify_sql "select fqdn from applications order by id limit 1")"
}

chk_env_files() {
  local e f names n missing=""
  for e in $ENV_FILES; do
    f=$HOME/.config/company-app-${e%%:*}.env
    [[ -f "$f" ]] || { missing+=" ${e%%:*}.env"; continue; }
    [[ "$(stat -c %a "$f")" == 600 ]] || { detail "$f must be mode 600"; return 1; }
    names=$(sed -nE 's/^(export )?([A-Z0-9_]+)=.*/\2/p' "$f")
    local want=${e#*:}
    for n in ${want//,/ }; do grep -qx "$n" <<<"$names" || missing+=" $n"; done
  done
  [[ -z "$missing" ]] || { detail "missing:$missing"; return 1; }
}

chk_ops_files() {
  [[ -n "$(db_uuid)" && -n "$(app_uuid)" ]] || { detail "Coolify has no app or database yet; run the wizard first"; return 1; }
  local dest src drift=""
  while IFS=$'\t' read -r dest src; do
    same_as "$dest" "$src" || drift+=" ${dest#"$HOME"/}"
  done < <(home_files)
  [[ "$(readlink -f "$HOME/.local/bin/perf-slow")" == "$HOME/.local/share/company-app-perf/perf-slow" ]] || drift+=" .local/bin/perf-slow"
  [[ -z "$drift" ]] || { detail "differ:$drift"; return 1; }
}
fix_ops_files() {
  [[ -n "$(db_uuid)" && -n "$(app_uuid)" ]] || return 1
  local dest src mode
  while IFS=$'\t' read -r dest src; do
    mode=$(stat -c %a "$src")
    mkdir -p "$(dirname "$dest")"
    render "$src" >"$dest" && chmod "$mode" "$dest"
  done < <(home_files)
  ln -sf "$HOME/.local/share/company-app-perf/perf-slow" "$HOME/.local/bin/perf-slow"
}

chk_timers() {
  [[ "$(loginctl show-user "$(id -un)" -p Linger --value)" == yes ]] || { detail "linger off"; return 1; }
  id -nG | grep -qw docker || { detail "$(id -un) not in the docker group"; return 1; }
  local t off=""
  for t in $USER_TIMERS; do
    systemctl --user is-active -q "$t.timer" && systemctl --user is-enabled -q "$t.timer" || off+=" $t"
  done
  systemctl --user is-active -q company-app-logs.service || off+=" company-app-logs"
  [[ -z "$off" ]] || { detail "off:$off"; return 1; }
}
fix_timers() {
  $S loginctl enable-linger "$(id -un)" || return 1
  local t
  systemctl --user daemon-reload
  for t in $USER_TIMERS; do systemctl --user enable --now "$t.timer" || return 1; done
  systemctl --user enable --now company-app-logs.service
}

chk_backup_recent() {
  local p r ts age
  p=$(systemctl --user show company-app-db-backup.service -p Result -p ExecMainExitTimestamp)
  r=$(sed -n 's/^Result=//p' <<<"$p"); ts=$(sed -n 's/^ExecMainExitTimestamp=//p' <<<"$p")
  [[ -n "$ts" ]] || { detail "has not run yet"; return 2; }
  age=$((($(date +%s) - $(date -d "$ts" +%s)) / 3600))
  detail "${age}h ago, $r"
  [[ "$r" == success ]] || return 1
  ((age < 26)) || return 2
}

refuse_prod() {
  if [[ "$(hostname)" == "$PROD_HOSTNAME" ]] || ip -4 -br addr 2>/dev/null | grep -qF "$PROD_TAILSCALE_IP/"; then
    echo "refusing: this is the prod VPS. apply only runs on a new box." >&2
    exit 3
  fi
}

report() { printf '%-5s %-14s %s%s\n' "$1" "$2" "$3" "${DETAIL:+ ($DETAIL)}"; }

# Runs one item's check. Echoes nothing; sets DETAIL, returns 0 pass, 1 fail, 2 warn.
run_check() { DETAIL=""; "chk_$1"; }

main() {
  local mode=${1:-} strict=0 entry name owner what rc fails=0 warns=0
  [[ "${2:-}" == --strict ]] && strict=1
  case "$mode" in check | plan) ;; apply) refuse_prod ;; *) sed -n 2,7p "$0"; exit 2 ;; esac
  for entry in "${ITEMS[@]}"; do
    IFS='|' read -r name owner what <<<"$entry"
    run_check "$name"; rc=$?
    case "$mode:$rc" in
      *:0) report PASS "$name" "$what" ;;
      check:2) report WARN "$name" "$what"; warns=$((warns + 1)) ;;
      check:*) report FAIL "$name" "$what"; fails=$((fails + 1)) ;;
      plan:*)
        case "$owner" in
          agent) report "DO" "$name" "$what" ;;
          user) report "USER" "$name" "$what: wizard.sh" ;;
          *) report "NOTE" "$name" "$what" ;;
        esac ;;
      apply:*)
        if [[ "$owner" == agent ]]; then
          "fix_$name" || true
          run_check "$name"; rc=$?
          if ((rc == 1)); then report FAIL "$name" "$what"; echo "stopped at $name" >&2; return 1; fi
          report "DONE" "$name" "$what"
        else
          [[ "$owner" == user ]] && declare -F "fix_$name" >/dev/null && { "fix_$name" || true; run_check "$name"; }
          report "$(tr a-z A-Z <<<"$owner")" "$name" "$what: wizard.sh"
        fi ;;
    esac
  done
  [[ "$mode" == check ]] || return 0
  echo "$fails failed, $warns warnings"
  ((fails == 0)) && ((strict == 0 || warns == 0))
}

[[ -n "${REPLICATE_SOURCE_ONLY:-}" ]] || main "$@"
