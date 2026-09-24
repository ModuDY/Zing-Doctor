#!/usr/bin/env bash
# =====================================================================
# zing-doctor  Debian 一键安装脚本（不依赖 Docker 跑应用；数据库可外部提供）
#
# 支持两种数据库来源：
#   A. 本机自建（默认）：脚本用 apt 安装并初始化 MariaDB 10.5（Debian 11 自带）。
#      触发条件：DB_HOST 为本机 且 未提供 DB_ADMIN_PASSWORD（root 走 unix_socket 免密）。
#   B. 外部已有库（容器 / 远程，★你的场景）：MySQL 或 MariaDB 已在 Docker 中运行，
#      宿主机通过映射端口访问。脚本【不装数据库】，只装客户端 + JRE + nginx，
#      用给定管理员账号 TCP 连接、建库导表、部署应用。
#      触发条件：提供了 DB_ADMIN_PASSWORD（即使 DB_HOST=127.0.0.1 也算外部库）。
#      脚本会自动 SELECT VERSION() 探测是 MySQL 还是 MariaDB，选择对应 Spring profile。
#
# ---------------------------------------------------------------------
# 场景 B：MySQL 跑在本机 Docker，宿主映射端口 23306，管理员 root/zing@123
#   chmod +x install-mariadb-debian.sh
#   sudo DB_PORT=23306 DB_ADMIN_PASSWORD='zing@123' \
#        APP_DB_PASSWORD='Zing@2026' ./install-mariadb-debian.sh
#   # 应用与数据库在同一台宿主机时 DB_HOST 默认 127.0.0.1 即可（走映射端口）。
#
# 场景 B2：应用与数据库不在同一台机
#   sudo DB_HOST=10.0.0.5 DB_PORT=23306 DB_ADMIN_PASSWORD='zing@123' \
#        APP_DB_PASSWORD='Zing@2026' ./install-mariadb-debian.sh
#
# 想让应用直接用 root（不建专用账号，不推荐：ICU 库将失去只读约束）：
#   sudo DB_PORT=23306 DB_ADMIN_PASSWORD='zing@123' \
#        APP_DB_USER=root APP_DB_PASSWORD='zing@123' ./install-mariadb-debian.sh
#
# 场景 A：干净 Debian，让脚本全自动装 MariaDB
#   sudo ./install-mariadb-debian.sh
#
# 对【医院现有 ICU 生产库】加只读索引（默认关闭，需显式开启）：
#   sudo ... SETUP_ICU_INDEX=yes ./install-mariadb-debian.sh
#
# ---------------------------------------------------------------------
# 离线服务器（无外网 / apt 源不可达，现象：Could not resolve 'security.debian.org'、
# E: Unable to locate package ...）：
#   脚本会先用系统已装好的组件，只对缺失的包调 apt，apt 失败也不中断，
#   最后统一体检并给出「缺什么、怎么补」。常用开关：
#     SKIP_APT=yes                            完全不调用 apt
#     DB_CLI='docker exec -i <容器名> mysql'   用容器内客户端（宿主机不装客户端）
#     WEB_MODE=docker|nginx|auto|none          前端托管方式；无宿主 nginx 但有 nginx 镜像时
#                                              auto 会自动改用容器跑前端（--network host）
#     JAVA_BIN=/path/to/java  NGINX_BIN=/path/to/nginx
#
# ---------------------------------------------------------------------
# 应用目录：默认就是包所在目录（解压即部署，不再落到 /opt/zing-doctor），
# 需要固定路径时在 conf/db.conf 里写 APP_HOME=/opt/zing-doctor。
#
# 推荐用法：连接参数写进包内 conf/db.conf，脚本会自动读取，然后只需：
#   ./install-mariadb-debian.sh                 完整安装 / 升级（读 conf/db.conf）
#   ./install-mariadb-debian.sh --config-only   只按 conf/db.conf 重写运行环境并重启后端
#                                               （不动数据库、不导 SQL、不更新前端）
#   优先级：命令行/环境变量 > conf/db.conf > 脚本默认值
#
# 幂等：可重复执行，建库/建表/加列/建索引均做存在性判断，不重复、不报错。
# =====================================================================
set -euo pipefail

# ---------------- 输出辅助 ----------------
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info(){ echo -e "${GREEN}[INFO]${NC} $*"; }
warn(){ echo -e "${YELLOW}[WARN]${NC} $*"; }
err(){ echo -e "${RED}[ERROR]${NC} $*" >&2; }
trap 'err "脚本在第 $LINENO 行失败，已中止（已完成的步骤是幂等的，修正后可重跑）"' ERR
[ "$(id -u)" -eq 0 ] || { err "请用 root 运行：sudo $0"; exit 1; }

# ---------------- 命令行参数 ----------------
#   --config-only  只按配置文件重写 /etc/zing-doctor/zing-doctor.env 并重启后端，
#                  不动数据库、不导 SQL、不更新前端（改库地址后最快生效的方式）
CONFIG_ONLY="no"
for _arg in "$@"; do
  case "$_arg" in
    --config-only|-c) CONFIG_ONLY="yes" ;;
    -h|--help) sed -n '2,64p' "$0"; exit 0 ;;
    *) : ;;
  esac
done

# ---------------- 目录 ----------------
SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="$SRC_DIR/sql/mysql"

# ---------------- 数据库连接配置文件 ----------------
# 默认读 conf/db.conf（包内）或根目录 db.conf；可用 DB_CONF=<路径> 指定其它文件。
# 优先级：命令行/环境变量 > 配置文件 > 脚本内置默认值
CONF_FILE="${DB_CONF:-}"
if [ -z "$CONF_FILE" ] && [ -f "$SRC_DIR/conf/db.conf" ]; then CONF_FILE="$SRC_DIR/conf/db.conf"; fi
if [ -z "$CONF_FILE" ] && [ -f "$SRC_DIR/db.conf" ]; then CONF_FILE="$SRC_DIR/db.conf"; fi

load_conf() {
  local file="$1" line key val loaded=""
  while IFS= read -r line || [ -n "$line" ]; do
    line="$(printf '%s' "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    case "$line" in ''|\#*) continue ;; esac
    # 行内注释：仅当 # 前有空白时裁剪（避免误伤密码里的 #）
    line="$(printf '%s' "$line" | sed -e 's/[[:space:]]#.*$//' -e 's/[[:space:]]*$//')"
    case "$line" in *=*) ;; *) continue ;; esac
    key="$(printf '%s' "${line%%=*}" | tr -d '[:space:]')"
    val="$(printf '%s' "${line#*=}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' \
           -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'\$/\1/")"
    [ -n "$key" ] || continue
    # 环境变量/命令行已显式提供时不覆盖
    if declare -p "$key" >/dev/null 2>&1; then continue; fi
    printf -v "$key" '%s' "$val"
    export "$key"
    loaded="${loaded}${loaded:+, }$key"
  done < "$file"
  [ -n "$loaded" ] && info "已从配置文件载入：$loaded"
  return 0
}

if [ -n "$CONF_FILE" ]; then
  info "读取数据库配置文件：$CONF_FILE"
  load_conf "$CONF_FILE"
else
  warn "未找到 conf/db.conf，全部使用命令行参数与内置默认值"
fi

if [ "$CONFIG_ONLY" = "yes" ]; then
  info "模式：--config-only（只按配置重写运行环境并重启后端：不动数据库、不导 SQL、不更新前端）"
fi

# ---------------- 可配置变量（环境变量 > 配置文件 > 默认值） ----------------
# 应用目录：默认就是包所在目录（解压在哪就在哪跑，不再落到 /opt）。
# ⚠️ 该目录即运行目录，不要删除或移动；升级时直接解压覆盖它即可。
# 想放到别处（如 /opt/zing-doctor）在 conf/db.conf 里写 APP_HOME=/opt/zing-doctor
APP_HOME="${APP_HOME:-$SRC_DIR}"
WEB_PORT="${WEB_PORT:-2001}"
BACKEND_PORT="${BACKEND_PORT:-8081}"

# 应用专用数据库账号（应用用它连接，默认新建最小权限账号；不想建可设 APP_DB_USER=root）
APP_DB_USER="${APP_DB_USER:-zing}"
APP_DB_PASSWORD="${APP_DB_PASSWORD:-Zing@2026}"

# 数据库位置
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DOCTOR_DB="${DOCTOR_DB:-zing_doctor_db_prod}"
ICU_DB="${ICU_DB:-zing_icu_db_prod}"

# 数据库类型：留空 = 自动探测（需要能连库的客户端）；
# mariadb / mysql = 显式指定，应用机没有客户端也能完成部署（库由对方 DBA 初始化）
DB_TYPE="${DB_TYPE:-}"

# 建库/授权用的管理员账号。
#   - 外部库（容器/远程）：必填 DB_ADMIN_PASSWORD，走 TCP + 密码。
#   - 本机自建：留空，root 走 unix_socket 免密。
DB_ADMIN_USER="${DB_ADMIN_USER:-root}"
DB_ADMIN_PASSWORD="${DB_ADMIN_PASSWORD:-}"

# 是否对医院现有 ICU 库执行性能索引脚本（默认关闭，避免擅自变更生产库）
SETUP_ICU_INDEX="${SETUP_ICU_INDEX:-no}"
# ICU 库位置（默认与主库同实例；不同机时单独指定）
ICU_DB_HOST="${ICU_DB_HOST:-$DB_HOST}"
ICU_DB_PORT="${ICU_DB_PORT:-$DB_PORT}"
# ICU 库连接账号：留空 = 复用应用账号；ICU 由厂方给只读账号时单独填这两个
ICU_DB_USER="${ICU_DB_USER:-}"
ICU_DB_PASSWORD="${ICU_DB_PASSWORD:-}"

# ICU 患者数据提供方式：sql=直连真实 ICU 库；mock=内置示例（无真实库时演示用）
ICU_DATA_PROVIDER="${ICU_DATA_PROVIDER:-sql}"

# 外链 / 安全相关（生产建议覆盖）
ICU_LINK_TOKEN="${ICU_LINK_TOKEN:-zing-icu-link-token-2026}"
EXTERNAL_LINK_SECRET="${EXTERNAL_LINK_SECRET:-zing-doctor-prod-secret-change-me}"
EXTERNAL_LINK_BASE_URL="${EXTERNAL_LINK_BASE_URL:-}"
QUALITY_CONFIG_WRITE_IP_WHITELIST="${QUALITY_CONFIG_WRITE_IP_WHITELIST:-}"

# 离线 / 受限环境开关（医院内网服务器常见：无外网、无可用 apt 源）
#   SKIP_APT=yes        完全不调用 apt，直接用系统里已装好的组件
#   DB_CLI=<命令>       手工指定数据库客户端。默认自动找 mariadb/mysql；
#                       数据库跑在本机 Docker 里且宿主机没装客户端时可写：
#                       DB_CLI='docker exec -i <容器名> mysql'
#   JAVA_BIN / NGINX_BIN  手工指定 java / nginx 路径（自动探测不到时用）
#
# 应用与数据库分两台服务器（典型：库在 ICU 那台）：
#   DB_HOST=<库服务器IP> DB_PORT=<库端口> 指定远程库；库侧需先放行端口并建好账号授权。
#   宿主机没有 mysql 客户端时，可借本机已有镜像在容器里跑客户端：
#     DB_CLI='docker run --rm -i <本机镜像> mysql -h<库IP> -P<端口> -uroot -p<密码>'
#   注意：该方式下 ICU 必须与主库同一实例（要分实例请让库侧 DBA 手工执行 03_icu_indexes.sql）。
#   应用机完全没有客户端、库与账号全由对方 DBA 维护时：在 conf/db.conf 里设
#   DB_TYPE=mariadb（或 mysql）跳过探测，脚本照常部署应用，只跳过建库/授权/SQL 导入。
#   WEB_MODE            前端托管方式：auto(默认)/nginx/docker/none
#                       宿主没装 nginx 但本机有 nginx 镜像时，用容器跑前端（--network host）
#   NGINX_IMAGE         容器方式使用的镜像（默认优先复用本机已有的 nginx 镜像）
SKIP_APT="${SKIP_APT:-no}"
DB_CLI="${DB_CLI:-}"
JAVA_BIN="${JAVA_BIN:-}"
NGINX_BIN="${NGINX_BIN:-}"
WEB_MODE="${WEB_MODE:-auto}"
NGINX_IMAGE="${NGINX_IMAGE:-}"
NGINX_CONTAINER="${NGINX_CONTAINER:-zing-doctor-frontend}"

# ---------------- 数据库来源判定 ----------------
# 本机地址？（注意：仅 DB_HOST 判定「库是否在本机」，应用连库地址始终由 DB_HOST/DB_PORT 决定）
host_is_local() { case "$1" in 127.0.0.1|localhost|::1) return 0;; *) return 1;; esac; }

have(){ command -v "$1" >/dev/null 2>&1; }

# DB_CLI 是复合命令（含空格，如 'docker exec -i zing-mysql mysql'、
# 'docker run --rm -i mariadb:10.5 mysql -h10.0.0.9 -uroot -pxxx'）时，
# 不再追加 -h/-P/-u/-p：连接与认证由该命令自身完成。
cli_is_compound(){ case "$DB_CLI" in *" "*) return 0;; *) return 1;; esac; }

if host_is_local "$DB_HOST" && [ -z "$DB_ADMIN_PASSWORD" ]; then
  INSTALL_SERVER="yes"      # 场景 A：本机自建
else
  INSTALL_SERVER="no"       # 场景 B：外部库（同机容器 / 另一台服务器）
fi

# 远程库必须能拿到管理员凭据：要么 DB_ADMIN_PASSWORD，要么写进 DB_CLI 的复合命令里
if [ "$INSTALL_SERVER" = "no" ] && ! host_is_local "$DB_HOST" \
   && [ -z "$DB_ADMIN_PASSWORD" ] && ! cli_is_compound; then
  err "远程数据库必须提供 DB_ADMIN_PASSWORD，或把连接信息写进 DB_CLI（如 'docker run --rm -i <镜像> mysql -h<库IP> -P<端口> -uroot -p<密码>'）"
  exit 1
fi

# 命令行客户端（mariadb-client 同时提供 mariadb / mysql；取存在者）
# DB_CLI 若已由环境变量指定（例如 docker exec 形式）则保持不覆盖
pick_cli(){ [ -n "$DB_CLI" ] || DB_CLI="$(command -v mariadb 2>/dev/null || command -v mysql 2>/dev/null || true)"; }

# 本机没有客户端时，如果本地有 mysql/mariadb 镜像，直接给出可粘贴的 DB_CLI 写法
suggest_db_cli() {
  have docker || return 0
  local img
  img="$(docker images --format '{{.Repository}}:{{.Tag}}' 2>/dev/null | grep -Ei 'mysql|maria' | head -1 || true)"
  [ -n "$img" ] || { warn "  本机也没有 mysql/mariadb 镜像，可在库里那台机器上执行 sql/mysql/install-all.sql"; return 0; }
  warn "  本机已有镜像 $img，把它填进 conf/db.conf 的 DB_CLI 即可让脚本自动建库导表："
  warn "    DB_CLI=docker run --rm -i $img mysql -h$DB_HOST -P$DB_PORT -u${DB_ADMIN_USER:-root} -p${DB_ADMIN_PASSWORD:-<管理员密码>}"
}

# 管理员访问是否走「本机直接执行客户端」语义（本机自建，或连接信息已写进 DB_CLI）
admin_is_local(){ [ "$INSTALL_SERVER" = "yes" ] || cli_is_compound; }

# 管理员连接：mariadb_admin [库名]  （stdin 喂 SQL，或 < 文件）
mariadb_admin() {
  local db="${1:-}"
  if admin_is_local; then
    $DB_CLI ${db:+"$db"}
  else
    $DB_CLI -h"$DB_HOST" -P"$DB_PORT" -u"$DB_ADMIN_USER" -p"$DB_ADMIN_PASSWORD" ${db:+"$db"}
  fi
}
# ICU 库管理员连接（可能与主库不同机）
mariadb_icu_admin() {
  if cli_is_compound; then
    # 连接信息已固定在 DB_CLI 里，只能操作该实例；ICU 在别的实例时给出明确提示
    if [ "$ICU_DB_HOST" != "$DB_HOST" ] || [ "$ICU_DB_PORT" != "$DB_PORT" ]; then
      warn "DB_CLI 是复合命令，无法为 ICU 单独指定实例（ICU=$ICU_DB_HOST:$ICU_DB_PORT，主库=$DB_HOST:$DB_PORT）"
      warn "  ICU 索引将按主库实例处理；若 ICU 库确在别处，请在宿主安装客户端后重跑，或让该库 DBA 手工执行 $SQL_DIR/03_icu_indexes.sql"
    fi
    $DB_CLI "$ICU_DB"
  elif admin_is_local && host_is_local "$ICU_DB_HOST"; then
    $DB_CLI "$ICU_DB"
  else
    $DB_CLI -h"$ICU_DB_HOST" -P"$ICU_DB_PORT" -u"$DB_ADMIN_USER" -p"$DB_ADMIN_PASSWORD" "$ICU_DB"
  fi
}
# 执行单条查询并输出结果：admin_query "SQL" [库名]（用于 VERSION()/COUNT 等 -e 场景）
admin_query() {
  local sql="$1"; local db="${2:-}"
  if admin_is_local; then
    $DB_CLI -N -uroot ${db:+"$db"} -e "$sql"
  else
    $DB_CLI -h"$DB_HOST" -P"$DB_PORT" -u"$DB_ADMIN_USER" -p"$DB_ADMIN_PASSWORD" -N ${db:+"$db"} -e "$sql"
  fi
}

echo ""
echo "=================================================="
echo "  zing-doctor  Debian 一键安装"
if [ "$INSTALL_SERVER" = "yes" ]; then
  echo "  数据库来源: 本机自建（apt 安装 MariaDB 10.5）"
else
  echo "  数据库来源: 外部库（容器/远程）$DB_HOST:$DB_PORT，账号 $DB_ADMIN_USER"
fi
echo "  应用目录  : $APP_HOME"
echo "  前端端口  : $WEB_PORT    后端端口: $BACKEND_PORT"
echo "  ICU 索引  : $SETUP_ICU_INDEX    ICU 数据: $ICU_DATA_PROVIDER"
echo "=================================================="
echo ""

# ---------------- 1. 安装系统依赖 ----------------
# 医院内网服务器多为离线：无 DNS、apt 源不可达（现象：Could not resolve 'security.debian.org'，
# 随后 E: Unable to locate package ...）。因此这里的策略是：
#   1) 先检查命令是否已存在，存在就直接用，不依赖 apt；
#   2) 只对确实缺失的包调 apt，apt 失败也不立刻中断；
#   3) 最后统一体检：必需组件缺失才退出，并给出「缺什么 / 怎么补」的具体做法。
export DEBIAN_FRONTEND=noninteractive

APT_PKGS="default-jre-headless ca-certificates"
if [ "$INSTALL_SERVER" = "yes" ]; then
  APT_PKGS="$APT_PKGS mariadb-server"
fi
have mariadb || have mysql || APT_PKGS="$APT_PKGS mariadb-client"
have nginx  || APT_PKGS="$APT_PKGS nginx"
have curl   || APT_PKGS="$APT_PKGS curl"

APT_FAILED="no"
if [ "$SKIP_APT" = "yes" ]; then
  info "SKIP_APT=yes：跳过 apt，直接使用系统已装组件"
else
  info "apt-get update（离线环境失败属正常，失败后继续用本机已有组件）..."
  apt-get update -y || warn "apt-get update 失败（常见原因：无外网 / DNS 不可用），继续检查本机组件"
  info "尝试安装缺失的系统依赖：$APT_PKGS"
  # shellcheck disable=SC2086
  apt-get install -y --no-install-recommends $APT_PKGS || APT_FAILED="yes"
  [ "$APT_FAILED" = "no" ] || warn "apt 安装未全部成功（离线服务器属预期），继续检查本机已有组件"
fi

# 本机自建模式下，MariaDB 服务是否可用（借容器客户端时不适用，跳过）
if [ "$INSTALL_SERVER" = "yes" ] && ! cli_is_compound; then
  if have mariadbd || have mysqld || have mariadb-install-db; then
    info "启动并设置 MariaDB 开机自启 ..."
    systemctl enable --now mariadb || warn "mariadb 服务启动失败，请检查：systemctl status mariadb"
  fi
fi

pick_cli
[ -n "$JAVA_BIN" ] || JAVA_BIN="$(command -v java || true)"
[ -n "$NGINX_BIN" ] || NGINX_BIN="$(command -v nginx || true)"

# ---------- 依赖体检：必需缺失才退出，可选缺失只警告 ----------
MISSING=""
# DB_TYPE 显式指定时不需要客户端（应用机连远程库、由 DBA 初始化库的场景）
if [ -z "$DB_TYPE" ]; then
  [ -n "$DB_CLI" ] || MISSING="$MISSING  数据库命令行客户端(mariadb/mysql，或在 conf 里设 DB_TYPE=mariadb|mysql)"
fi
[ -n "$JAVA_BIN" ] || MISSING="$MISSING  运行环境(java / JRE 8+)"
if [ "$INSTALL_SERVER" = "yes" ] && ! cli_is_compound && ! { have mariadbd || have mysqld || have mariadb; }; then
  MISSING="$MISSING  数据库服务(mariadb-server)"
fi

if [ -n "$MISSING" ]; then
  err "缺少必需组件：$MISSING"
  if [ "$APT_FAILED" = "yes" ]; then
    err "apt 不可用（本次是离线环境）。三种补法任选："
    err "  1) 有外网的机器上下载 deb 包，拷到本机安装："
    err "     apt-get download <包名>        # 有网机器执行，产物拷到本机后 dpkg -i *.deb"
    err "  2) 数据库已跑在本机 Docker 里时，直接用容器内客户端，宿主机无需装客户端："
    err "     DB_CLI='docker exec -i <容器名> mysql' sudo -E $0"
    err "  3) 组件已装在非标准路径时，直接指定：JAVA_BIN=/path/to/java NGINX_BIN=/path/to/nginx ..."
  fi
  err "已完成的步骤是幂等的，补齐后重跑本脚本即可。"
  suggest_db_cli
  # 离线现场最常见的输错点：本机 Docker 里明明有数据库容器，却没人告诉脚本用哪个
  if have docker; then
    CAND="$(docker ps --format '{{.Names}}|{{.Ports}}' 2>/dev/null | grep -Ei '3306|mysql|maria' | head -3 || true)"
    if [ -n "$CAND" ]; then
      err "检测到本机 Docker 中的数据库容器，可直接借容器内客户端（宿主机无需装客户端）："
      echo "$CAND" | while IFS='|' read -r cname cports; do
        err "  DB_CLI='docker exec -i $cname mysql'    # $cname  $cports"
      done
      err "  用法示例：SKIP_APT=yes DB_CLI='docker exec -i <容器名> mysql' DB_HOST=127.0.0.1 DB_PORT=<映射端口> $0"
    fi
  fi
  exit 1
fi

if [ -z "$NGINX_BIN" ]; then
  if have docker; then
    warn "宿主未安装 nginx：稍后尝试用本机已有的 nginx 镜像以容器方式托管前端（无需联网）。"
  else
    warn "未找到 nginx 且无 Docker：前端将无法托管（后端仍会正常启动）。"
    warn "补法：装好 nginx 后重跑本脚本，或自行托管 $APP_HOME/frontend/dist 并反代到 127.0.0.1:$BACKEND_PORT。"
  fi
fi
info "组件就绪：客户端 ${DB_CLI} / java ${JAVA_BIN} / nginx ${NGINX_BIN:-未安装}"

# ICU 库与主库不在同一实例时，脚本只能在主库那台上建号授权，去不了 ICU 那台。
# （同一实例时下面的授权语句已覆盖 ICU 库，不需要额外操作）
if [ "$ICU_DB_HOST" != "$DB_HOST" ] || [ "$ICU_DB_PORT" != "$DB_PORT" ]; then
  SELF_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
  SELF_IP="${SELF_IP:-<本应用机IP>}"
  warn "ICU 库指向远程 $ICU_DB_HOST:$ICU_DB_PORT：本机不会在那边建账号/授权，需 ICU 侧 DBA 确认（或填 conf 里的 ICU_DB_USER/ICU_DB_PASSWORD 用对方给的只读账号）"
  warn "  CREATE USER IF NOT EXISTS '${ICU_DB_USER:-$APP_DB_USER}'@'$SELF_IP' IDENTIFIED BY '<密码>';"
  warn "  GRANT SELECT ON \`$ICU_DB\`.* TO '${ICU_DB_USER:-$APP_DB_USER}'@'$SELF_IP';"
fi

# ---------------- 2. 连接数据库 + 探测类型/版本 ----------------
# DB_TYPE 显式指定（conf 里 DB_TYPE=mariadb|mysql）时跳过探测：
# 适用于「应用机没有数据库客户端、库由对方 DBA 维护」的部署形态。
if [ -n "$DB_TYPE" ]; then
  case "$DB_TYPE" in
    mariadb) DB_KIND="mariadb"; DB_PROFILE="mariadb"; PFIX="MARIADB"; DRIVER="org.mariadb.jdbc.Driver" ;;
    mysql)   DB_KIND="mysql";   DB_PROFILE="mysql";   PFIX="MYSQL";  DRIVER="com.mysql.cj.jdbc.Driver" ;;
    *) err "conf 里的 DB_TYPE 只能是 mariadb 或 mysql（当前：$DB_TYPE）"; exit 1 ;;
  esac
  DB_VER="（未探测，按配置指定）"
  info "按配置指定数据库类型：$DB_KIND -> Spring profile=$DB_PROFILE，驱动 $DRIVER"
  if [ -z "$DB_CLI" ]; then
    warn "本机没有数据库客户端：跳过建库/建号/授权与 SQL 导入，$DB_HOST:$DB_PORT 的库需由库侧 DBA 用 sql/mysql/ 初始化"
    suggest_db_cli
  else
    # 有客户端就顺手验一下连通性，并核对 conf 里写的类型与实测是否一致
    DB_VER_PROBE="$(admin_query "SELECT VERSION();" 2>/dev/null || true)"
    if [ -n "$DB_VER_PROBE" ]; then
      DB_VER="$DB_VER_PROBE"
      info "已连上数据库：$DB_VER_PROBE"
      case "$DB_VER_PROBE" in
        *MariaDB*) [ "$DB_KIND" = "mariadb" ] || warn "实测是 MariaDB，但 conf 里 DB_TYPE=$DB_TYPE，建议改成 DB_TYPE=mariadb" ;;
        *)         [ "$DB_KIND" = "mysql" ]   || warn "实测不是 MariaDB（$DB_VER_PROBE），但 conf 里 DB_TYPE=$DB_TYPE，建议改成 DB_TYPE=mysql" ;;
      esac
    else
      warn "已配置 DB_CLI 但连不上数据库：请检查 $DB_HOST:$DB_PORT、账号密码与防火墙（本次不再探测）"
    fi
  fi
else

if [ "$INSTALL_SERVER" = "no" ]; then
  info "测试到外部数据库 $DB_HOST:$DB_PORT 的连接 ..."
fi
DB_VER="$(admin_query "SELECT VERSION();" 2>/dev/null || true)"
if [ -z "$DB_VER" ]; then
  if cli_is_compound; then
    err "借容器客户端连接数据库失败：$DB_CLI"
    err "最常见原因：容器内 root 需要密码（现象 ERROR 1045 Access denied ... using password: NO）。"
    err "先取出密码（容器启动时注入的环境变量）："
    err "  docker inspect <容器名> --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -iE 'root_password|password'"
    err "再带密码重跑（密码含特殊字符时整体用单引号包住赋值给变量）："
    err "  ROOTPW='<密码>'"
    err "  SKIP_APT=yes DB_CLI=\"docker exec -i <容器名> mysql -uroot -p\$ROOTPW\" ... $0"
  elif [ "$INSTALL_SERVER" = "yes" ]; then
    err "本机 MariaDB 连接失败，请检查服务状态：systemctl status mariadb"
  else
    err "无法连接外部数据库或认证失败：$DB_HOST:$DB_PORT（账号 $DB_ADMIN_USER）"
    err "排查：1) 端口映射/防火墙（容器是否 -p 23306:3306）  2) 账号是否允许该客户端 host 连接"
    err "      3) MySQL 8 默认 caching_sha2_password，若客户端报认证错误，可升级客户端，或："
    err "         ALTER USER '$DB_ADMIN_USER'@'%' IDENTIFIED WITH mysql_native_password BY '<密码>';"
  fi
  exit 1
fi

# 探测数据库种类 -> Spring profile 与环境变量前缀
case "$DB_VER" in
  *MariaDB*) DB_KIND="mariadb"; DB_PROFILE="mariadb"; PFIX="MARIADB"; DRIVER="org.mariadb.jdbc.Driver" ;;
  *)         DB_KIND="mysql";   DB_PROFILE="mysql";   PFIX="MYSQL";  DRIVER="com.mysql.cj.jdbc.Driver" ;;
esac
info "数据库版本：$DB_VER"
info "识别为 $DB_KIND -> Spring profile=$DB_PROFILE，驱动 $DRIVER"
if [ "$INSTALL_SERVER" = "yes" ]; then
  case "$DB_VER" in
    10.5.*) info "MariaDB 10.5 系列，符合目标" ;;
    *) warn "本机 MariaDB 版本非 10.5.x（$DB_VER），SQL 为通用语法，通常可直接运行" ;;
  esac
  warn "apt 安装的是源内 10.5 最新安全补丁版（高于 10.5.6、向后兼容），不建议锁定到有漏洞的精确 10.5.6"
fi

fi   # 结束 DB_TYPE 未指定（自动探测）分支

# ---------------- 3. 建库、建账号、授权 ----------------
run_sql_file() {
  local file="$1"; local db="${2:-}"
  info "  执行 $(basename "$file")"
  mariadb_admin "$db" < "$file"
}

if [ "$CONFIG_ONLY" = "yes" ]; then
  info "跳过：建库/建账号/授权、SQL 导入（--config-only 只更新运行环境）"
elif [ -z "$DB_CLI" ]; then
  warn "本机没有数据库客户端：跳过建库/建账号/授权与 SQL 导入"
  warn "  请在库服务器（$DB_HOST）上按 sql/mysql/README.md 的顺序执行 sql/mysql/ 下脚本初始化，"
  warn "  并确认账号 $APP_DB_USER 对主库 $DOCTOR_DB 有 ALL、对 ICU 库 $ICU_DB 有 SELECT"
  warn "  也可以把一次性的 sql/mysql/install-all.sql 拷到库服务器执行；"
  suggest_db_cli
else
  info "创建数据库与应用账号 ..."
  # ICU 若单独给了只读账号，一并建号授权（不存在才建，幂等）
  ICU_ACL_SQL=""
  if [ -n "$ICU_DB_USER" ] && [ "$ICU_DB_USER" != "$APP_DB_USER" ]; then
    ICU_ACL_SQL="CREATE USER IF NOT EXISTS '$ICU_DB_USER'@'%' IDENTIFIED BY '$ICU_DB_PASSWORD';
GRANT SELECT ON \`$ICU_DB\`.* TO '$ICU_DB_USER'@'%';"
  fi
  # 授权/建号失败不再中止：远程库常由 DBA 管理，账号可能已存在或无授权权限，
  # 那种情况提示人工确认即可，不该把后面的部署也一起挡掉。
  if mariadb_admin <<SQL
CREATE DATABASE IF NOT EXISTS \`$DOCTOR_DB\`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS \`$ICU_DB\`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '$APP_DB_USER'@'%' IDENTIFIED BY '$APP_DB_PASSWORD';
CREATE USER IF NOT EXISTS '$APP_DB_USER'@'localhost' IDENTIFIED BY '$APP_DB_PASSWORD';
ALTER USER '$APP_DB_USER'@'%' IDENTIFIED BY '$APP_DB_PASSWORD';
ALTER USER '$APP_DB_USER'@'localhost' IDENTIFIED BY '$APP_DB_PASSWORD';
GRANT ALL PRIVILEGES ON \`$DOCTOR_DB\`.* TO '$APP_DB_USER'@'%';
GRANT ALL PRIVILEGES ON \`$DOCTOR_DB\`.* TO '$APP_DB_USER'@'localhost';
GRANT SELECT ON \`$ICU_DB\`.* TO '$APP_DB_USER'@'%';
GRANT SELECT ON \`$ICU_DB\`.* TO '$APP_DB_USER'@'localhost';
$ICU_ACL_SQL
FLUSH PRIVILEGES;
SQL
  then
    info "数据库与账号就绪（主库 $DOCTOR_DB 全权，ICU 库 $ICU_DB 只读）"
  else
    warn "建库/建账号/授权未全部成功（账号可能已存在，或当前管理员无授权权限）"
    warn "  若库由 DBA 管理，请让其确认：账号 $APP_DB_USER 对主库 $DOCTOR_DB 有 ALL、对 ICU 库 $ICU_DB 有 SELECT"
  fi

  # ---------------- 4. 导入建表 + 种子脚本 ----------------
  [ -d "$SQL_DIR" ] || { err "缺少 SQL 目录：$SQL_DIR（请确认在交付包根目录运行）"; exit 1; }

  info "初始化主库幂等存储过程 ..."
  run_sql_file "$SQL_DIR/00b_idempotent_helpers_doctor.sql" "$DOCTOR_DB"

  # 历史库修复：早期转换版本把达梦 id 列的 SEQ 默认值删掉了，导致不写 id 的种子 INSERT
  # 报 Field 'id' doesn't have a default value。必须在种子脚本之前修，否则又会中断。
  if [ -f "$SQL_DIR/24_fix_id_auto_increment.sql" ]; then
    info "修复历史库中 id 列缺 AUTO_INCREMENT 的表（幂等，新建库为空操作）..."
    run_sql_file "$SQL_DIR/24_fix_id_auto_increment.sql" "$DOCTOR_DB"
  fi

  info "导入主库结构与种子数据（顺序执行，幂等）..."
  MAIN_SQL=(
    01_schema.sql 02_seed.sql 05_apache2_pdf.sql 06_abx_drug_dict.sql
    07_sofa.sql 08_sofa_p1.sql 09_quality.sql 10_quality_config.sql
    11_quality_count_rule.sql 12_archive.sql 13_auth.sql 14_param_framework.sql
    15_quality_patient_fields.sql 16_quality_fatality_ref.sql
    17_quality_rule_local.sql 18_quality_manual_audit.sql
    19_quality_target_direction.sql 20_quality_fact_patient_default_cols.sql
    21_ards_prone.sql 22_ards_prone_config.sql 23_ards_prone_sign_work_no.sql
    # 25/26 表名规范化 rename（人工改写的存储过程版；全新 MariaDB 库无旧表 → 全部跳过，幂等）
    25_rename_doctor_tables.sql 26_rename_clinical_tables.sql
    # 28 质控每日批算参数。必须排在 25/26 之后：脚本写的是新表名 sys_param，
    #    老库上要等 25/26 把 zing_sys_param 改名过来，排前面会报表不存在
    28_quality_daily_param.sql
    # 24 质控配置写保护总开关。同样排在 25/26 之后（写的是新表名 sys_param / sys_param_group）
    24_quality_config_guard.sql
    # 29 患者工作台页面注册。排在 25/26 之后（写的是新表名 sys_page_config）；
    #    另外若启用 27_restore_config_snapshot.sql，本脚本必须排在它之后 ——
    #    27 会整表 DELETE + INSERT sys_page_config，而那份快照导出时还没有工作台这个页面
    29_patient_workbench.sql
    # 30 患者工作台科室边界与管理员名单。排在 25/26 之后（写的是新表名 sys_param /
    #    sys_param_group）；若启用 27_restore_config_snapshot.sql，也必须排在它之后 ——
    #    27 会整表 DELETE + INSERT sys_param，而那份快照导出时还没有这个参数
    30_user_depart_scope.sql
  )
  for f in "${MAIN_SQL[@]}"; do
    [ -f "$SQL_DIR/$f" ] || { warn "缺少 $f，跳过"; continue; }
    run_sql_file "$SQL_DIR/$f" "$DOCTOR_DB"
  done
  info "主库表数量：$(admin_query "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DOCTOR_DB';")"

  # 可选识别词库（默认不导，需要时取消注释）
  # for f in 03_word_inc_v23111.sql 03_word_inc_v2319.sql 04_abx_word_training.sql; do
  #   run_sql_file "$SQL_DIR/$f" "$DOCTOR_DB"
  # done

  # ICU 只读库性能索引（默认关闭：这是对医院生产库的变更，需 DBA 知情）
  if [ "$SETUP_ICU_INDEX" = "yes" ]; then
    info "在 ICU 库 $ICU_DB_HOST:$ICU_DB_PORT 准备幂等存储过程并执行索引脚本 ..."
    mariadb_icu_admin < "$SQL_DIR/00c_idempotent_helpers_icu.sql"
    mariadb_icu_admin < "$SQL_DIR/03_icu_indexes.sql"
    info "ICU 库性能索引已处理（幂等，已存在则跳过）"
  else
    warn "已跳过 ICU 库性能索引（SETUP_ICU_INDEX=no）。若应用查询 ICU 库较慢，"
    warn "经 DBA 评估后可执行：SETUP_ICU_INDEX=yes sudo -E $0"
  fi
fi

# ---------------- 5. 部署应用文件 ----------------
if [ "$CONFIG_ONLY" = "yes" ] && [ ! -f "$APP_HOME/app/zing-doctor.jar" ]; then
  err "--config-only 需要先完成一次完整安装（未找到 $APP_HOME/app/zing-doctor.jar）"
  err "先执行：$0   （不带参数），之后再改配置文件用 --config-only 同步"
  exit 1
fi
mkdir -p "$APP_HOME"/{app,config,frontend,logs,sql}

if [ "$CONFIG_ONLY" = "no" ]; then
info "部署应用文件到 $APP_HOME ..."
JAR_SRC="$SRC_DIR/app/zing-doctor.jar"
[ -f "$JAR_SRC" ] || JAR_SRC="$(ls "$SRC_DIR"/target/zing-doctor-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
[ -n "$JAR_SRC" ] && [ -f "$JAR_SRC" ] || { err "未找到后端 jar（期望 app/zing-doctor.jar 或 target/zing-doctor-*.jar）"; exit 1; }
# 默认应用目录就是包目录本身（脚本所在目录），此时源与目标相同，install/cp 会因
# "same file" 报错，故先判断
if [ "$JAR_SRC" = "$APP_HOME/app/zing-doctor.jar" ]; then
  info "后端 jar 已在部署目录（应用目录=包目录）：$JAR_SRC"
else
  install -m 644 "$JAR_SRC" "$APP_HOME/app/zing-doctor.jar"
  info "后端 jar 已安装：$JAR_SRC -> $APP_HOME/app/zing-doctor.jar"
fi

if [ ! -f "$SRC_DIR/frontend/dist/index.html" ]; then
  err "缺少前端产物 frontend/dist/index.html（交付包应内置构建好的 dist）"
  err "请把构建好的 dist 放到 $SRC_DIR/frontend/dist/ 后重跑"
  exit 1
fi
if [ "$SRC_DIR/frontend/dist" = "$APP_HOME/frontend/dist" ]; then
  info "前端 dist 已在部署目录（应用目录=包目录），无需复制"
else
  cp -a "$SRC_DIR/frontend/dist/." "$APP_HOME/frontend/dist/"
  info "前端 dist 已同步到 $APP_HOME/frontend/dist/"
fi
else
  info "跳过：应用文件复制（--config-only）"
fi

# 敏感环境变量文件（chmod 600）。变量前缀按探测到的库类型选择（MYSQL_* / MARIADB_*）
# ICU 账号未单独指定时复用应用账号
[ -n "$ICU_DB_USER" ]     || ICU_DB_USER="$APP_DB_USER"
[ -n "$ICU_DB_PASSWORD" ] || ICU_DB_PASSWORD="$APP_DB_PASSWORD"
info "运行环境：主库 $DB_HOST:$DB_PORT/$DOCTOR_DB，ICU 库 $ICU_DB_HOST:$ICU_DB_PORT/$ICU_DB（账号 $ICU_DB_USER）"
ENV_FILE="/etc/zing-doctor/zing-doctor.env"
mkdir -p /etc/zing-doctor
cat > "$ENV_FILE" <<EOF
# zing-doctor 运行环境（由安装脚本生成，chmod 600）
# 数据库类型自动探测结果：$DB_KIND（版本 $DB_VER）
# 生成来源：$([ -n "$CONF_FILE" ] && echo "$CONF_FILE" || echo "命令行/默认值")
SPRING_PROFILES_ACTIVE=$DB_PROFILE
ICU_DATA_PROVIDER=$ICU_DATA_PROVIDER
ICU_LINK_TOKEN=$ICU_LINK_TOKEN
EXTERNAL_LINK_SECRET=$EXTERNAL_LINK_SECRET
EXTERNAL_LINK_BASE_URL=$EXTERNAL_LINK_BASE_URL
QUALITY_CONFIG_WRITE_IP_WHITELIST=$QUALITY_CONFIG_WRITE_IP_WHITELIST
# 主数据源（$DRIVER）
${PFIX}_DOCTOR_HOST=$DB_HOST
${PFIX}_DOCTOR_PORT=$DB_PORT
${PFIX}_DOCTOR_USER=$APP_DB_USER
${PFIX}_DOCTOR_PASSWORD=$APP_DB_PASSWORD
# ICU 只读数据源
${PFIX}_ICU_HOST=$ICU_DB_HOST
${PFIX}_ICU_PORT=$ICU_DB_PORT
${PFIX}_ICU_USER=$ICU_DB_USER
${PFIX}_ICU_PASSWORD=$ICU_DB_PASSWORD
EOF
chmod 600 "$ENV_FILE"
info "已写入环境文件 $ENV_FILE（profile=$DB_PROFILE，权限 600）"

# 非敏感外部配置（质控白名单等；与 install.sh 约定一致）
cat > "$APP_HOME/config/application.yml" <<EOF
# zing-doctor 后端外部配置（优先级高于 jar 内 application.yml）
# 数据库连接走环境变量（见 $ENV_FILE），这里只放业务开关。
zing:
  quality:
    config-write-ip-whitelist: ${QUALITY_CONFIG_WRITE_IP_WHITELIST}
    config-default-operator: \${QUALITY_CONFIG_DEFAULT_OPERATOR:}
EOF

# ---------------- 6. systemd 服务 ----------------
info "注册 systemd 服务 zing-doctor ..."
cat > /etc/systemd/system/zing-doctor.service <<EOF
[Unit]
Description=Zing Doctor Decision Support System
After=network.target

[Service]
Type=simple
WorkingDirectory=$APP_HOME
EnvironmentFile=$ENV_FILE
ExecStart=$JAVA_BIN -Xms512m -Xmx1024m -jar $APP_HOME/app/zing-doctor.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143
StandardOutput=append:$APP_HOME/logs/backend.log
StandardError=append:$APP_HOME/logs/backend.log
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable zing-doctor
systemctl restart zing-doctor
info "后端服务已启动（systemctl status zing-doctor 查看状态）"

# ---------------- 7. nginx 前端 + 反代 ----------------
# 宿主装了 nginx 就用宿主 nginx；没装但有 Docker + 本机已有 nginx 镜像，
# 就用容器跑前端（--network host，容器内直接监听 $WEB_PORT）。
# 站点配置只有一份来源，两种方式共用；dist 挂载路径与 root 指令保持一致。
WEB_OK="no"
WEB_HOW=""

if [ "$CONFIG_ONLY" = "yes" ]; then
  WEB_OK="yes"; WEB_HOW="沿用既有前端（--config-only 未改动）"
  info "跳过：前端托管配置（--config-only）"
else

write_site_conf() {
  cat > "$1" <<EOF
server {
    listen $WEB_PORT;
    server_name _;
    root $APP_HOME/frontend/dist;
    index index.html;
    client_max_body_size 30m;

    location /api/ {
        proxy_pass http://127.0.0.1:$BACKEND_PORT;
        proxy_set_header Host \$http_host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Host \$http_host;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    location /entry/ {
        proxy_pass http://127.0.0.1:$BACKEND_PORT;
        proxy_set_header Host \$http_host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    }
    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF
}

pick_nginx_image() {
  [ -n "$NGINX_IMAGE" ] && return 0
  local cand
  for cand in nginx:stable nginx:latest nginx:alpine; do
    if docker image inspect "$cand" >/dev/null 2>&1; then NGINX_IMAGE="$cand"; return 0; fi
  done
  cand="$(docker images --format '{{.Repository}}:{{.Tag}}' 2>/dev/null | grep -E '^nginx:' | head -1 || true)"
  if [ -n "$cand" ]; then NGINX_IMAGE="$cand"; return 0; fi
  return 1
}

if [ "$WEB_MODE" != "docker" ] && [ -n "$NGINX_BIN" ]; then
  info "配置宿主 nginx（端口 $WEB_PORT，/api 反代到 127.0.0.1:$BACKEND_PORT）..."
  write_site_conf /etc/nginx/conf.d/zing-doctor.conf
  if nginx -t; then
    systemctl enable nginx || warn "nginx 开机自启设置失败"
    systemctl restart nginx || warn "nginx 重启失败，请检查：systemctl status nginx"
    WEB_OK="yes"; WEB_HOW="宿主 nginx"
  else
    warn "nginx 配置校验未通过，已跳过启用；配置文件保留在 /etc/nginx/conf.d/zing-doctor.conf"
  fi
elif [ "$WEB_MODE" != "nginx" ] && have docker; then
  info "宿主未安装 nginx：尝试用本机已有的 nginx 镜像跑前端容器 ..."
  if pick_nginx_image; then
    info "使用镜像 $NGINX_IMAGE（容器名 $NGINX_CONTAINER，--network host 监听 $WEB_PORT）"
    mkdir -p "$APP_HOME/config"
    write_site_conf "$APP_HOME/config/nginx-zing-doctor.conf"
    docker rm -f "$NGINX_CONTAINER" >/dev/null 2>&1 || true
    if docker run -d --name "$NGINX_CONTAINER" --restart=always --network host \
         -v "$APP_HOME/frontend/dist:$APP_HOME/frontend/dist:ro" \
         -v "$APP_HOME/config/nginx-zing-doctor.conf:/etc/nginx/conf.d/default.conf:ro" \
         "$NGINX_IMAGE" >/dev/null; then
      WEB_OK="yes"; WEB_HOW="nginx 容器($NGINX_CONTAINER)"
      info "前端容器已启动（dist 为挂载，改文件无需重启容器）"
    else
      warn "前端容器启动失败，请查看：docker logs $NGINX_CONTAINER"
    fi
  else
    warn "本机没有可用的 nginx 镜像（离线无法拉取）。可指定：NGINX_IMAGE=<本机镜像>"
  fi
fi

if [ "$WEB_OK" != "yes" ]; then
  warn "前端未托管：产物已就位 $APP_HOME/frontend/dist"
  warn "  可用任意静态服务器托管，并把 /api、/entry 反代到 127.0.0.1:$BACKEND_PORT"
fi
fi

# ---------------- 8. 健康检查 ----------------
info "等待后端启动（最多 60 秒）..."
ok=no
for i in $(seq 1 30); do
  if have curl; then
    curl -fs "http://127.0.0.1:$BACKEND_PORT/api/health" >/dev/null 2>&1 && { ok=yes; break; }
  elif (exec 3<>"/dev/tcp/127.0.0.1/$BACKEND_PORT") >/dev/null 2>&1; then
    # 无 curl 时退化为端口探测：只能证明进程已监听，不能证明健康检查内容
    ok=yes; break
  fi
  sleep 2
done

HOST_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"; [ -z "$HOST_IP" ] && HOST_IP="<服务器IP>"
echo ""
echo "=================================================="
if [ "$ok" = "yes" ]; then
  echo -e "  ${GREEN}后端健康检查通过${NC}"
else
  echo -e "  ${YELLOW}后端尚未响应健康检查（首次启动可能较慢），请查日志：${NC}"
  echo "    journalctl -u zing-doctor -n 80 --no-pager"
  echo "    tail -n 80 $APP_HOME/logs/backend.log"
fi
if [ "$WEB_OK" = "yes" ]; then
  echo "  前端入口 : http://$HOST_IP:$WEB_PORT/   （$WEB_HOW）"
else
  echo "  前端入口 : （未托管，请自行托管 $APP_HOME/frontend/dist）"
fi
echo "  后端接口 : http://$HOST_IP:$BACKEND_PORT/api/health"
echo "  数据库   : $DB_KIND $DB_VER @ $DB_HOST:$DB_PORT / $DOCTOR_DB（应用账号 $APP_DB_USER）"
echo ""
echo "  服务管理 :"
echo "    systemctl status|restart zing-doctor"
echo "    tail -f $APP_HOME/logs/backend.log"
echo ""
echo "  默认管理员: admin / zing@123（首次启动自动创建，请及时改密）"
echo "=================================================="
