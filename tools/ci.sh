#!/usr/bin/env bash
# =====================================================================
# 医生决策系统 - 持续集成校验脚本（本地 / 任意 CI 平台通用）
#
# 设计原则：**不依赖具体 CI 平台**。GitHub Actions / GitLab CI / Jenkins
# 只需调用本脚本，换平台时改的是流水线声明，不是校验逻辑。
#
# 校验项（任一失败即退出非 0）：
#   1) 后端：mvn test         —— 评分、质控 DSL、业务计算单元测试
#   2) 前端：npm ci + lint    —— ESLint error 门槛（warning 不阻塞）
#   3) 前端：npm run build    —— 构建必须通过（Vue 模板/导入错误在这里暴露）
#   4) 依赖扫描：npm audit --omit=dev --audit-level=high
#
# 可选：
#   CI_SECURITY_SCAN=1  → 额外跑 mvn -Psecurity-check verify（需联网下载 NVD 库）
#
# 用法：
#   bash tools/ci.sh              # 全量
#   bash tools/ci.sh --backend    # 只跑后端
#   bash tools/ci.sh --frontend   # 只跑前端
# =====================================================================
set -e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

GREEN='\033[32m'; RED='\033[31m'; YELLOW='\033[33m'; NC='\033[0m'
step(){ echo -e "\n${GREEN}==>${NC} $*"; }
fail(){ echo -e "${RED}[FAIL]${NC} $*"; exit 1; }
skip(){ echo -e "${YELLOW}[SKIP]${NC} $*"; }

RUN_BACKEND=1
RUN_FRONTEND=1
case "${1:-}" in
  --backend)  RUN_FRONTEND=0 ;;
  --frontend) RUN_BACKEND=0 ;;
  "")         ;;
  *)          fail "未知参数：$1（可用：--backend / --frontend）" ;;
esac

# ---------------------------------------------------------------- 后端
if [ "$RUN_BACKEND" = "1" ]; then
  step "后端单元测试：mvn -B test"
  mvn -B test || fail "后端测试失败"

  if [ "${CI_SECURITY_SCAN:-0}" = "1" ]; then
    step "后端依赖漏洞扫描：mvn -B -Psecurity-check verify（需联网更新 NVD 库）"
    mvn -B -Psecurity-check verify || fail "后端依赖扫描发现高危漏洞（报告见 target/dependency-check-report.html）"
  else
    skip "后端 dependency-check（设置 CI_SECURITY_SCAN=1 启用，需联网）"
  fi
fi

# ---------------------------------------------------------------- 前端
if [ "$RUN_FRONTEND" = "1" ]; then
  step "前端依赖安装：npm ci"
  (cd frontend && npm ci) || fail "前端依赖安装失败"

  step "前端代码检查：npm run lint（error 门槛）"
  (cd frontend && npm run lint) || fail "前端 lint 未通过"

  step "依赖漏洞：npm run audit:ci（生产依赖，high 及以上中断）"
  (cd frontend && npm run audit:ci) || fail "前端生产依赖存在 high 及以上漏洞"

  step "前端构建：npm run build"
  (cd frontend && npm run build) || fail "前端构建失败"
fi

echo -e "\n${GREEN}==> CI 全部通过${NC}"
