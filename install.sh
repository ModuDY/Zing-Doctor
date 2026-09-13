#!/usr/bin/env bash
# =====================================================================
# zing-doctor 医生系统 - 一键安装部署脚本
#
# 用法：将交付包解压后，在 zing-doctor 根目录执行：
#     bash install.sh
#
# 部署路径 = 脚本所在目录（当前解压路径），不写死任何绝对路径。
#
# 流程：
#   1) 环境检查（docker / docker compose / 达梦连通）
#   2) 达梦数据库初始化（统一用 SYSDBA，自动选择通道，顺序尝试）：
#        00_init_user.sql：CREATE SCHEMA "zing_doctor_db_prod" AUTHORIZATION SYSDBA（建医生系统自有模式）
#        01_schema.sql   ：建 4 张业务表（SQL 内显式 "zing_doctor_db_prod"."xxx" 模式前缀）
#        02_seed.sql     ：初始化页面注册数据
#       通道：
#        a. 本机 disql 客户端
#        b. 达梦 Docker 容器（docker exec 内嵌 disql）
#        c. 本机 JDK + 内置 JDBC 初始化工具（tools/db-init）
#      全部通道不可用时，打印手动初始化指引并退出。
#   3) 构建并启动后端 + 前端容器（自动选择部署模式）：
#        - 外网可达 Docker Hub      → Docker Compose 全量（backend + frontend 容器）
#        - 内网无法拉基础镜像       → 内网直连：后端 java -jar（复用服务器 Java8）+ 前端 nginx stable 容器
#   4) 输出验证与外链接入方式
# =====================================================================
set -e

# ---------- 基础 ----------
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

RED='\033[31m'; GREEN='\033[32m'; YELLOW='\033[33m'; NC='\033[0m'
info(){ echo -e "${GREEN}[INFO]${NC} $*"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $*"; }
err(){ echo -e "${RED}[ERROR]${NC} $*"; }

echo "=================================================="
echo "  zing-doctor 医生系统 · 一键安装部署"
echo "  部署路径：$ROOT"
echo "=================================================="

# ---------- 1. 环境检查 ----------
command -v docker >/dev/null 2>&1 || { err "未安装 Docker，请先安装"; exit 1; }

# 定位 docker compose：优先系统自带，其次使用交付包内置二进制（无需服务器联网安装）
if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
  info "Docker 环境：$COMPOSE"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
  info "Docker 环境：$COMPOSE"
elif [ -f "tools/docker-compose/docker-compose-linux-x86_64" ] \
     || [ -f "tools/docker-compose/docker-compose-linux-aarch64" ]; then
  ARCH="$(uname -m)"
  case "$ARCH" in
    x86_64|amd64) DC_BIN="tools/docker-compose/docker-compose-linux-x86_64" ;;
    aarch64|arm64) DC_BIN="tools/docker-compose/docker-compose-linux-aarch64" ;;
    *) err "暂不支持该服务器架构: $ARCH（当前内置 x86_64 / aarch64 两种 compose）"; exit 1 ;;
  esac
  info "使用交付包内置 docker-compose（架构 $ARCH），安装到 /usr/local/bin/docker-compose ..."
  install -m 0755 "$DC_BIN" /usr/local/bin/docker-compose
  COMPOSE="docker-compose"
  info "Docker 环境：$COMPOSE"
else
  err "未找到 docker compose / docker-compose，且交付包未内置 compose 二进制"
  err "请安装后重试：curl -L \"https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64\" -o /usr/local/bin/docker-compose && chmod +x /usr/local/bin/docker-compose"
  exit 1
fi

# 从 docker-compose.yml 提取达梦连接
extract(){ grep -oP "$1" docker-compose.yml | head -1; }
DM_HOST_PORT="$(extract 'DOCTOR_URL: jdbc:dm://\K[^ ]+')"
# 统一使用 SYSDBA 连接（与 ICU 系统访问方式一致），通过显式模式前缀访问 zing_doctor_db_prod
ADMIN_USER="$(extract 'DOCTOR_USERNAME: \K\S+')"
ADMIN_PASS="$(extract 'DOCTOR_PASSWORD: \K\S+')"
[ -z "$ADMIN_USER" ] && { ADMIN_USER="SYSDBA"; ADMIN_PASS="Sa_20250815"; }
if [ -z "$DM_HOST_PORT" ]; then
  err "docker-compose.yml 中未找到达梦地址（DOCTOR_URL），请先按 docs/04-安装部署手册.md 配置"
  exit 1
fi
info "达梦：$DM_HOST_PORT  连接账号：$ADMIN_USER（目标模式 zing_doctor_db_prod，显式前缀访问）"

# ---------- 2. 达梦数据库初始化 ----------
# 统一用 SYSDBA 执行（建模式 zing_doctor_db_prod → 建表 → 种子数据）：
#   00_init_user.sql：CREATE SCHEMA "zing_doctor_db_prod" AUTHORIZATION SYSDBA
#   01_schema.sql   ：建表（SQL 内显式 "zing_doctor_db_prod"."xxx" 模式前缀）
#   02_seed.sql     ：初始化页面注册数据
init_db() {
  local logfile=/tmp/zing-dbinit.log
  local DISQL=""
  local p=""

  # 通道 a：本机 disql（扩展搜索路径 + find 兜底）
  command -v disql >/dev/null 2>&1 && DISQL="disql"
  if [ -z "$DISQL" ]; then
    for p in /opt/dmdbms/bin/disql /dm8/bin/disql /opt/dm8/bin/disql /dmdbms/bin/disql \
             /home/dmdba/dmdbms/bin/disql /usr/local/dmdbms/bin/disql \
             /data/dmdbms/bin/disql /app/dmdbms/bin/disql /usr/local/bin/disql; do
      [ -x "$p" ] && { DISQL="$p"; break; }
    done
  fi
  # find 兜底搜索（限时3秒，避免卡住）
  if [ -z "$DISQL" ]; then
    DISQL="$(timeout 3 find / -name disql -type f 2>/dev/null | head -1)"
    [ -n "$DISQL" ] && info "通过 find 定位 disql: $DISQL"
  fi
  # 表已存在则跳过初始化（重复部署场景，避免报错）
  if [ -n "$DISQL" ]; then
    local _exist="$(echo "SELECT COUNT(*) FROM all_tables WHERE owner='ZING_DOCTOR_DB_PROD' AND table_name='ZING_PAGE_CONFIG';" \
         | "$DISQL" "$ADMIN_USER/$ADMIN_PASS@$DM_HOST_PORT" 2>/dev/null \
         | grep -oE '[0-9]+' | tail -1)"
    if [ "$_exist" = "1" ]; then
      info "检测到 zing_doctor_db_prod.zing_page_config 表已存在，跳过数据库初始化（如需重建请先 DROP SCHEMA）"
      warn "老库升级：请手动执行增量脚本（只执行一次）sql/06_abx_drug_dict.sql，否则抗菌药识别词库刷新会持续告警“无效的表或视图名[zing_abx_drug_dict]”"
      warn "老库升级：使用质控指标中台需手动执行 sql/09_quality.sql（幂等），否则质控看板/月度汇总页会因表不存在而报错，且 quality-board / quality-monthly 未注册导致外链被拒"
      return 0
    fi
  fi

  if [ -n "$DISQL" ]; then
    info "通道 a：本机 disql 初始化达梦（建模式+建表+种子）..."
    if cat "$ROOT/sql/00_init_user.sql" "$ROOT/sql/01_schema.sql" "$ROOT/sql/02_seed.sql" "$ROOT/sql/06_abx_drug_dict.sql" "$ROOT/sql/07_sofa.sql" "$ROOT/sql/08_sofa_p1.sql" "$ROOT/sql/09_quality.sql" \
         | "$DISQL" "$ADMIN_USER/$ADMIN_PASS@$DM_HOST_PORT" >"$logfile" 2>&1; then
      info "达梦初始化完成（本机 disql，模式 zing_doctor_db_prod）"; return 0
    fi
    warn "本机 disql 执行失败，尝试其他通道（详见 $logfile）"
  fi

  # 通道 b：达梦 Docker 容器内 disql
  local CID="$(docker ps --format '{{.Names}} {{.Image}}' 2>/dev/null | grep -iE 'dm8|dameng' | head -1 | awk '{print $1}')"
  if [ -n "$CID" ]; then
    info "通道 b：达梦容器 $CID 初始化..."
    for p in /opt/dmdbms/bin/disql /dm8/bin/disql /opt/dm8/bin/disql; do
      if docker exec "$CID" test -x "$p" 2>/dev/null; then
        if cat "$ROOT/sql/00_init_user.sql" "$ROOT/sql/01_schema.sql" "$ROOT/sql/02_seed.sql" "$ROOT/sql/06_abx_drug_dict.sql" "$ROOT/sql/07_sofa.sql" "$ROOT/sql/08_sofa_p1.sql" "$ROOT/sql/09_quality.sql" \
             | docker exec -i "$CID" "$p" "$ADMIN_USER/$ADMIN_PASS@$DM_HOST_PORT" >"$logfile" 2>&1; then
          info "达梦初始化完成（容器 $CID，模式 zing_doctor_db_prod）"; return 0
        fi
        warn "达梦容器通道失败，尝试 JDBC 通道（详见 $logfile）"
        break
      fi
    done
  fi

  # 通道 c：本机 JDK + JDBC 初始化工具
  # 优先使用交付包预编译的 tools/db-init/DbInit.class（服务器无需 javac），
  # 其次用 tools/db-init-classes/，最后才尝试现场编译（需 javac）
  if command -v java >/dev/null 2>&1 && [ -f "lib/DmJdbcDriver18-8.1.3.140.jar" ]; then
    local _cp=""
    if [ -f "tools/db-init/DbInit.class" ]; then
      _cp="tools/db-init"
    elif [ -f "tools/db-init-classes/DbInit.class" ]; then
      _cp="tools/db-init-classes"
    elif [ -f "tools/db-init/DbInit.java" ] && command -v javac >/dev/null 2>&1; then
      info "通道 c：现场编译 JDBC 初始化工具..."
      mkdir -p tools/db-init-classes
      javac -encoding UTF-8 -cp "lib/DmJdbcDriver18-8.1.3.140.jar" \
        -d tools/db-init-classes tools/db-init/DbInit.java && _cp="tools/db-init-classes"
    fi
    if [ -n "$_cp" ]; then
      info "通道 c：运行 JDBC 初始化工具连接达梦（classpath: $_cp）..."
      if java -cp "lib/DmJdbcDriver18-8.1.3.140.jar:$_cp" \
              DbInit "jdbc:dm://$DM_HOST_PORT" "$ADMIN_USER" "$ADMIN_PASS" \
              "$ROOT/sql/00_init_user.sql" "$ROOT/sql/01_schema.sql" "$ROOT/sql/02_seed.sql" \
              "$ROOT/sql/03_icu_indexes.sql" "$ROOT/sql/05_apache2_pdf.sql" "$ROOT/sql/06_abx_drug_dict.sql" \
              "$ROOT/sql/07_sofa.sql" "$ROOT/sql/08_sofa_p1.sql" "$ROOT/sql/09_quality.sql"; then
        info "达梦初始化完成（JDBC 工具，模式 zing_doctor_db_prod + ICU 库性能索引 + APACHE2 PDF列）"; return 0
      fi
      warn "JDBC 工具执行失败（详见上方日志）"
    else
      warn "通道 c：未找到 DbInit.class 且无 javac，跳过 JDBC 通道"
    fi
  fi

  # 全部通道失败
  err "自动初始化通道均不可用，请手动在能连达梦的机器上执行："
  echo "    disql $ADMIN_USER/$ADMIN_PASS@$DM_HOST_PORT"
  echo "    start $ROOT/sql/00_init_user.sql"
  echo "    start $ROOT/sql/01_schema.sql"
  echo "    start $ROOT/sql/02_seed.sql"
  echo "    start $ROOT/sql/03_icu_indexes.sql  # ICU 库性能优化索引（可选，建议执行）"
  echo "    start $ROOT/sql/05_apache2_pdf.sql  # APACHE2 评分文书PDF列（老环境升级执行，幂等可重复）"
  echo "    start $ROOT/sql/06_abx_drug_dict.sql # 抗菌药物字典表（HIS 抗菌药同步副本，只执行一次）"
  echo "    start $ROOT/sql/07_sofa.sql         # SOFA 评分建表 + 页面注册 + 配置种子"
  echo "    start $ROOT/sql/08_sofa_p1.sql      # SOFA 页面注册与列注释（幂等可重复）"
  echo "    start $ROOT/sql/09_quality.sql      # 质控指标中台建表 + 页面注册（幂等可重复）"
  exit 1
}

# 表已存在（重复部署）时允许跳过初始化
if [ "$1" = "--skip-db" ]; then
  warn "跳过数据库初始化（--skip-db）"
else
  init_db
fi

# ---------- 3. 构建并启动 ----------
# ⚠️ 前置校验：前端静态产物必须存在。
# frontend/dist 以 bind mount 挂成 nginx 的 /usr/share/nginx/html；若它为空（缺 index.html），
# nginx 的 try_files 会回退到不存在的 /index.html，触发
#   "rewrite or internal redirection cycle while internally redirecting to /index.html"
# 表现为：GET / 返回 403、GET /page/xxx 返回 500。
# （线上曾因交付包中 frontend/dist 为空，出现"部分页面能打开、其余 500"的假象——
#   能打开的其实只是浏览器缓存里的旧 index.html，与页面本身无关。）
if [ ! -f "$ROOT/frontend/dist/index.html" ]; then
  err "缺少前端静态产物：$ROOT/frontend/dist/index.html 不存在（frontend/dist 为空）"
  err "nginx 会对所有页面返回 403/500，已中止部署。修复方式："
  err "  1) 交付包已含 frontend/dist，请确认解压完整；增量更新用"
  err "     cp -r <新包>/frontend/dist/. $ROOT/frontend/dist/"
  err "     （不要 rm -rf frontend/dist，bind mount 换 inode 后容器仍看到旧空目录）"
  err "  2) 或本机构建后同步：cd frontend && npm install && npm run build"
  exit 1
fi

# 内网直连部署：后端 java -jar（复用服务器 Java 8）+ 前端 nginx stable 容器（复用已有镜像）
# 适用：服务器无外网 / 无法拉取 Docker Hub 基础镜像（openjdk/maven/node）时自动使用
direct_deploy() {
  info "无法访问 Docker Hub，改用内网直连部署（后端 java -jar + 前端 nginx 容器）"

  # ---- 后端：java -jar ----
  command -v java >/dev/null 2>&1 || { err "需要 Java 8 运行后端，未找到 java 命令"; return 1; }
  JAR="$ROOT/app/zing-doctor.jar"
  [ -f "$JAR" ] || { err "缺少 $JAR（后端可执行包），请确认交付包完整（含 app/zing-doctor.jar）"; return 1; }

  # 停止旧后端进程（重复部署）
  if [ -f "$ROOT/backend.pid" ] && kill -0 "$(cat "$ROOT/backend.pid")" 2>/dev/null; then
    info "停止旧后端进程 PID $(cat "$ROOT/backend.pid") ..."
    kill "$(cat "$ROOT/backend.pid")" 2>/dev/null; sleep 3
  fi
  pkill -f "zing-doctor.jar" 2>/dev/null && sleep 2 || true

  mkdir -p "$ROOT/logs"
  # 本机 IP（用于 nginx 反代后端地址）
  ALL_IPS="$(hostname -I 2>/dev/null | tr ' ' '\n' | grep -v '^$' | tr '\n' ' ')"
  HOST_IP="$(echo "$ALL_IPS" | awk '{print $1}')"
  [ -z "$HOST_IP" ] && HOST_IP="127.0.0.1"
  # 外链跳转地址默认取外链请求自带的 Host（跟随外部系统访问地址，不依赖本机网卡 IP）；
  # 仅当需要强制固定跳转地址时才显式设置 EXTERNAL_LINK_BASE_URL，例如：
  #   EXTERNAL_LINK_BASE_URL=http://100.120.1.104:2001 ./install.sh
  info "启动后端：java -jar $JAR（ICU_DATA_PROVIDER=sql，连接真实 ICU 库）..."
  ICU_DATA_PROVIDER=sql \
  EXTERNAL_LINK_SECRET="${EXTERNAL_LINK_SECRET:-zing-doctor-prod-secret-change-me}" \
  ICU_LINK_TOKEN="${ICU_LINK_TOKEN:-zing-icu-link-token-2026}" \
  EXTERNAL_LINK_BASE_URL="${EXTERNAL_LINK_BASE_URL:-}" \
  nohup java -jar "$JAR" > "$ROOT/logs/backend.log" 2>&1 &
  echo $! > "$ROOT/backend.pid"
  info "后端 PID: $(cat "$ROOT/backend.pid")（日志：logs/backend.log）"
  if [ -n "${EXTERNAL_LINK_BASE_URL:-}" ]; then
    info "外链跳转基础地址（显式配置）：EXTERNAL_LINK_BASE_URL=${EXTERNAL_LINK_BASE_URL}"
  else
    info "外链跳转地址：跟随外部系统访问地址（请求 Host），无需配置 EXTERNAL_LINK_BASE_URL"
  fi

  # ---- 前端：nginx stable 容器（复用服务器已有镜像，不拉取）----
  if docker image inspect nginx:stable >/dev/null 2>&1; then
    mkdir -p "$ROOT/runtime/nginx"
    cat > "$ROOT/runtime/nginx/default.conf" <<EOF
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    # 放开请求体上限：评分文书 PDF（base64，单页约 1MB）随保存接口提交，
    # 默认 1m 会 413，前端表现为保存一直转圈/失败
    client_max_body_size 20m;
    client_body_timeout 120s;

    location /api/ {
        proxy_pass http://$HOST_IP:8081;
        # 用 \$http_host（带端口）：\$host 会丢端口，后端 /entry 的 302 会跳到
        # http://<IP>/page/...（80 端口），依赖 80 是否也映射到本前端
        proxy_set_header Host \$http_host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Host \$http_host;
        proxy_set_header X-Forwarded-Proto \$scheme;
        # 大字段（PDF base64）写入与回传需要更长超时
        proxy_connect_timeout 60s;
        proxy_send_timeout 120s;
        proxy_read_timeout 120s;
        proxy_request_buffering off;
        proxy_buffering off;
    }
    location /entry/ {
        proxy_pass http://$HOST_IP:8081;
        proxy_set_header Host \$http_host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Host \$http_host;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    location / {
        try_files \$uri \$uri/ /index.html;
    }

    # 入口 HTML 禁止缓存：否则发版后浏览器/外层 iframe 仍跑旧 JS。
    # 注意："旧页面还能打开、没访问过的页面 500"的假象正来自这里——
    # index.html 被浏览器缓存住，服务器端其实早已 500。
    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        add_header Pragma "no-cache" always;
        expires -1;
    }

    # 带内容 hash 的静态资源：文件名随内容变化，可安全长缓存
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000" always;
    }
}
EOF
    docker rm -f zing-doctor-frontend 2>/dev/null
    # 前端端口：默认 2001；若宿主机 80 端口空闲则同时映射 80（外链无端口访问 /page 时也能打开，避免 404）
    PORTS=(-p 2001:80)
    if ! ss -tln 2>/dev/null | awk '{print $4}' | grep -qE ':80$'; then
      PORTS+=(-p 80:80)
      info "宿主机 80 端口空闲，前端同时映射 80:80（http://$HOST_IP/page/... 可默认访问）"
    else
      info "宿主机 80 端口已被占用（可能是 ICU 系统），前端仅映射 2001:80"
    fi
    docker run -d --name zing-doctor-frontend --restart unless-stopped \
      "${PORTS[@]}" \
      -v "$ROOT/frontend/dist:/usr/share/nginx/html:ro" \
      -v "$ROOT/runtime/nginx/default.conf:/etc/nginx/conf.d/default.conf:ro" \
      nginx:stable
    info "前端容器 zing-doctor-frontend 已启动（nginx stable，映射 ${PORTS[*]}）"
  else
    err "未找到 nginx:stable 镜像且服务器无外网无法拉取，前端无法托管"
    err "请确认服务器存在 nginx:stable 镜像后重试"
    return 1
  fi

  # ---- 等待后端就绪（最多 60 秒）----
  info "等待后端就绪（最多 60 秒）..."
  for i in $(seq 1 30); do
    if curl -m 2 -s -o /dev/null "http://127.0.0.1:8081/api/external/token"; then
      info "后端已就绪（$i 次探测）"; break
    fi
    sleep 2
  done

  return 0
}

# 探测外网：能否访问 Docker Hub（决定 Docker 全量 or 内网直连）
if curl -m 5 -sI https://registry-1.docker.io/v2/ >/dev/null 2>&1; then
  info "网络可达 Docker Hub，使用 Docker Compose 全量部署"
  # 生成前端 nginx 配置（反向代理 /api 到后端容器，静态资源托管 dist）
  mkdir -p "$ROOT/runtime/nginx"
  cat > "$ROOT/runtime/nginx/default.conf" <<'NGINXEOF'
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://backend:8081;
        # \$host 不含端口，外链 302 会跳到 80 端口；用 \$http_host 保留 ":2001"
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location /entry/ {
        proxy_pass http://backend:8081;
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location / {
        try_files $uri $uri/ /index.html;
    }
}
NGINXEOF
  info "已生成 nginx 配置 runtime/nginx/default.conf（/api 反代到 backend:8081）"
  $COMPOSE up -d --build
  MODE=docker
else
  warn "无法访问 Docker Hub（内网环境），自动切换内网直连部署"
  direct_deploy || { err "内网直连部署失败，详见上方日志"; exit 1; }
  MODE=direct
fi

# ---------- 4. 等待与验证 ----------
if [ "$MODE" = "docker" ]; then
  info "等待服务启动..."
  sleep 10
  $COMPOSE ps
  LOG_HINT="docker logs zing-doctor-backend --tail 50"
else
  info "容器状态："
  docker ps --filter "name=zing-doctor" --format "  {{.Names}}\t{{.Status}}\t{{.Ports}}"
  LOG_HINT="tail -50 $ROOT/logs/backend.log"
fi

echo ""
echo "=================================================="
echo "  部署完成（部署模式：$MODE）"
echo "  前端入口:   http://<部署机IP>:2001/page/abx-patient-list"
echo "  后端端口:   8081"
echo ""
echo "  生成外链验证（在部署机上执行）："
echo "    curl \"http://127.0.0.1:8081/api/external/token?pageCode=abx-patient-list&baseUrl=http://<部署机IP>:2001\""
echo "    curl \"http://127.0.0.1:8081/api/external/token?pageCode=abx-decision&baseUrl=http://<部署机IP>:2001&patientId=<真实患者ID>\""
echo ""
echo "  查看后端日志：$LOG_HINT"
echo "=================================================="
