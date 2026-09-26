#!/usr/bin/env bash
# =====================================================================
# 医生决策系统 - 一键安装部署脚本
#
# 用法：将交付包解压后，在 zing-doctor 根目录执行：
#     bash install.sh
#
# 部署路径 = 脚本所在目录（当前解压路径），不写死任何绝对路径。
#
# 流程：
#   1) 环境检查（docker / docker compose / 达梦连通）
#   2) 达梦数据库初始化（统一用 SYSDBA，自动选择通道，顺序尝试）：
#        00_init_user.sql：CREATE SCHEMA "zing_doctor_db_prod" AUTHORIZATION SYSDBA（建医生决策系统自有模式）
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
echo "  医生决策系统 · 一键安装部署"
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
# ---------- disql 探测（全局：全量初始化与增量升级共用）----------
DB_DISQL=""
detect_disql() {
  local p
  # ⚠️ 全部用 if 而非 `cmd && var=...`：脚本开头有 `set -e`，
  # `cmd && var=...` 在 cmd 失败时整条 AND-list 返回非 0，会让 install.sh 直接退出
  # （典型：服务器没装 disql 时，`command -v disql` 失败 → 脚本静默中断）。
  if command -v disql >/dev/null 2>&1; then
    DB_DISQL="disql"
  fi
  if [ -z "$DB_DISQL" ]; then
    for p in /opt/dmdbms/bin/disql /dm8/bin/disql /opt/dm8/bin/disql /dmdbms/bin/disql \
             /home/dmdba/dmdbms/bin/disql /usr/local/dmdbms/bin/disql \
             /data/dmdbms/bin/disql /app/dmdbms/bin/disql /usr/local/bin/disql; do
      if [ -x "$p" ]; then DB_DISQL="$p"; break; fi
    done
  fi
  # find 兜底搜索（限时3秒，避免卡住）
  if [ -z "$DB_DISQL" ]; then
    DB_DISQL="$(timeout 3 find / -name disql -type f 2>/dev/null | head -1)"
    if [ -n "$DB_DISQL" ]; then info "通过 find 定位 disql: $DB_DISQL"; fi
  fi
}
detect_disql

# ---------- 全量初始化（新库：建模式 + 建表 + 种子）----------
# ⚠️ 新增 sql/NN_xxx.sql 必须同时维护三处清单：
#      FULL_SQL（disql 全量）、FULL_SQL_JDBC（JDBC 全量）、INCREMENTAL_SQL（老库增量）。
#    漏维护的表现：新装环境缺表；或老库升级不补表，页面报「无效的表或视图名」。
#    22_ards_prone_config.sql 曾因漏加导致「ARDS 数据映射」页 500，故改为数组集中维护。
#
# ⚠️ 顺序 = 依赖顺序，不要随意调整：
#   01 建模式 → 02 种子 → 06/07/08 业务表 → 09/10/11 质控表 → 12/13/14 系统表
#   → 15~20 给上述表加列 → 21 ARDS 俯卧位 5 张表（参数种子写 sys_param，由 14 建）
#   → 22 ARDS 映射配置表 + patient_doc_prone_record 加列（依赖 21）
#   → 25/26 表名规范化 rename（空库无旧表，整段跳过；放在最后不影响全新安装顺序）。
FULL_SQL=(
    "00_init_user.sql"
    "01_schema.sql"
    "02_seed.sql"
    "06_abx_drug_dict.sql"
    "07_sofa.sql"
    "08_sofa_p1.sql"
    "09_quality.sql"
    "10_quality_config.sql"
    "11_quality_count_rule.sql"
    "12_archive.sql"
    "13_auth.sql"
    "14_param_framework.sql"
    "15_quality_patient_fields.sql"
    "16_quality_fatality_ref.sql"
    "17_quality_rule_local.sql"
    "18_quality_manual_audit.sql"
    "19_quality_target_direction.sql"
    "20_quality_fact_patient_default_cols.sql"
    "21_ards_prone.sql"
    "22_ards_prone_config.sql"
    "23_ards_prone_sign_work_no.sql"
    # 25/26 表名规范化（zing_* → sys_*/config_*/patient_doc_*、qc_fact_* → quality_fact_*、
    # ards_prone_*/apache2_*/sofa_*/sepsis_* → patient_doc_*/config_*）。
    # 全新库没有旧表，全部跳过；半初始化库按存在性逐个改名，幂等。
    "25_rename_doctor_tables.sql"
    "26_rename_clinical_tables.sql"
    # 27 配置快照：把现场维护过的配置灌回新库（17 张配置表 / 1204 行，2026-09-22 达梦生产库导出）。
    #    必须排最后 —— 它按「整表 DELETE + INSERT」把 02_seed 灌的出厂配置覆盖成生产值。
    #    ⚠️ 只加进 FULL_SQL（全新初始化）；INCREMENTAL_SQL 里绝不能加，否则每次升级都会
    #       把现场配置打回 2026-09-22 的快照。重新部署后想更新快照就重跑 DmExport 覆盖本文件。
    "27_restore_config_snapshot.sql"
    # 28 质控每日批算参数（QUALITY_BACKFILL_DAYS）。**必须排在 27 之后**：
    #    27 把 sys_param 整表 DELETE + INSERT 成快照，排它前面会被快照覆盖掉；
    #    而该快照导出于切换每日批算之前，本就不含这个键 —— 覆盖即静默丢失。
    #    漏执行的后果可控（Java 侧回退默认值 3），但现场将无法按院方节奏调整回溯窗口。
    "28_quality_daily_param.sql"
    # 29 患者工作台页面注册（全新库初始化）
    "29_patient_workbench.sql"
    # 24 质控配置写保护总开关（QUALITY_CONFIG_WRITE_OPEN）。同样**必须排在 27 之后**：
    #    它也是 sys_param 里的一条参数，排 27 前面同样会被配置快照整表覆盖掉。
    #    漏执行的后果可控（Java 侧回退 application.yml 的 config-write-open 默认 false，
    #    即保持严格模式），但参数设置页看不到这个开关，现场无法在页面上切换。
    "24_quality_config_guard.sql"
    # 30 患者工作台的科室边界：新建 workbench 参数分组 + WORKBENCH_SUPER_USERS 管理员名单。
    #    同样必须排在 27 之后：它往 sys_param 插一条数据，排 27 前面会被配置快照整表覆盖。
    #    漏执行的后果：Java 侧回退 application.yml 的 zing.workbench.super-users
    #    （默认 admin,zing），科室边界照常生效，但现场无法在页面上增删管理员。
    "30_user_depart_scope.sql"
    # 31 抗感染「待决策判定规则」：新建 antibiotic 参数分组 + ABX_PENDING_DECISION_RULE。
    #    同样必须排在 27 之后（写 sys_param）。漏执行表现：列表「待决策」固定按
    #    「当日无决策记录」统计，切换不到「入科超 24 小时且从未决策」。
    "31_abx_pending_rule.sql"
    # 32 疑似感染列表并入患者工作台：更新页面注册展示名（保留 old path，仅改名称与说明）。
    #    同样必须排在 27 之后，否则被配置快照覆盖回旧名称。漏执行不影响功能，只是名称没变。
    "32_abx_page_merge.sql"
)

# JDBC 通道比 disql 通道多两个：03 ICU 库性能索引、05 APACHE2 PDF 列（历史上 disql 通道就没带，保持原样）
FULL_SQL_JDBC=(
    "00_init_user.sql"
    "01_schema.sql"
    "02_seed.sql"
    "03_icu_indexes.sql"
    "05_apache2_pdf.sql"
    "06_abx_drug_dict.sql"
    "07_sofa.sql"
    "08_sofa_p1.sql"
    "09_quality.sql"
    "10_quality_config.sql"
    "11_quality_count_rule.sql"
    "12_archive.sql"
    "13_auth.sql"
    "14_param_framework.sql"
    "15_quality_patient_fields.sql"
    "16_quality_fatality_ref.sql"
    "17_quality_rule_local.sql"
    "18_quality_manual_audit.sql"
    "19_quality_target_direction.sql"
    "20_quality_fact_patient_default_cols.sql"
    "21_ards_prone.sql"
    "22_ards_prone_config.sql"
    "23_ards_prone_sign_work_no.sql"
    "25_rename_doctor_tables.sql"
    "26_rename_clinical_tables.sql"
    # 27 配置快照（同上，仅全新初始化；不进 INCREMENTAL_SQL）
    "27_restore_config_snapshot.sql"
    # 28 质控每日批算参数（同 FULL_SQL：必须排 27 之后，否则被配置快照覆盖）
    "28_quality_daily_param.sql"
    # 29 患者工作台页面注册（全新库初始化）
    "29_patient_workbench.sql"
    # 24 质控配置写保护总开关（同 FULL_SQL：必须排 27 之后，否则被配置快照覆盖）
    "24_quality_config_guard.sql"
    # 30 患者工作台科室边界（同 FULL_SQL：必须排 27 之后，否则被配置快照覆盖）
    "30_user_depart_scope.sql"
    # 31 抗感染「待决策判定规则」（同 FULL_SQL：必须排 27 之后）
    "31_abx_pending_rule.sql"
    # 32 页面注册展示名更新（同 FULL_SQL：必须排 27 之后）
    "32_abx_page_merge.sql"
)

# ---------- 增量升级（幂等脚本，可重复执行）----------
# 老库（表已存在）会跳过全量初始化，新增的表/列就靠这里自动补上，
# 避免「代码更新了、表没改」导致页面 500（典型：无效的列名[param_type]）。
# 只放**幂等**脚本：每个 DDL 都先判断存在性，重跑不会报「对象已存在」。
# 一次性脚本（如 06_abx_drug_dict.sql 的裸 CREATE TABLE）不要加进来。
# 12_archive.sql 虽含裸 CREATE TABLE/SEQUENCE，但 JDBC 通道会先查元数据跳过（幂等）。
#
# ⚠️ 顺序 = 依赖顺序，不要随意调整：
#   09/10/11 建表（质控结果表 / 配置真源三表 / 规则表）→ 15/16/17/18/19 给这些表加列。
#   DbInit 的幂等只覆盖 CREATE 与 ADD COLUMN，**不检查 ALTER 的目标表是否存在** ——
#   一旦把 15/16/17 排到 10/11 前面，老库上会因「表不存在」直接抛错并中断该文件，
#   连排在后面的 12/13/14 都跑不到（老库漏建 sys_param 相关列多半就是这么来的）。
#   12 必须排在 14 之前：14 会给 sys_param 加列，而该表由 12 创建。
#
# 09/10/11 的写入都是幂等的（页面注册先 DELETE 再 INSERT，种子 INSERT 带 WHERE NOT EXISTS），
# 且这三份脚本内均无 DROP TABLE / TRUNCATE —— 对已有数据只会「补表补列」，不会清数据。
# 把 09/10/11 纳入增量（而不是只靠人工执行）是必须的：老库漏跑 10 会让
# config-source 兜底回退 yaml，表现为「配置页只能看、没有新增按钮」，且不报任何错。
INCREMENTAL_SQL=(
    # 25/26 表名规范化必须排在所有脚本之前：历史脚本（01/21/22/23…）里的表名已统一改成新名，
    # 若不先 rename，它们在老库上会「新建一张新名的空表」而不是补到原有表上，
    # 表现为页面能打开但数据全不见（且旧表变成孤儿表）。先改名，后续脚本才补到正确的表上。
    # 幂等：仅当「旧表存在 且 新表不存在」时执行，全新库整段跳过。
    "25_rename_doctor_tables.sql"
    "26_rename_clinical_tables.sql"
    "09_quality.sql"
    "10_quality_config.sql"
    "11_quality_count_rule.sql"
    "15_quality_patient_fields.sql"
    "16_quality_fatality_ref.sql"
    "17_quality_rule_local.sql"
    "18_quality_manual_audit.sql"
    "19_quality_target_direction.sql"
    "20_quality_fact_patient_default_cols.sql"
    "12_archive.sql"
    "13_auth.sql"
    "14_param_framework.sql"
    # 28 质控每日批算参数（依赖 14 预置的 quality 分组与 param_type 等扩展列，故排其后；幂等可重复）
    "28_quality_daily_param.sql"
    # 24 质控配置写保护总开关（同 28：依赖 14 预置的 quality 分组与扩展列，故排其后；幂等可重复）
    "24_quality_config_guard.sql"
    # 30 患者工作台科室边界（同 28：依赖 14 预置的 param_type 等扩展列与 sys_param_group，
    #    故排其后；幂等可重复）
    "30_user_depart_scope.sql"
    # 29 患者工作台页面注册
    "29_patient_workbench.sql"
    # 21 建 ARDS 俯卧位 5 张表 + 页面注册 + 参数种子；参数种子写 sys_param（14 建），故排最后
    "21_ards_prone.sql"
    # 22 建 ARDS 采集映射配置表 + patient_doc_prone_record 日期扩列（依赖 21，故排其后）；
    #    漏执行表现：参数设置页「ARDS 数据映射」点「一键从内置生成」500「无效的表或视图名[config_prone_item]」
    "22_ards_prone_config.sql"
    # 23 给 patient_doc_prone_record 补三个签名人工号列（依赖 21）；
    #    漏执行表现：文书签名区只打印姓名、不显示电子签名图，且保存记录报「无效的列名[doctor_work_no]」
    "23_ards_prone_sign_work_no.sql"
    # 31 抗感染「待决策判定规则」（依赖 14 预置的参数分组表与 param_type 等扩展列，故排其后；幂等可重复）
    "31_abx_pending_rule.sql"
    # 32 疑似感染列表并入工作台后的页面注册展示名（幂等 UPDATE，未注册时影响 0 行）
    "32_abx_page_merge.sql"
)

# ---------- JDBC 初始化工具 classpath ----------
# ⚠️ 交付包里的 tools/db-init/DbInit.class 是预编译产物：若 DbInit.java 比它新
# （典型：改了源码却没重新编译就打包），必须现场重编译，否则跑的还是旧逻辑
# ——曾因旧 class 把 PL/SQL 块按分号切碎，导致 13/14 初始化脚本报「语法分析出错」。
# 结果写入全局变量 DBINIT_CP（不用命令替换，避免 info 日志混进 classpath）。
DBINIT_CP=""
resolve_dbinit_cp() {
  DBINIT_CP=""
  if command -v javac >/dev/null 2>&1 && [ -f "tools/db-init/DbInit.java" ] \
     && { [ ! -f "tools/db-init/DbInit.class" ] || [ "tools/db-init/DbInit.java" -nt "tools/db-init/DbInit.class" ]; }; then
    info "编译 JDBC 初始化工具（DbInit.java 比 DbInit.class 新）..."
    mkdir -p tools/db-init-classes
    if javac -encoding UTF-8 -cp "lib/DmJdbcDriver18-8.1.3.140.jar" \
         -d tools/db-init-classes tools/db-init/DbInit.java >/dev/null 2>&1; then
      DBINIT_CP="tools/db-init-classes"
      return 0
    fi
    warn "javac 编译失败，回退使用交付包自带的 DbInit.class"
  fi
  if [ -f "tools/db-init/DbInit.class" ]; then
    DBINIT_CP="tools/db-init"
  elif [ -f "tools/db-init-classes/DbInit.class" ]; then
    DBINIT_CP="tools/db-init-classes"
  fi
}

# 用可用通道执行若干 SQL 文件：本机 disql → 容器内 disql → JDBC 工具。
# 返回 0=成功、1=执行报错、2=无任何可用通道（无法执行，也无法校验）。
# ⚠️ 调用处必须放在 if / || 等条件上下文里：脚本开头有 set -e。
db_exec() {
  local logfile="$1"; shift
  local files=("$@")
  local p CID

  if [ -n "$DB_DISQL" ]; then
    cat "${files[@]}" | "$DB_DISQL" "$ADMIN_USER/$ADMIN_PASS@$DM_HOST_PORT" >"$logfile" 2>&1
    return $?
  fi

  # ⚠️ 末尾 || true 不能去：grep 无匹配时返回 1，若将来启用 pipefail（或 docker ps 本身失败），
  # 这条赋值就会在 set -e 下把整个部署打断 —— 而「没有达梦容器」是完全正常的分支，要走下去试 JDBC。
  CID="$(docker ps --format '{{.Names}} {{.Image}}' 2>/dev/null | grep -iE 'dm8|dameng' | head -1 | awk '{print $1}' || true)"
  if [ -n "$CID" ]; then
    for p in /opt/dmdbms/bin/disql /dm8/bin/disql /opt/dm8/bin/disql; do
      if docker exec "$CID" test -x "$p" 2>/dev/null; then
        cat "${files[@]}" | docker exec -i "$CID" "$p" "$ADMIN_USER/$ADMIN_PASS@$DM_HOST_PORT" >"$logfile" 2>&1
        return $?
      fi
    done
  fi

  if command -v java >/dev/null 2>&1 && [ -f "lib/DmJdbcDriver18-8.1.3.140.jar" ]; then
    resolve_dbinit_cp
    local _cp="$DBINIT_CP"
    if [ -n "$_cp" ]; then
      java -cp "lib/DmJdbcDriver18-8.1.3.140.jar:$_cp" \
           DbInit "jdbc:dm://$DM_HOST_PORT" "$ADMIN_USER" "$ADMIN_PASS" "${files[@]}" >"$logfile" 2>&1
      return $?
    fi
  fi

  return 2
}

apply_incremental() {
  local files=()
  local f
  for f in "${INCREMENTAL_SQL[@]}"; do files+=("$ROOT/sql/$f"); done
  local logfile=/tmp/zing-db-incr.log

  local rc=0
  db_exec "$logfile" "${files[@]}" || rc=$?
  if [ "$rc" = "2" ]; then
    warn "未找到可用通道自动应用增量脚本，请手动执行 sql/：${INCREMENTAL_SQL[*]}"
  elif [ "$rc" = "0" ]; then
    info "增量脚本执行完成"
  else
    warn "增量脚本返回非 0（详见 $logfile）：多为「对象已存在」提示，可忽略"
  fi
  verify_quality_columns
}

# ---------------- 增量升级校验：关键列到底加上了没有 ----------------
# apply_incremental 对失败只 warn 后继续 —— 这是必要的（老库重复加列属正常噪声），
# 但副作用是「漏执行」与「已执行」在日志里长得一样。生产库正是这样缺了
# value_source，直到人工录入保存 500（无效的列名[value_source]）才被发现，
# 而这时脚本已经在 install.sh 里躺了很久。
#
# 所以这里显式复查，并且不靠解析表格输出（格式随通道而变，很脆）：
# 缺列就让 PL/SQL 抛错、通道返回非 0，判断只有「通过」和「没通过」两种。
verify_quality_columns() {
  local vf=/tmp/zing-verify-cols.sql
  local log=/tmp/zing-verify-cols.log
  cat > "$vf" <<'SQL'
DECLARE
  v_missing VARCHAR(2000) := '';
  PROCEDURE chk(p_table VARCHAR, p_col VARCHAR) IS
    c INT;
  BEGIN
    SELECT COUNT(*) INTO c FROM ALL_TAB_COLUMNS
     WHERE UPPER(TABLE_NAME) = UPPER(p_table) AND UPPER(COLUMN_NAME) = UPPER(p_col);
    IF c = 0 THEN
      v_missing := v_missing || ' ' || UPPER(p_table) || '.' || UPPER(p_col);
    END IF;
  END;
BEGIN
  chk('QUALITY_METRIC_RESULT', 'VALUE_SOURCE');
  chk('QUALITY_METRIC_RESULT', 'OPERATOR');
  chk('QUALITY_METRIC_RESULT', 'MANUAL_NOTE');
  chk('QUALITY_COUNT_RULE', 'TARGET_DIRECTION');
  IF LENGTH(v_missing) > 0 THEN
    RAISE_APPLICATION_ERROR(-20001, '缺少列:' || v_missing
      || ' —— 请手动执行 sql/18_quality_manual_audit.sql 与 sql/19_quality_target_direction.sql');
  END IF;
END;
/
SQL

  local rc=0
  db_exec "$log" "$vf" || rc=$?
  if [ "$rc" = "2" ]; then
    warn "无可用的数据库通道，跳过关键列校验；请自行确认 18/19 两个脚本已执行"
    return 0
  fi
  if [ "$rc" = "0" ]; then
    info "关键列校验通过（quality_metric_result: value_source/operator/manual_note、quality_count_rule: target_direction）"
    return 0
  fi

  err "关键列缺失，停止部署（详见 $log）"
  err "  缺 value_source → 人工录入保存 500「无效的列名[value_source]」，且重算不再保护人工值"
  err "  缺 target_direction → 「质控指标」看板加载失败「无效的列名[target_direction]」"
  err "  请先手动执行 sql/18_quality_manual_audit.sql 与 sql/19_quality_target_direction.sql，再重启服务"
  exit 1
}

init_db() {
  local logfile=/tmp/zing-dbinit.log
  local DISQL="$DB_DISQL"
  local p=""
  # 全量脚本路径（清单见文件上方 FULL_SQL / FULL_SQL_JDBC，新增 SQL 记得同步）
  local files=()
  local jfiles=()
  local f
  for f in "${FULL_SQL[@]}";      do files+=("$ROOT/sql/$f");  done
  for f in "${FULL_SQL_JDBC[@]}"; do jfiles+=("$ROOT/sql/$f"); done
  # 表已存在则跳过初始化（重复部署场景，避免报错）
  # ⚠️ 表名是双引号小写建的，比较必须统一 UPPER；否则老库识别不出来，会去重跑全量并报错
  # ⚠️ 新旧名都要认：sys_page_config 由 25 号 rename 而来（旧名 zing_page_config）。
  #    只判断新名的话，尚未执行过 rename 的老库会被当成空库去重跑全量：
  #    01_schema.sql 会按新名再建一套空表，老表留在原地变成孤儿表（数据看起来「全没了」）。
  if [ -n "$DISQL" ]; then
    local _exist="$(echo "SELECT COUNT(*) FROM all_tables WHERE UPPER(owner)='ZING_DOCTOR_DB_PROD' AND UPPER(table_name) IN ('ZING_PAGE_CONFIG','SYS_PAGE_CONFIG');" \
         | "$DISQL" "$ADMIN_USER/$ADMIN_PASS@$DM_HOST_PORT" 2>/dev/null \
         | grep -oE '[0-9]+' | tail -1)"
    if [ -n "$_exist" ] && [ "$_exist" -gt 0 ] 2>/dev/null; then
      info "检测到 zing_doctor_db_prod 的业务表（sys_page_config / 旧名 zing_page_config）已存在，跳过全量初始化（如需重建请先 DROP SCHEMA）"
      # 老库升级：自动套用增量脚本（幂等），不再要求人工执行 SQL
      apply_incremental
      warn "老库升级：sql/06_abx_drug_dict.sql 为一次性脚本，若从未执行过需手动执行一次，否则抗菌药识别词库刷新会持续告警“无效的表或视图名[zing_abx_drug_dict]”"
      warn "老库升级：质控建表（09/10/11）与加列（15/16/17）已纳入自动增量；若上面增量日志出现失败，才需手动逐个执行这 6 个脚本（均幂等可重复）"
      warn "老库升级：若 sql/10_quality_config.sql 未成功执行，配置真源会兜底回退 yaml —— 表现是「配置页只能看、没有新增按钮」，且不报任何错，排查时先查这三张表在不在（quality_metric_def / quality_fact_def / quality_def_history）"
      warn "老库升级：sql/11_quality_count_rule.sql 建表后，需在看板点一次「同步指标规则」从 ICU 侧灌数，否则看板「质控指标」页为空"
      return 0
    fi
  fi

  if [ -n "$DISQL" ]; then
    info "通道 a：本机 disql 初始化达梦（建模式+建表+种子）..."
    if cat "${files[@]}" \
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
        if cat "${files[@]}" \
             | docker exec -i "$CID" "$p" "$ADMIN_USER/$ADMIN_PASS@$DM_HOST_PORT" >"$logfile" 2>&1; then
          info "达梦初始化完成（容器 $CID，模式 zing_doctor_db_prod）"; return 0
        fi
        warn "达梦容器通道失败，尝试 JDBC 通道（详见 $logfile）"
        break
      fi
    done
  fi

  # 通道 c：本机 JDK + JDBC 初始化工具
  # 优先使用交付包预编译的 tools/db-init/DbInit.class（服务器无需 javac）；
  # 但若 DbInit.java 比 .class 新（改了源码没重新编译就打包），现场重编译，避免跑旧逻辑。
  if command -v java >/dev/null 2>&1 && [ -f "lib/DmJdbcDriver18-8.1.3.140.jar" ]; then
    resolve_dbinit_cp
    local _cp="$DBINIT_CP"
    if [ -n "$_cp" ]; then
      info "通道 c：运行 JDBC 初始化工具连接达梦（classpath: $_cp）..."
      if java -cp "lib/DmJdbcDriver18-8.1.3.140.jar:$_cp" \
              DbInit "jdbc:dm://$DM_HOST_PORT" "$ADMIN_USER" "$ADMIN_PASS" "${jfiles[@]}"; then
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
  echo "    start $ROOT/sql/10_quality_config.sql # 质控配置真源三表 + 注册 quality-config 页（幂等可重复）"
  echo "    start $ROOT/sql/11_quality_count_rule.sql # 质控「真指标」规则表（幂等；建表后需在看板点「同步指标规则」灌数）"
  echo "    start $ROOT/sql/12_archive.sql      # 系统参数表 + 归档流水表 + 评分记录归档列（幂等可重复）"
  echo "    start $ROOT/sql/13_auth.sql         # 直连登录账号表（幂等可重复；后端首启自动写入 admin 账号）"
  echo "    start $ROOT/sql/14_param_framework.sql # 参数框架：参数分组表 + zing_sys_param 类型扩展列（幂等可重复）"
  echo "    start $ROOT/sql/15_quality_patient_fields.sql # 质控：quality_metric_def 加 patient_fields 列（幂等；漏执行则质控配置页 500：无效的列名[patient_fields]）"
  echo "    start $ROOT/sql/16_quality_fatality_ref.sql # 质控：加引用列 + 两条病死率改自动计算 + 人员数量改人工填报（幂等；漏执行则质控配置页 500：无效的列名[numerator_metric]）"
  echo "    start $ROOT/sql/17_quality_rule_local.sql # 质控：指标规则加 origin/local_override 列，支持本院自建规则（幂等；漏执行则配置页「指标规则」页签加载失败、新增规则报无效的列名[origin]）"
  echo "    start $ROOT/sql/18_quality_manual_audit.sql # 质控：加 value_source 列（幂等；漏执行则人工录入保存 500）"
  echo "    start $ROOT/sql/19_quality_target_direction.sql # 质控：加 target_direction 列（幂等；漏执行则质控看板加载失败）"
  echo "    start $ROOT/sql/20_quality_fact_patient_default_cols.sql # 质控：事实表加默认列（幂等）"
  echo "    start $ROOT/sql/21_ards_prone.sql # ARDS 俯卧位 5 张表 + 页面注册 + 参数种子（幂等）"
  echo "    start $ROOT/sql/22_ards_prone_config.sql # ARDS 采集映射配置表 + 日期扩列（幂等；漏执行则「ARDS 数据映射」页 500）"
  echo "    start $ROOT/sql/23_ards_prone_sign_work_no.sql # ARDS 签名人工号列（幂等；漏执行则文书不显示电子签名图、保存报无效的列名[doctor_work_no]）"
  exit 1
}

# 表已存在（重复部署）时允许跳过初始化
if [ "$1" = "--skip-db" ]; then
  warn "跳过数据库初始化（--skip-db）"
else
  init_db
fi

# ---------- 3. 构建并启动 ----------

# 生成后端外部配置文件 $ROOT/config/application.yml
#
# Spring Boot 默认加载 jar 同级 ./config/ 下的 application.yml，优先级高于 jar 内配置
# （直连模式 cwd=$ROOT → 读 $ROOT/config/；容器模式 WORKDIR=/app → 由 compose 挂载到 /app/config）。
# 因此把「每台服务器都不一样」的项集中放这里：运维可直接改文件，不必动交付包。
#
# 幂等策略（避免重装把已配好的白名单清空）：
#   传了 QUALITY_CONFIG_WRITE_IP_WHITELIST → 写入实际值（原文件先备份为 .bak.<时间戳>）
#   未传 且 文件已存在                    → 原样保留
#   未传 且 文件不存在                    → 生成占位符版，行为等同默认（仍由环境变量决定）
write_backend_config() {
  local dir="$ROOT/config"
  local file="$dir/application.yml"
  mkdir -p "$dir"
  if [ -n "${QUALITY_CONFIG_WRITE_IP_WHITELIST:-}" ]; then
    if [ -f "$file" ]; then
      cp -f "$file" "$file.bak.$(date +%Y%m%d%H%M%S)" && info "已备份原配置：$file.bak.*"
    fi
    cat > "$file" <<EOF
# zing-doctor 后端外部配置（由 install.sh 生成，优先级高于 jar 内 application.yml）
# 这里只放「每台服务器不同」的项，其余配置见交付包 src/main/resources/application.yml。
# 改完需重启后端生效：pkill -f zing-doctor.jar && bash install.sh
zing:
  quality:
    # 质控配置写接口白名单（POST /api/quality/config/**：保存指标/事实层、回滚、批量导入、重载）
    # 逗号分隔、支持前缀匹配：194.1.3. 等价于 194.1.3.*（同网段同事无需再单独加）
    # 留空 = 这些写请求一律 403（刻意的 fail-closed）；只读看板 GET 请求不受影响。
    config-write-ip-whitelist: ${QUALITY_CONFIG_WRITE_IP_WHITELIST}
    # 默认操作人：内网直连打开配置页（无外链 realname）时，变更历史里记录的名字。
    # 不配则记 unknown，事后无法追溯是谁改的口径。粒度是「这台工作站」不是「这个人」。
    config-default-operator: \${QUALITY_CONFIG_DEFAULT_OPERATOR:}
EOF
    info "已写入 $file（质控配置写白名单：${QUALITY_CONFIG_WRITE_IP_WHITELIST}）"
  elif [ -f "$file" ]; then
    info "沿用已有配置 $file（本次未传 QUALITY_CONFIG_WRITE_IP_WHITELIST，不覆盖）"
  else
    cat > "$file" <<'EOF'
# zing-doctor 后端外部配置（install.sh 生成；优先级高于 jar 内 application.yml）
# 这里只放「每台服务器不同」的项，其余配置见交付包 src/main/resources/application.yml。
zing:
  quality:
    # 质控配置写接口白名单（POST /api/quality/config/**：保存指标/事实层、回滚、批量导入、重载）
    # 逗号分隔、支持前缀匹配：194.1.3. 等价于 194.1.3.*
    # 留空 = 这些写请求一律 403（刻意的 fail-closed）；只读看板 GET 请求不受影响。
    # 下行为占位符：仍由环境变量 QUALITY_CONFIG_WRITE_IP_WHITELIST 决定，也可直接填网段后重启。
    config-write-ip-whitelist: ${QUALITY_CONFIG_WRITE_IP_WHITELIST:}
    # 默认操作人：内网直连打开配置页（无外链 realname）时，变更历史里记录的名字。
    # 不配则记 unknown，事后无法追溯是谁改的口径。粒度是「这台工作站」不是「这个人」。
    config-default-operator: ${QUALITY_CONFIG_DEFAULT_OPERATOR:}
EOF
    info "已生成默认配置 $file（白名单留空 = 写接口 403，需要时填网段后重启后端）"
  fi
}
write_backend_config

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
  # 质控配置写接口白名单（POST /api/quality/config/**：保存指标/事实层、回滚、批量导入、重载配置）。
  # 不配则这些写请求一律 403（刻意的 fail-closed：能改全院质控口径的入口不默认开放）。
  # 逗号分隔、支持前缀匹配，填质控科/信息科工作站的 IP 或网段，浏览器端无需任何改动，例：
  #   QUALITY_CONFIG_WRITE_IP_WHITELIST="194.1.3.,100.120.1." ./install.sh
  info "启动后端：java -jar $JAR（ICU_DATA_PROVIDER=sql，连接真实 ICU 库）..."
  ICU_DATA_PROVIDER=sql \
  EXTERNAL_LINK_SECRET="${EXTERNAL_LINK_SECRET:-zing-doctor-prod-secret-change-me}" \
  ICU_LINK_TOKEN="${ICU_LINK_TOKEN:-zing-icu-link-token-2026}" \
  EXTERNAL_LINK_BASE_URL="${EXTERNAL_LINK_BASE_URL:-}" \
  QUALITY_CONFIG_WRITE_IP_WHITELIST="${QUALITY_CONFIG_WRITE_IP_WHITELIST:-}" \
  nohup java -jar "$JAR" > "$ROOT/logs/backend.log" 2>&1 &
  echo $! > "$ROOT/backend.pid"
  info "后端 PID: $(cat "$ROOT/backend.pid")（日志：logs/backend.log）"
  if [ -n "${EXTERNAL_LINK_BASE_URL:-}" ]; then
    info "外链跳转基础地址（显式配置）：EXTERNAL_LINK_BASE_URL=${EXTERNAL_LINK_BASE_URL}"
  else
    info "外链跳转地址：跟随外部系统访问地址（请求 Host），无需配置 EXTERNAL_LINK_BASE_URL"
  fi
  if [ -n "${QUALITY_CONFIG_WRITE_IP_WHITELIST:-}" ]; then
    info "质控配置写接口白名单：${QUALITY_CONFIG_WRITE_IP_WHITELIST}"
  else
    warn "未配置 QUALITY_CONFIG_WRITE_IP_WHITELIST：质控配置页的保存/回滚/导入/重载将一律 403"
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
    if curl -m 2 -s -o /dev/null "http://127.0.0.1:8081/api/health"; then
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
