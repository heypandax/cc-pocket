#!/usr/bin/env bash
# 更新 orbix（Linux + systemd --user）的 cc-pocket daemon + voice agent。
set -euo pipefail

REPO="$HOME/workspace/cc-pocket"
RELAY="ws://cc.dmitt.com:6002"
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
JAVA_OPTS_CAP="-Xmx1024m -XX:MaxMetaspaceSize=256m"
DEST="$HOME/.local/share/cc-pocket-daemon"
UNIT="$HOME/.config/systemd/user/cc-pocket-daemon.service"

export JAVA_HOME
export XDG_RUNTIME_DIR="/run/user/$(id -u)"

echo "── 0/5 确保 systemd --user 管理器活着 ──"
if ! systemctl --user status >/dev/null 2>&1; then
  echo "   user@$(id -u).service 没在跑，尝试拉起来"
  if [ "$(id -u)" -eq 0 ]; then
    systemctl start "user@$(id -u).service"
  else
    sudo systemctl start "user@$(id -u).service"
  fi
  sleep 2
fi

echo "── 1/6 构建 daemon（installDist）──"
cd "$REPO"
./gradlew :daemon:installDist -q

echo "── 2/6 构建 voice agent ──"
cd "$REPO/voice-agent"
npm install --production
npx tsc

echo "── 3/6 安装到 $DEST ──"
rm -rf "$DEST"; mkdir -p "$DEST"
cp -R daemon/build/install/cc-pocket-daemon/bin daemon/build/install/cc-pocket-daemon/lib "$DEST/"
# 安装 voice agent（只复制运行时需要的文件）
cp -R "$REPO/voice-agent/dist" "$DEST/voice-agent/"
cp "$REPO/voice-agent/package.json" "$DEST/voice-agent/"
cp -R "$REPO/voice-agent/node_modules" "$DEST/voice-agent/"
# .env 单独处理：如果已存在则不覆盖（保护 API key）
if [ ! -f "$DEST/voice-agent/.env" ]; then
  cp "$REPO/voice-agent/.env" "$DEST/voice-agent/"
fi

echo "── 4/6 停掉旧实例（保证单实例）──"
systemctl --user stop cc-pocket-daemon 2>/dev/null || true
pkill -f 'cc-pocket-daemon.*MainKt' 2>/dev/null || true
lsof -ti :8799 2>/dev/null | xargs -r kill -9 2>/dev/null || true
sleep 1

echo "── 5/6 写 systemd unit（带内存上限 + voice agent）──"
mkdir -p "$(dirname "$UNIT")"
cat > "$UNIT" <<EOF
[Unit]
Description=cc-pocket daemon
After=network-online.target

[Service]
ExecStart=$DEST/bin/cc-pocket-daemon run --relay $RELAY
WorkingDirectory=$DEST
Environment=PATH=$PATH
Environment=JAVA_OPTS=$JAVA_OPTS_CAP
Environment=CC_POCKET_HOME=$DEST
Restart=always
RestartSec=5
StartLimitIntervalSec=120
StartLimitBurst=6

MemoryHigh=1200M
MemoryMax=1300M

[Install]
WantedBy=default.target
EOF
rm -rf "$UNIT.d"
systemctl --user daemon-reload
systemctl --user enable --now cc-pocket-daemon

echo "── 6/6 校验：单实例 + relay 已连 ──"
sleep 6
COUNT="$(pgrep -f 'cc-pocket-daemon.*MainKt' | wc -l | tr -d ' ')"
PID="$(systemctl --user show cc-pocket-daemon -p MainPID --value)"

RELAY_PORT=$(echo "$RELAY" | grep -oE ':[0-9]+' | tr -d ':' || echo "443")

SOCK=0; [ -n "$PID" ] && [ "$PID" != "0" ] && SOCK="$(lsof -nP -p "$PID" 2>/dev/null | grep -c ":$RELAY_PORT.*ESTABLISHED" || true)"
echo "   daemon 进程数=$COUNT  pid=$PID  relay-socket($RELAY_PORT)=$SOCK"
systemctl --user is-active cc-pocket-daemon

if [ "$COUNT" = "1" ] && [ "$SOCK" -ge 1 ]; then
  echo "✅ 完成：单实例已连上 $RELAY"
  echo ""
  echo "── Voice Agent ──"
  VA_STATUS="$("$DEST/bin/cc-pocket-daemon" voice-agent --action status 2>&1 || true)"
  echo "$VA_STATUS"
  echo ""
  echo "启动 voice agent:  cc-pocket-daemon voice-agent --action start"
else
  echo "⚠️  未达预期。看日志：journalctl --user -u cc-pocket-daemon -n 40"
  exit 1
fi
