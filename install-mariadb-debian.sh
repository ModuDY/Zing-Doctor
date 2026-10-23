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
#     SKIP_DB_INIT=yes                        库与账号已就绪时跳过建库/建账号/授权（后期更新用）
#     DB_TIMEOUT=60                           单次数据库操作超时秒数（卡住时自动中止并继续）
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
#   ./install-mariadb-debian.sh --skip-db-init  后期更新：库与账号已存在，跳过建库/建号/授权
#   优先级：命令行/环境变量 > conf/db.conf > 脚本默认值
#
# 幂等：可重复执行，建库/建表/加列/建索引均做存在性判断，不重复、不报错。
#
# SQL 重复初始化保护：执行台账记在 DOCTOR_DB 的 zing_sql_history（文件名 + 内容 MD5）。
#   已执行过的脚本再次安装直接跳过，不再把参数设置、质控口径覆盖回出厂值；
#   老库首次升级到本版本只登记基线、不重跑；全新库照常按顺序全量执行。
#   排障需要强制重跑全部：./install-mariadb-debian.sh --force-sql
# =====================================================================
set -euo pipefail

# ---------------- 控制台输出 ----------------
# 排版分三层，现场一眼就能分层：
#   步骤标题   [n/m] 当前步骤 / 总步骤，下面跟一条分隔线
#   细节行     两空格缩进
#   结果标记   [完成] / [注意] / [失败]，同样缩进
# 结尾汇总用「标签 + 值」两列：标签统一写成 4 个汉字，显示宽度一致，无需计算，
# 逐个手写空格对齐即可（汉字在等宽字体下占 2 列，用 printf 的 %-Ns 按字符数补
# 空格反而会对不齐）。
# 刻意不使用任何图标或符号字符：换终端字体、重定向进日志文件、被 CI 或聊天工具
# 抓取时都不会变形。
# ⚠️ 颜色必须用 printf 生成「真实的 ESC 字节」，不能写成 '\033[2m' 这种字面量：
#    printf 只在「格式串」里解释 \033，在「%s 的实参」里是原样输出的。
#    step/ok/warn/err 把颜色拼进了格式串 → 正常上色；
#    而 rule() 走的是实参（printf '%s%s%s\n' "$C_DIM" ...）→ 字面量被原样打出来，
#    现场看到的就是裸的 \033[2m------------\033[0m，分隔线一点没变暗。
ESC="$(printf '\033')"
# 非终端（重定向进日志 / CI 抓取 / ssh 管道）时整体关闭颜色，避免日志里混进 ANSI 乱码。
# 关闭后颜色变量为空串，此时 rule() 的格式串仍是 '%s%s%s\n'，不会变成以 "--" 开头的
# 非法选项 —— 这正是横线一定要走 %s 而不是直接写进格式串的原因。
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_RED="${ESC}[31m"; C_GREEN="${ESC}[32m"; C_YELLOW="${ESC}[33m"; C_CYAN="${ESC}[36m"
  C_BOLD="${ESC}[1m"; C_DIM="${ESC}[2m"; C_RESET="${ESC}[0m"
else
  C_RED=''; C_GREEN=''; C_YELLOW=''; C_CYAN=''; C_BOLD=''; C_DIM=''; C_RESET=''
fi
STEP_N=0; STEP_TOTAL=8

# 分隔线用 %s 承载：不能把横线直接写进格式串 —— 颜色变量一旦为空（日志重定向、
# NO_COLOR 时会被清空），格式串就以 "--" 开头，printf 会当选项报 invalid option
rule(){ printf '%s%s%s\n' "$C_DIM" "--------------------------------------------------------------------" "$C_RESET"; }
step(){
  STEP_N=$((STEP_N + 1))
  printf "\n${C_CYAN}${C_BOLD}[%d/%d]${C_RESET} ${C_BOLD}%s${C_RESET}\n" "$STEP_N" "$STEP_TOTAL" "$*"
  rule
}
info(){ printf '  %s\n' "$*"; }
ok(){   printf "  ${C_GREEN}[完成]${C_RESET} %s\n" "$*"; }
warn(){ printf "  ${C_YELLOW}[注意]${C_RESET} %s\n" "$*"; }
err(){  printf "  ${C_RED}[失败]${C_RESET} %s\n" "$*" >&2; }
# 汇总行：标签固定 4 个汉字，对齐由标签宽度保证
kv(){ printf '  %s  %s\n' "$1" "$2"; }
trap 'err "脚本在第 $LINENO 行失败，已中止（已完成的步骤是幂等的，修正后可重跑）"' ERR
[ "$(id -u)" -eq 0 ] || { err "请用 root 运行：sudo $0"; exit 1; }

# ---------------- 命令行参数 ----------------
#   --config-only  只按配置文件重写 /etc/zing-doctor/zing-doctor.env 并重启后端，
#                  不动数据库、不导 SQL、不更新前端（改库地址后最快生效的方式）
CONFIG_ONLY="no"
for _arg in "$@"; do
  case "$_arg" in
    --config-only|-c) CONFIG_ONLY="yes" ;;
    --force-sql) FORCE_SQL="yes" ;;
    --skip-db-init) SKIP_DB_INIT="yes" ;;
    -h|--help) sed -n '2,65p' "$0"; exit 0 ;;
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
    # 只接受脚本支持的键。printf -v 能设置任意变量名（PATH / IFS / LD_PRELOAD…），
    # 而本脚本以 root 运行 —— 不加限制等于「能写 conf 文件的人 = root」。
    # 下列都是本脚本自己会读的变量，不含 shell 特殊变量，放开是安全的。
    # 副作用：拼错的键不再被静默忽略成默认值，而是当场提示，省掉一次排查。
    case "$key" in
      DB_HOST|DB_PORT|DB_TYPE|DOCTOR_DB|ICU_DB|ICU_DB_HOST|ICU_DB_PORT) ;;
      DB_ADMIN_USER|DB_ADMIN_PASSWORD|APP_DB_USER|APP_DB_PASSWORD) ;;
      ICU_DB_USER|ICU_DB_PASSWORD|DB_CLI|INSTALL_SERVER) ;;
      APP_HOME|WEB_PORT|BACKEND_PORT|WEB_MODE|NGINX_IMAGE|NGINX_CONTAINER) ;;
      SKIP_APT|APT_PKGS|JAVA_BIN|NGINX_BIN|SPRING_PROFILES_ACTIVE) ;;
      ICU_DATA_PROVIDER|SETUP_ICU_INDEX|ICU_LINK_TOKEN|EXTERNAL_LINK_SECRET) ;;
      EXTERNAL_LINK_BASE_URL|QUALITY_CONFIG_WRITE_IP_WHITELIST|QUALITY_CONFIG_DEFAULT_OPERATOR) ;;
      DB_TIMEOUT|SQL_TIMEOUT|SKIP_DB_INIT) ;;
      *) warn "conf 里忽略不支持的配置项：$key（检查是否拼错）"; continue ;;
    esac
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
# 质控配置变更的兜底操作人（留空 = 记 unknown，可用 systemd drop-in 覆盖）
QUALITY_CONFIG_DEFAULT_OPERATOR="${QUALITY_CONFIG_DEFAULT_OPERATOR:-}"

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

# SQL 执行台账：记录已执行过的 sql/mysql 脚本（文件名 + MD5），避免重复初始化
SQL_HISTORY_TABLE="${SQL_HISTORY_TABLE:-zing_sql_history}"
FORCE_SQL="${FORCE_SQL:-no}"

# 单次数据库操作的超时（秒）。不加超时的话，DDL 一旦撞上元数据锁就会一直等 ——
# MariaDB 的 lock_wait_timeout 默认 31536000 秒（一年），现象就是「卡在这里不动」，
# 没有报错、没有超时、日志也不动。查询/建库/台账这类短操作用 DB_TIMEOUT；
# 导 SQL 脚本本身就是分钟级，用 SQL_TIMEOUT（默认 30 分钟）兜底。
DB_TIMEOUT="${DB_TIMEOUT:-60}"
SQL_TIMEOUT="${SQL_TIMEOUT:-1800}"
# 后期更新专用：库与账号都已存在、不想再跑建库/建号/授权时置 yes
SKIP_DB_INIT="${SKIP_DB_INIT:-no}"

# ---------------- 数据库来源判定 ----------------
# 本机地址？（注意：仅 DB_HOST 判定「库是否在本机」，应用连库地址始终由 DB_HOST/DB_PORT 决定）
host_is_local() { case "$1" in 127.0.0.1|localhost|::1) return 0;; *) return 1;; esac; }

have(){ command -v "$1" >/dev/null 2>&1; }

# 有 timeout(1) 就加上限，没有就原样跑（极简环境可能没装 coreutils 的 timeout）
TIMEOUT_BIN="$(command -v timeout 2>/dev/null || true)"

# 短操作：查询、建库建号、台账读写 —— 正常都是秒级，等一分钟还没回来说明在等锁
db_exec() {
  if [ -n "${TIMEOUT_BIN:-}" ]; then
    "$TIMEOUT_BIN" "$DB_TIMEOUT" "$@"
  else
    "$@"
  fi
}

# 长操作：导入 SQL 脚本本身可能跑几分钟，给足时间但仍有上限，避免真挂住时无限等
db_exec_sql() {
  if [ -n "${TIMEOUT_BIN:-}" ]; then
    "$TIMEOUT_BIN" "$SQL_TIMEOUT" "$@"
  else
    "$@"
  fi
}

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

# 管理员连接的附加参数，写入数组 ADMIN_CONN。分两种情况，区别只在「谁负责认证」：
#   1) 本机自建 / DB_CLI 是复合命令 → 认证已由 unix_socket 或 DB_CLI 自身完成，
#      一个参数都不能追加。⚠️ 此前 admin_query 在这里固定追加 -uroot：DB_CLI 写成
#      'docker run ... mysql -uadmin -pxxx'（文档推荐形态）时，追加的 -uroot 会把
#      命令里给的账号顶掉，静默用错账号。
#   2) 远程库 → 用 DB_ADMIN_USER 显式指定账号（此前本机分支完全不认这个变量，
#      改了 DB_ADMIN_USER 也不生效，与文档描述不符）。
admin_conn_args() {
  ADMIN_CONN=()
  if admin_is_local; then return 0; fi
  ADMIN_CONN=(-h"$DB_HOST" -P"$DB_PORT" -u"$DB_ADMIN_USER" -p"$DB_ADMIN_PASSWORD")
}

# 管理员连接：mariadb_admin [库名]  （stdin 喂 SQL，或 < 文件）
# 空数组单独走一个分支：bash 4.4 以前 `"${arr[@]}"` 在 set -u 下会报 unbound variable，
# 而这里绝大多数场景（本机自建 / 复合 DB_CLI）ADMIN_CONN 恰好是空的。
mariadb_admin() {
  local db="${1:-}"
  admin_conn_args
  if [ "${#ADMIN_CONN[@]}" -gt 0 ]; then
    db_exec $DB_CLI "${ADMIN_CONN[@]}" ${db:+"$db"}
  else
    db_exec $DB_CLI ${db:+"$db"}
  fi
}

# 同上，但用于导入 SQL 脚本（分钟级，用 SQL_TIMEOUT 而不是 60 秒）
mariadb_admin_sql() {
  local db="${1:-}"
  admin_conn_args
  if [ "${#ADMIN_CONN[@]}" -gt 0 ]; then
    db_exec_sql $DB_CLI "${ADMIN_CONN[@]}" ${db:+"$db"}
  else
    db_exec_sql $DB_CLI ${db:+"$db"}
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
  admin_conn_args
  if [ "${#ADMIN_CONN[@]}" -gt 0 ]; then
    db_exec $DB_CLI "${ADMIN_CONN[@]}" -N ${db:+"$db"} -e "$sql"
  else
    db_exec $DB_CLI -N ${db:+"$db"} -e "$sql"
  fi
}

printf "\n${C_BOLD}医生决策系统  Debian 一键安装${C_RESET}\n"
rule
if [ "$INSTALL_SERVER" = "yes" ]; then
  kv "数据来源" "本机自建（apt 安装 MariaDB 10.5）"
else
  kv "数据来源" "外部库（容器 / 远程）$DB_HOST:$DB_PORT，账号 $DB_ADMIN_USER"
fi
kv "应用目录" "$APP_HOME"
kv "服务端口" "前端 $WEB_PORT，后端 $BACKEND_PORT"
kv "ICU 索引" "$SETUP_ICU_INDEX（数据源 $ICU_DATA_PROVIDER）"
printf "\n"

# ---------------- 1. 安装系统依赖 ----------------
step "安装系统依赖（已存在则跳过，不依赖 apt）"
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
step "连接数据库并探测类型与版本"
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
step "建库、建账号与授权"
run_sql_file() {
  local file="$1"; local db="${2:-}"
  info "  执行 $(basename "$file")"
  # 强制在文件前注入 SET NAMES：本脚本是「一个文件一条连接」，而约三十个种子脚本
  # （29~49 等）开头并没有自己的 SET NAMES。客户端默认字符集跟随 locale，在
  # LANG=C（sudo / 无 locale 的服务器）下是 latin1 —— UTF-8 的中文会被按 latin1
  # 逐字节解释后存进 utf8mb4 列，于是参数页显示成「æ, £ê€...」这种双重编码乱码。
  # 放在 stdin 里而不是 --default-character-set：DB_CLI 为复合命令（docker exec）时
  # 追加的参数会传给 docker 而不是 mysql。
  { printf 'SET NAMES utf8mb4;\n'; cat "$file"; } | mariadb_admin_sql "$db"
}

# ---------------- SQL 执行台账（避免每次安装重复初始化） ----------------
# 起因：MAIN_SQL 里的脚本虽写成「幂等」，但 02_seed 及部分种子用的是
#   INSERT ... ON DUPLICATE KEY UPDATE —— 每次全量安装都会把参数设置页里调过的值、
#   质控口径改回出厂值；而 64_fix_param_mojibake 这类修复脚本重跑也没有意义。
# 方案：在 DOCTOR_DB 里维护一张台账，按「文件名 + 内容 MD5」判断是否已执行过：
#   台账有该文件      → 跳过（文件内容变了只告警，不重跑）
#   台账为空 + 库非空 → 老版本装的库：只登记基线，一条都不执行
#   全新库            → 按顺序执行，成功后逐条登记
#   FORCE_SQL=yes     → 忽略台账全部重跑（排障用，会覆盖已调参数）
SQL_BASELINE="no"
SQL_HISTORY_OK="no"
SQL_RECORDS=""
SQL_APPLIED_MAP=""
SQL_CNT_RUN=0
SQL_CNT_SKIP=0
SQL_CNT_BASE=0

# 台账里该脚本执行时的 MD5；没记录过输出 __NONE__
sql_applied_checksum() {
  printf '%s\n' "$SQL_APPLIED_MAP" | awk -F'|' -v k="$1" '
    $1 == k { print $2; found = 1; exit }
    END     { if (!found) print "__NONE__" }'
}

sql_history_flush() {
  [ -n "$SQL_RECORDS" ] || return 0
  if printf 'SET NAMES utf8mb4;\n%s' "$SQL_RECORDS" | mariadb_admin "$DOCTOR_DB" >/dev/null 2>&1; then
    SQL_RECORDS=""
  else
    warn "写入 SQL 执行台账失败（不影响本次安装，但下次可能重复执行）"
    SQL_RECORDS=""
  fi
}

sql_record() {
  local base="$1" sum="$2" by="$3" n
  # 不用 VALUES(col) 形式：MySQL 8.4 已移除该语法，直接内联值两边都能跑
  SQL_RECORDS="${SQL_RECORDS}INSERT INTO \`$SQL_HISTORY_TABLE\` (script, checksum, applied_by)
  VALUES ('$base', '$sum', '$by')
  ON DUPLICATE KEY UPDATE checksum = '$sum', applied_at = NOW(), applied_by = '$by';
"
  # 每 20 条落一次库：中途失败时最多丢一批，不会整轮重来
  n="$(printf '%s' "$SQL_RECORDS" | grep -c '^INSERT' || true)"
  [ "${n:-0}" -ge 20 ] && sql_history_flush
  return 0
}

# ---- 合并执行：把本轮要跑的脚本拼成一份，只开一次连接 ----
# 起因：MAIN_SQL 有 60 多个文件，原来每个文件都调一次客户端。DB_CLI 写成
#   'docker run --rm -i <镜像> mysql ...' 时，每一次调用都要启动一个容器（1~3 秒），
#   首次安装光启动容器就要几分钟；合并成一次连接后只剩一次。
# 前提：这些脚本本身都声明为幂等，合并只改「连接次数」，不改执行顺序与语义。
SQL_BATCH_FILE=""
SQL_BATCH_FILES=()
SQL_BATCH_NAMES=()
SQL_BATCH_SUMS=()
SQL_BATCH_BYS=()
# 中途出错退出时别把合并文件留在 /tmp；空串时直接跳过，不依赖 rm 对空操作数的处理
trap '[ -z "${SQL_BATCH_FILE:-}" ] || rm -f "$SQL_BATCH_FILE"' EXIT

# 入队：追加文件内容到合并文件；by 为空表示「没有台账，无需登记」
sql_batch_add() {
  local file="$1" base="$2" sum="$3" by="$4"
  if [ -z "$SQL_BATCH_FILE" ]; then
    SQL_BATCH_FILE="$(mktemp "${TMPDIR:-/tmp}/zing-sqlbatch-XXXXXX" 2>/dev/null || true)"
    if [ -z "$SQL_BATCH_FILE" ]; then
      # 连临时文件都建不出来（只读 /tmp、配额满）：退回单条执行，
      # 不能因为「优化」把安装卡死
      info "  执行 $base"
      { printf 'SET NAMES utf8mb4;\n'; cat "$file"; } | mariadb_admin_sql "$DOCTOR_DB"
      [ -z "$by" ] || sql_record "$base" "$sum" "$by"
      SQL_CNT_RUN=$((SQL_CNT_RUN + 1))
      return 0
    fi
    printf 'SET NAMES utf8mb4;\n' > "$SQL_BATCH_FILE"
  fi
  # 每个文件前重置定界符：存过程脚本用 DELIMITER $$，万一某份没还原成 ;
  # 合并后会把下一个文件的解析带偏（分开执行时这种状态不会跨文件泄漏）
  printf '\nDELIMITER ;\n' >> "$SQL_BATCH_FILE"
  cat "$file" >> "$SQL_BATCH_FILE" 2>/dev/null || true
  printf '\n' >> "$SQL_BATCH_FILE"
  SQL_BATCH_FILES+=("$file")
  SQL_BATCH_NAMES+=("$base")
  SQL_BATCH_SUMS+=("$sum")
  SQL_BATCH_BYS+=("$by")
  return 0
}

sql_batch_flush() {
  [ -n "$SQL_BATCH_FILE" ] || return 0
  local n="${#SQL_BATCH_NAMES[@]}" i f
  if [ "$n" -eq 0 ]; then
    rm -f "$SQL_BATCH_FILE"; SQL_BATCH_FILE=""
    return 0
  fi
  if [ "$n" -eq 1 ]; then
    info "  执行 ${SQL_BATCH_NAMES[0]}"
  else
    info "  执行 $n 个脚本（合并为一次连接）：${SQL_BATCH_NAMES[*]}"
  fi

  if mariadb_admin_sql "$DOCTOR_DB" < "$SQL_BATCH_FILE"; then
    for ((i = 0; i < n; i++)); do
      [ -z "${SQL_BATCH_BYS[i]}" ] || sql_record "${SQL_BATCH_NAMES[i]}" "${SQL_BATCH_SUMS[i]}" "${SQL_BATCH_BYS[i]}"
    done
    SQL_CNT_RUN=$((SQL_CNT_RUN + n))
    rm -f "$SQL_BATCH_FILE"; SQL_BATCH_FILE=""
    SQL_BATCH_FILES=(); SQL_BATCH_NAMES=(); SQL_BATCH_SUMS=(); SQL_BATCH_BYS=()
    return 0
  fi

  # 合并失败：逐个重跑以定位是哪一份出错。脚本都幂等，重跑安全；
  # 每成功一个立刻落账，避免后面失败退出时前面的记录一起丢掉。
  warn "合并执行失败，改为逐条执行以定位出错脚本（已成功的照常记入台账）"
  rm -f "$SQL_BATCH_FILE"; SQL_BATCH_FILE=""
  for ((i = 0; i < n; i++)); do
    f="${SQL_BATCH_FILES[i]}"
    info "  执行 ${SQL_BATCH_NAMES[i]}"
    { printf 'SET NAMES utf8mb4;\n'; cat "$f"; } | mariadb_admin_sql "$DOCTOR_DB"
    if [ -n "${SQL_BATCH_BYS[i]}" ]; then
      sql_record "${SQL_BATCH_NAMES[i]}" "${SQL_BATCH_SUMS[i]}" "${SQL_BATCH_BYS[i]}"
      sql_history_flush
    fi
    SQL_CNT_RUN=$((SQL_CNT_RUN + 1))
  done
  SQL_BATCH_FILES=(); SQL_BATCH_NAMES=(); SQL_BATCH_SUMS=(); SQL_BATCH_BYS=()
  return 0
}

sql_history_init() {
  local cnt pre exists
  exists="$(admin_query "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DOCTOR_DB' AND TABLE_NAME='$SQL_HISTORY_TABLE';" "$DOCTOR_DB" 2>/dev/null || true)"
  if [ "${exists:-}" = "1" ]; then
    SQL_HISTORY_OK="yes"
  elif printf 'SET NAMES utf8mb4;\nCREATE TABLE IF NOT EXISTS `%s` (\n  `script` VARCHAR(191) NOT NULL COMMENT %s,\n  `checksum` CHAR(32) DEFAULT NULL COMMENT %s,\n  `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,\n  `applied_by` VARCHAR(64) DEFAULT NULL COMMENT %s,\n  PRIMARY KEY (`script`)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT=%s;\n' \
      "$SQL_HISTORY_TABLE" "'脚本文件名'" "'执行时的内容 MD5'" "'installer / baseline / force'" "'安装脚本已执行的 SQL 台账'" \
      | mariadb_admin "$DOCTOR_DB" >/dev/null 2>&1; then
    SQL_HISTORY_OK="yes"
    info "已创建 SQL 执行台账：$DOCTOR_DB.$SQL_HISTORY_TABLE"
  else
    warn "无法创建 SQL 执行台账（账号可能无建表权限）：本次退回全量执行，重复执行可能覆盖已调参数"
    return 0
  fi

  cnt="$(admin_query "SELECT COUNT(*) FROM \`$DOCTOR_DB\`.\`$SQL_HISTORY_TABLE\`;" "$DOCTOR_DB" 2>/dev/null || true)"
  if [ -z "$cnt" ]; then
    SQL_HISTORY_OK="no"
    warn "读取 SQL 执行台账失败：本次退回全量执行"
    return 0
  fi
  SQL_APPLIED_MAP="$(admin_query "SELECT CONCAT(script, '|', IFNULL(checksum, '')) FROM \`$DOCTOR_DB\`.\`$SQL_HISTORY_TABLE\`;" "$DOCTOR_DB" 2>/dev/null || true)"

  if [ "$cnt" = "0" ]; then
    # 台账为空：区分「全新库」和「老版本装过、但没有台账」
    pre="$(admin_query "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DOCTOR_DB' AND TABLE_NAME IN ('sys_param','quality_metric_def','quality_fact_def');" "$DOCTOR_DB" 2>/dev/null || true)"
    if [ -n "${pre:-}" ] && [ "$pre" != "0" ]; then
      SQL_BASELINE="yes"
      warn "检测到 $DOCTOR_DB 已初始化但没有执行台账（老版本安装）"
      warn "  本次只登记基线、不重复执行 SQL；确需重跑请用 --force-sql，或删台账里对应行"
    fi
  fi
}

# 按台账决定「跳过 / 只登记基线 / 入队待执行」：
# 真正执行在 sql_batch_flush 里合并完成（不是每个文件一次连接）。
# 注：合并只针对 DOCTOR_DB，调用方也只有这一个库。
run_sql_once() {
  local file="$1" base sum stored by=""
  base="$(basename "$file")"
  sum="$(md5sum "$file" 2>/dev/null | awk '{print $1}')"
  sum="${sum:-unknown}"

  if [ "$SQL_HISTORY_OK" != "yes" ]; then
    # 没有台账：照旧全跑，只是改为合并执行（by 为空 = 不记账）
    sql_batch_add "$file" "$base" "$sum" ""
    return 0
  fi

  stored="$(sql_applied_checksum "$base")"
  if [ "$FORCE_SQL" != "yes" ] && [ "$stored" != "__NONE__" ]; then
    # 内容变了也只告警不重跑：改过的脚本可能已带上现场数据修补，重跑反而危险
    [ "$stored" = "$sum" ] || warn "  $base 内容与登记时不一致，仍按已执行跳过（强制重跑用 --force-sql）"
    SQL_CNT_SKIP=$((SQL_CNT_SKIP + 1))
    return 0
  fi

  if [ "$SQL_BASELINE" = "yes" ]; then
    sql_record "$base" "$sum" "baseline"
    SQL_CNT_BASE=$((SQL_CNT_BASE + 1))
    return 0
  fi

  if [ "$FORCE_SQL" = "yes" ]; then by="force"; else by="installer"; fi
  sql_batch_add "$file" "$base" "$sum" "$by"
  return 0
}

# ---------------- SQL 清单自检 ----------------
# 起因：23_external_link_params.sql 曾长期是「孤岛文件」—— 躺在 sql/mysql/ 下，
# 但没被登记进任何清单，于是装完缺外链参数，日志里完全看不出问题。
# 所以这里查两个方向：清单里登记的文件在不在；目录里有没有没登记的 .sql。
check_sql_manifest() {
  local f base missing="" unlisted=""
  # known = 已知且刻意不登记进 MAIN_SQL 的脚本（各有单独处理或人为执行）
  #   00b/00c 幂等辅助过程（单独执行）  00_init_user 建库已由脚本自身完成
  #   03_icu_indexes ICU 库索引（SETUP_ICU_INDEX=yes 时单独执行）
  #   24_fix_id_auto_increment 历史库修复（单独执行）
  #   03_word_* / 04_abx_word_training 可选词库（默认不导）
  #   27_restore_config_snapshot 配置快照（刻意不自动执行，见 MAIN_SQL 上方说明）
  #   install-all.sql 打包时由 tools/build-delivery.ps1 合成的一次性脚本（给库侧 DBA 用），
  #     交付包里它和本目录其他脚本并存，不参与本脚本的执行流程
  local known=" 00b_idempotent_helpers_doctor.sql 00c_idempotent_helpers_icu.sql
    00_init_user.sql 03_icu_indexes.sql 24_fix_id_auto_increment.sql
    03_word_inc_v23111.sql 03_word_inc_v2319.sql 04_abx_word_training.sql
    27_restore_config_snapshot.sql install-all.sql "
  for f in "${MAIN_SQL[@]}"; do
    [ -f "$SQL_DIR/$f" ] || missing="$missing $f"
  done
  for f in "$SQL_DIR"/*.sql; do
    base="$(basename "$f")"
    case " ${MAIN_SQL[*]} " in *" $base "*) continue ;; esac
    case "$known" in *" $base "*) continue ;; esac
    unlisted="$unlisted $base"
  done
  if [ -n "$missing" ]; then
    err "MAIN_SQL 清单里登记的脚本不存在:$missing"
    return 1
  fi
  if [ -n "$unlisted" ]; then
    warn "sql/mysql/ 下有未登记进 MAIN_SQL 的脚本:$unlisted"
    warn "  需要执行 → 加进 MAIN_SQL；确认不需要 → 加进 check_sql_manifest 的 known 白名单"
  fi
  return 0
}

# ---------------- 配置真源表校验：三张表到底建了没有 ----------------
# 与达梦版 install.sh 的 verify_quality_tables 对应：增量/全量执行失败只 warn，
# 漏建表和已建表在日志里长得一样。quality_fact_def 缺失时后端静默回退 yaml，
# 页面不报错，直到调用 /api/quality/config/facts 才 500。这里显式复查。
verify_quality_tables() {
  local missing
  # 一次连接查出「缺哪几张」，不再 3 次独立查询：
  # DB_CLI 写成 docker run 时，每次查询都要启动一个容器，这类校验积少成多。
  # GROUP_CONCAT 保证永远返回一行（不缺则是 'OK'），免得「查询失败」和「都不缺」同样输出为空。
  missing="$(admin_query "SELECT IFNULL(GROUP_CONCAT(t.n), 'OK') FROM (SELECT 'quality_metric_def' AS n UNION ALL SELECT 'quality_fact_def' UNION ALL SELECT 'quality_def_history') t WHERE NOT EXISTS (SELECT 1 FROM information_schema.TABLES i WHERE i.TABLE_SCHEMA='$DOCTOR_DB' AND i.TABLE_NAME=t.n);" "$DOCTOR_DB" 2>/dev/null || true)"
  if [ -z "$missing" ]; then
    err "配置真源表校验查询失败（读不到 information_schema）"
    err "  请确认数据库连接与账号权限；确认无误后重跑本脚本"
    exit 1
  fi
  if [ "$missing" != "OK" ]; then
    missing=" $(printf '%s' "$missing" | tr ',' ' ')"
    err "配置真源表缺失:$missing"
    err "  缺 quality_fact_def → 质控配置页「事实层」Tab 500「无效的表或视图名[quality_fact_def]」"
    err "  缺 quality_def_history → 配置变更历史 Tab 不可用，且保存配置时无法留痕"
    err "  缺 quality_metric_def → 配置真源回退 yaml，页面只能看、没有新增按钮"
    err "  请先手动执行 sql/mysql/10_quality_config.sql（幂等可重复），再重启服务"
    exit 1
  fi
  info "配置真源表校验通过（quality_metric_def / quality_fact_def / quality_def_history）"
}

# ---------------- 消息推送对象校验：表 / 分组 / 参数条数 ----------------
# 与达梦版 install.sh 的 verify_msgpush_objects 对应。58_msg_push.sql 执行失败只 warn，
# 而「缺 msgpush 分组」的表现是参数设置页左侧根本没有这一项 —— 页面不报错、
# 后端日志也没线索，现场只能从「我明明部署了新包」开始猜。这里显式复查并中止。
verify_msgpush_objects() {
  local row no_table no_group param_cnt
  # 三个计数一次连接查完（原来 3 次），结果是一行三列、以 TAB 分隔
  row="$(admin_query "SELECT (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DOCTOR_DB' AND TABLE_NAME='msg_push_log'), (SELECT COUNT(*) FROM \`$DOCTOR_DB\`.\`sys_param_group\` WHERE group_code='msgpush'), (SELECT COUNT(*) FROM \`$DOCTOR_DB\`.\`sys_param\` WHERE param_group='msgpush');" "$DOCTOR_DB" 2>/dev/null || true)"
  if [ -z "$row" ]; then
    err "消息推送对象校验查询失败（读不到 msg_push_log / sys_param_group / sys_param）"
    err "  若刚升级过，先执行 sql/mysql/58_msg_push.sql（幂等），再重跑本脚本"
    exit 1
  fi
  no_table="$(printf '%s' "$row" | cut -f1)"
  no_group="$(printf '%s' "$row" | cut -f2)"
  param_cnt="$(printf '%s' "$row" | cut -f3)"
  # 取不到列时 cut 给空串，下面 -lt 会报「integer expression expected」，先归零
  case "$param_cnt" in ''|*[!0-9]*) param_cnt=0 ;; esac
  case "$no_table" in ''|*[!0-9]*) no_table=0 ;; esac
  case "$no_group" in ''|*[!0-9]*) no_group=0 ;; esac

  # 三个值都是「查到了几行」，所以判定是 =0（没查到才是缺），不是 !=0
  if [ "$no_table" = "0" ] || [ "$no_group" = "0" ] || [ "${param_cnt:-0}" -lt 30 ]; then
    err "消息推送对象缺失，停止部署"
    err "  审计表 msg_push_log：$([ "$no_table" = "0" ] && echo 缺 || echo 有)"
    err "  msgpush 分组：$([ "$no_group" = "0" ] && echo 缺 || echo 有)"
    err "  参数条数：${param_cnt:-0}（应 30）"
    err "  缺 msg_push_log 表 → 发送审计无处落库，测试发送直接失败"
    err "  缺 msgpush 分组   → 参数设置页左侧不显示「消息推送」，功能完全看不到且后端无任何报错"
    err "  参数不足 30 条    → 平台真实报文所需字段（如 senderCode/format）无处可填，网关判参数校验不通过"
    err "  请先手动执行 sql/mysql/58_msg_push.sql（幂等可重复），再重启服务"
    exit 1
  fi
  info "消息推送校验通过（msg_push_log 表 + msgpush 分组 + $param_cnt 条参数）"
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
  if [ "$SKIP_DB_INIT" = "yes" ]; then
    info "跳过建库、建账号与授权（SKIP_DB_INIT=yes）：沿用已存在的库与账号"
    info "  若刚改过 conf/db.conf 里的账号密码，需去掉该开关重跑一次，库侧密码才会同步"
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
  #
  # SET SESSION lock_wait_timeout：这一句是「卡住」的解药。这些 DDL 拿不到元数据锁时，
  # MariaDB 默认会等 lock_wait_timeout = 31536000 秒（一年）—— 现场看到的就是
  # 「停在『建库、建账号与授权』不动，没有报错、日志也不再输出」。改成 20 秒即报错返回。
  if mariadb_admin <<SQL
SET SESSION lock_wait_timeout = 20;
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
    db_init_rc=$?
    if [ "$db_init_rc" -eq 124 ]; then
      warn "建库/建账号/授权超时（${DB_TIMEOUT} 秒未返回），本次跳过，继续后面的步骤"
      warn "  多半是数据库端在等锁（应用正在批算或有长事务）。另开一个窗口看谁在等："
      warn "    $DB_CLI -e 'SHOW FULL PROCESSLIST' | grep -iE 'Waiting|lock'"
      warn "  等批算结束再重跑本脚本即可；库与账号已就绪的更新场景建议直接加 --skip-db-init"
    else
      warn "建库/建账号/授权未全部成功（账号可能已存在，或当前管理员无授权权限）"
    fi
    warn "  若库由 DBA 管理，请让其确认：账号 $APP_DB_USER 对主库 $DOCTOR_DB 有 ALL、对 ICU 库 $ICU_DB 有 SELECT"
  fi
  fi   # 结束 SKIP_DB_INIT 判断

  # ---------------- 4. 导入建表 + 种子脚本 ----------------
step "导入建表与种子脚本（已执行过的自动跳过）"
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
    # 23_external_link_params.sql：外链参数分组 + EXTERNAL_LINK_BASE_URL /
    #    EXTERNAL_LINK_ICU_TOKEN 两条 sys_param。达梦侧这两条写在 14_param_framework.sql
    #    里，MySQL 版的 14 只写了 AUTO_REGISTER_* 三条 —— 故 MariaDB 侧必须在这里单独补，
    #    此前它没被登记进任何清单（孤岛文件），装完缺参数且不报任何错，表现为
    #    「参数设置 - 外链页面」少两个配置项。依赖 14 建的 sys_param_group / sys_param，
    #    故排其后；幂等（不存在才插）。
    23_external_link_params.sql
    15_quality_patient_fields.sql 16_quality_fatality_ref.sql
    17_quality_rule_local.sql 18_quality_manual_audit.sql
    19_quality_target_direction.sql 20_quality_fact_patient_default_cols.sql
    21_ards_prone.sql 22_ards_prone_config.sql 23_ards_prone_sign_work_no.sql
    # 25/26 表名规范化 rename（人工改写的存储过程版；全新 MariaDB 库无旧表 → 全部跳过，幂等）
    25_rename_doctor_tables.sql 26_rename_clinical_tables.sql
    # ⚠️ 27_restore_config_snapshot.sql 刻意不进本清单：它是「17 张配置表整表 DELETE +
    #    INSERT」的生产配置快照（1204 行），达梦侧只在确认全新库时才执行。这里若自动跑，
    #    一旦判断失误就会把现场维护的配置打回快照日期。因此 MariaDB 侧两套库的初始化
    #    结果天然不同（达梦新库带快照，MariaDB 新库是 02_seed 出厂值）。
    #    确需与达梦侧一致的「全新库」，人工执行一次：
    #      mysql <库名> < sql/mysql/27_restore_config_snapshot.sql
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
    # 31/32 抗感染「待决策」口径参数与页面合并。达梦侧 install.sh 三处清单均已含，
    #    此处漏登记会导致 MariaDB 库装完缺这批参数与页面注册
    31_abx_pending_rule.sql 32_abx_page_merge.sql
    # 33 抗感染 48～72 小时复评任务与留痕
    33_antibiotic_reassessment.sql
    34_system_check_page.sql
    35_workbench_view_mode.sql
    36_workbench_score_todo_rule.sql
    37_abx_pkpd_knowledge.sql
    38_doc_header.sql
    39_sidebar_logo.sql
    40_qc_fact_def_seed.sql
    42_workbench_bed_sort_mode.sql
    43_workbench_score_display.sql
    44_patient_round_record.sql
    45_antibiotic_reassessment_window.sql
    46_nutrition_formula.sql
    47_quality_patient_monthly_page.sql
    48_external_link_pages.sql
    49_lab_renal_page.sql
    # 50 质控缺口落地：导管事件源 / DVT 预防 / 床位资源 / ARDS 支持时长（事实层 + 27 条指标改 IMPL）
    #    并新建表 patient_doc_device_infection（导管相关感染确诊录入）。内含建表 + 建索引，须排最前。
    # 51 CAUTI 发病率 LOCAL 规则（其余比率规则 ICU 侧已有，原子项落地后自动出数）
    # 52 导管相关感染确诊页注册（依赖 50 建的 patient_doc_device_infection 表）
    # 三处清单必须同步：install.sh 的 FULL_SQL / FULL_SQL_JDBC / INCREMENTAL_SQL 与
    # tools/build-delivery.ps1 的 myOrder（漏登记 → MariaDB 库或 install-all.sql 装出的库缺表缺页面）
    50_quality_gap_impl.sql
    51_quality_rule_local_gap.sql
    52_device_infection_page.sql
    # 53 导管类型分组配置表 / 54 院感分析+ARDS筛查页注册
    # 55 血流动力学+ADL 手工录入表 / 56 血流动力学+超声教学页注册
    53_tube_group_config.sql
    54_infection_ards_pages.sql
    55_hemodynamics_adl.sql
    56_manual_pages.sql
    57_device_status_page.sql
    # 58 高风险预警消息推送：参数分组 + 配置项 + 发送审计表
    58_msg_push.sql
    # 59 按平台真实成功报文升级 58 的报文模板与配套参数（幂等，可重复执行）
    59_fix_msgpush_template.sql
    # 60 血管活性药分析页注册（原为 58 号，与 58_msg_push.sql 撞号一直未被登记）
    60_vasoactive_page.sql
    # 61/62/63 消息推送修复：清掉示例授权码、成功标识改为 code=00000、高风险预警规则补全
    #   均为幂等 UPDATE/INSERT，须排在 58/59 之后（否则被 59 的模板覆盖回去）
    61_fix_msgpush_certificate.sql
    62_msgpush_success_key.sql
    63_msgpush_alert.sql
    # 66 数据项映射配置表：观察项编码 / LIS 项目名 → 程序内部键
    #    换院部署只改这一处（页面上改，不必改代码重打包）；表为空时业务回退内置常量
    66_config_data_item.sql
    41_patient_summary_page.sql
    # 64 修复参数页乱码：早期逐文件导入时缺 SET NAMES，UTF-8 中文被按 latin1 存成
    #   双重编码。判据严格（正常中文会得到 '?' 被排除），幂等，新库执行为空操作。
    #   必须排最后：它修的是前面所有脚本种进去的数据。
    64_fix_param_mojibake.sql
    # 65 参数值列放宽：院徽/侧边栏 logo 这类图片参数存的是 base64 dataURI，
    #   VARCHAR(1000) 必然被截断（严格模式直接 1406），上传后图片加载不出来。
    #   纯 DDL，查 information_schema 判重，幂等。
    65_param_value_mediumtext.sql
    )
  # 清单自检：登记的脚本必须存在；目录里也不能有未登记的孤岛脚本
  check_sql_manifest
  # 台账先行：判断本轮是「全新库」「已装过的库」还是「老库补基线」
  sql_history_init
  for f in "${MAIN_SQL[@]}"; do
    [ -f "$SQL_DIR/$f" ] || { warn "缺少 $f，跳过"; continue; }
    run_sql_once "$SQL_DIR/$f" "$DOCTOR_DB"
  done
  # 上面只是按台账排好队，这里一次性提交（一次连接，而不是 60 多次）
  sql_batch_flush
  sql_history_flush
  info "SQL 执行统计：新执行 $SQL_CNT_RUN 个，跳过 $SQL_CNT_SKIP 个（台账已记录），仅登记基线 $SQL_CNT_BASE 个"
  if [ "$SQL_CNT_BASE" -gt 0 ]; then
    warn "本次为老库补基线：$SQL_CNT_BASE 个脚本登记为「已执行」但未真正执行"
    warn "  其中若含本版本新增的脚本，需单独补跑：先 DELETE FROM $SQL_HISTORY_TABLE WHERE script='文件名'; 再重跑本脚本"
  fi
  info "执行明细：SELECT script, applied_at, applied_by FROM $DOCTOR_DB.$SQL_HISTORY_TABLE ORDER BY applied_at"
  info "主库表数量：$(admin_query "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DOCTOR_DB';")"
  verify_quality_tables
  verify_msgpush_objects

  # 可选识别词库（默认不导，需要时取消注释）
  # for f in 03_word_inc_v23111.sql 03_word_inc_v2319.sql 04_abx_word_training.sql; do
  #   run_sql_file "$SQL_DIR/$f" "$DOCTOR_DB"
  # done

  # ICU 只读库性能索引（默认关闭：这是对医院生产库的变更，需 DBA 知情）
  if [ "$SETUP_ICU_INDEX" = "yes" ]; then
    info "在 ICU 库 $ICU_DB_HOST:$ICU_DB_PORT 准备幂等存储过程并执行索引脚本 ..."
    # 同上：这两个文件开头没有 SET NAMES，统一在 stdin 前注入
    { printf 'SET NAMES utf8mb4;\n'; cat "$SQL_DIR/00c_idempotent_helpers_icu.sql"; } | mariadb_icu_admin
    { printf 'SET NAMES utf8mb4;\n'; cat "$SQL_DIR/03_icu_indexes.sql"; } | mariadb_icu_admin
    info "ICU 库性能索引已处理（幂等，已存在则跳过）"
  else
    warn "已跳过 ICU 库性能索引（SETUP_ICU_INDEX=no）。若应用查询 ICU 库较慢，"
    warn "经 DBA 评估后可执行：SETUP_ICU_INDEX=yes sudo -E $0"
  fi
fi

# ---------------- 5. 部署应用文件 ----------------
step "部署应用文件（后端 jar / 前端产物 / 配置）"
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
# ⚠️ 刻意「不整文件重写」：现场会在这个文件里追加数据源 url 覆盖、日志级别等，
#    整文件重写会一并抹掉；更常见的是白名单被写成空值，页面立刻退回
#    「质控配置写接口未启用 / 只读预览」。规则改为：
#      文件不存在 → 生成骨架；已存在 → 只补/改这两个键，conf 里没给值就保留文件原值。
APP_YML="$APP_HOME/config/application.yml"
mkdir -p "$APP_HOME/config"

# 读已有 yml 里某键的值（去掉行尾空白与包裹引号）；没有则输出空
yaml_get() {
  [ -f "$APP_YML" ] || return 0
  awk -v k="$1" '
    $0 ~ "^[[:space:]]*" k "[[:space:]]*:" {
      sub("^[[:space:]]*" k "[[:space:]]*:[[:space:]]*", "")
      sub(/[[:space:]]+$/, "")
      gsub(/^"|"$/, "")
      print
      exit
    }' "$APP_YML"
}

# 设置某键：已存在则换值并保留原缩进，不存在则插到 quality: 下一行
yaml_set() {
  local key="$1" val="$2" tmp
  [ -f "$APP_YML" ] || return 0
  tmp="$(mktemp)" || return 0
  if grep -qE "^[[:space:]]*${key}[[:space:]]*:" "$APP_YML"; then
    awk -v k="$key" -v v="$val" '
      $0 ~ "^[[:space:]]*" k "[[:space:]]*:" {
        match($0, /^[[:space:]]*/)
        printf "%s%s: %s\n", substr($0, 1, RLENGTH), k, v
        next
      }
      { print }' "$APP_YML" > "$tmp"
  else
    awk -v k="$key" -v v="$val" '
      { print }
      /^[[:space:]]*quality[[:space:]]*:[[:space:]]*$/ && !done { print "    " k ": " v; done = 1 }
      END { if (!done) printf "zing:\n  quality:\n    %s: %s\n", k, v }' "$APP_YML" > "$tmp"
  fi
  cat "$tmp" > "$APP_YML"
  rm -f "$tmp"
}

if [ ! -f "$APP_YML" ]; then
  cat > "$APP_YML" <<EOF
# zing-doctor 后端外部配置（优先级高于 jar 内 application.yml）
# 数据库连接走环境变量（见 $ENV_FILE），这里只放业务开关。
zing:
  quality:
    config-write-ip-whitelist: "${QUALITY_CONFIG_WRITE_IP_WHITELIST}"
    config-default-operator: "\${QUALITY_CONFIG_DEFAULT_OPERATOR:}"
EOF
  info "已生成外部配置 $APP_YML"
else
  cp -a "$APP_YML" "$APP_YML.bak"
  WL_OLD="$(yaml_get config-write-ip-whitelist)"
  if [ -n "$QUALITY_CONFIG_WRITE_IP_WHITELIST" ] && [ "$QUALITY_CONFIG_WRITE_IP_WHITELIST" != "$WL_OLD" ]; then
    yaml_set config-write-ip-whitelist "\"$QUALITY_CONFIG_WRITE_IP_WHITELIST\""
    info "白名单已按 conf 更新：${WL_OLD:-（空）} -> $QUALITY_CONFIG_WRITE_IP_WHITELIST"
  elif [ -z "$WL_OLD" ]; then
    warn "白名单为空：conf/db.conf 未配 QUALITY_CONFIG_WRITE_IP_WHITELIST，文件里也是空，质控配置页将只读"
  else
    info "白名单沿用 $APP_YML 现有值：$WL_OLD（conf 里没配就不动它）"
  fi
  if [ -n "$QUALITY_CONFIG_DEFAULT_OPERATOR" ]; then
    yaml_set config-default-operator "\"$QUALITY_CONFIG_DEFAULT_OPERATOR\""
    info "兜底操作人已写入：$QUALITY_CONFIG_DEFAULT_OPERATOR"
  fi
fi

# ---------------- 6. systemd 服务 ----------------
step "配置 systemd 服务（开机自启与崩溃拉起）"
info "注册 systemd 服务 zing-doctor ..."
cat > /etc/systemd/system/zing-doctor.service <<EOF
[Unit]
Description=Zing Doctor Decision Support System
After=network.target

[Service]
Type=simple
WorkingDirectory=$APP_HOME
EnvironmentFile=$ENV_FILE
# Java 8 的默认 file.encoding 跟随 locale：systemd 启动的服务没有 LANG，
# 进程 locale 是 POSIX/C，file.encoding 会退化成 ANSI_X3.4-1968(ASCII)，
# 于是 stdout 里的中文（日志、启动横幅）全变成 '?'。显式给 UTF-8 即可。
# C.UTF-8 是 glibc 内置的，不需要额外装语言包，Debian 10+ 都可用。
Environment=LANG=C.UTF-8
ExecStart=$JAVA_BIN -Dfile.encoding=UTF-8 -Xms512m -Xmx1024m -jar $APP_HOME/app/zing-doctor.jar
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
step "配置 nginx 前端与反向代理"
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
step "健康检查与结果汇总"
info "等待后端启动（最多 60 秒）..."
HEALTHY=no
for i in $(seq 1 30); do
  if have curl; then
    curl -fs "http://127.0.0.1:$BACKEND_PORT/api/health" >/dev/null 2>&1 && { HEALTHY=yes; break; }
  elif (exec 3<>"/dev/tcp/127.0.0.1/$BACKEND_PORT") >/dev/null 2>&1; then
    # 无 curl 时退化为端口探测：只能证明进程已监听，不能证明健康检查内容
    HEALTHY=yes; break
  fi
  sleep 2
done

HOST_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"; [ -z "$HOST_IP" ] && HOST_IP="<服务器IP>"
printf "\n"
rule
printf "  ${C_BOLD}部署完成${C_RESET}\n"
rule
if [ "$HEALTHY" = "yes" ]; then
  ok "后端健康检查通过"
else
  warn "后端尚未响应健康检查（首次启动可能较慢），请查日志："
  printf '    %s\n' "journalctl -u zing-doctor -n 80 --no-pager"
  printf '    %s\n' "tail -n 80 $APP_HOME/logs/backend.log"
fi
printf "\n"
if [ "$WEB_OK" = "yes" ]; then
  kv "前端入口" "http://$HOST_IP:$WEB_PORT/（$WEB_HOW）"
else
  kv "前端入口" "未托管，请自行托管 $APP_HOME/frontend/dist"
fi
kv "后端接口" "http://$HOST_IP:$BACKEND_PORT/api/health"
kv "数据连接" "$DB_KIND $DB_VER @ $DB_HOST:$DB_PORT / $DOCTOR_DB（应用账号 $APP_DB_USER）"
kv "服务管理" "systemctl status|restart zing-doctor"
kv "日志查看" "tail -f $APP_HOME/logs/backend.log"
kv "默认账号" "admin / zing@123（首次启动自动创建，请及时改密）"
rule
info "下一步：浏览器打开前端入口核对版本号，再到「参数设置 - 外链页面」复制各页完整外链"
rule
