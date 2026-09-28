#!/usr/bin/env bash
# 本机启动 RocketMQ 4.9.x（不用 Docker）
# 用法：./start-local.sh
set -euo pipefail

RMQ_HOME="${RMQ_HOME:-$HOME/rocketmq/rocketmq-4.9.7}"
# RocketMQ 4.9 建议 JDK 8
export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/corretto-1.8.0_372/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ ! -d "$RMQ_HOME/bin" ]]; then
  echo "未找到 $RMQ_HOME 。请先解压 rocketmq-all-4.9.7-bin-release 到该目录。"
  exit 1
fi
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "JAVA_HOME 无效: $JAVA_HOME （请改成本机 JDK8 路径）"
  exit 1
fi

CONF="$RMQ_HOME/conf/broker-local.conf"
if [[ ! -f "$CONF" ]]; then
  ROOT="$(cd "$(dirname "$0")" && pwd)"
  cp "$ROOT/broker.conf" "$CONF"
fi

mkdir -p "$RMQ_HOME/logs" "$HOME/rocketmq/store"

echo "JAVA_HOME=$JAVA_HOME"
echo "RMQ_HOME=$RMQ_HOME"
echo "启动 NameServer :9876 ..."
nohup sh "$RMQ_HOME/bin/mqnamesrv" >"$RMQ_HOME/logs/namesrv.log" 2>&1 &

wait_port() {
  local port=$1
  local name=$2
  local i=0
  while (( i < 60 )); do
    if nc -z 127.0.0.1 "$port" >/dev/null 2>&1; then
      echo "  $name OK :$port"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  echo "  $name 未就绪，看 $RMQ_HOME/logs/"
  return 1
}

wait_port 9876 "NameServer" || true
echo "启动 Broker（冷启动可能较慢）..."
nohup sh "$RMQ_HOME/bin/mqbroker" -n 127.0.0.1:9876 -c "$CONF" autoCreateTopicEnable=true \
  >"$RMQ_HOME/logs/broker.log" 2>&1 &
wait_port 10911 "Broker" || echo "  详见 $RMQ_HOME/logs/broker.log"
echo "trade 配置 rocketmq.name-server=127.0.0.1:9876"
