#!/usr/bin/env bash
# 停掉本机 RocketMQ
set -euo pipefail

RMQ_HOME="${RMQ_HOME:-$HOME/rocketmq/rocketmq-4.9.7}"
export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/corretto-1.8.0_372/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ -x "$RMQ_HOME/bin/mqshutdown" ]]; then
  sh "$RMQ_HOME/bin/mqshutdown" broker || true
  sh "$RMQ_HOME/bin/mqshutdown" namesrv || true
else
  pkill -f org.apache.rocketmq.broker.BrokerStartup || true
  pkill -f org.apache.rocketmq.namesrv.NamesrvStartup || true
fi
echo "已请求关闭 NameServer / Broker"
