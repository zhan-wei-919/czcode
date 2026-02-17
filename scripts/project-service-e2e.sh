#!/usr/bin/env bash
set -euo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "需要 jq：请先安装 jq 后再运行此脚本。"
  exit 1
fi

BASE_URL="${BASE_URL:-http://localhost:18083}"
TOKEN="${TOKEN:-}"
PROJECT_KEY="${PROJECT_KEY:-DEMO_$(date +%s)}"
PROJECT_NAME="${PROJECT_NAME:-demo-project}"
FEATURE_BRANCH_NAME="${FEATURE_BRANCH_NAME:-feature/demo-$(date +%s)}"

if [[ -z "${TOKEN}" ]]; then
  echo "请通过环境变量传入 TOKEN。示例：TOKEN='<jwt>' bash scripts/project-service-e2e.sh"
  exit 1
fi

api_get() {
  local path="$1"
  curl -sS --fail-with-body \
    -H "Authorization: Bearer ${TOKEN}" \
    "${BASE_URL}${path}"
}

api_post() {
  local path="$1"
  local body="$2"
  curl -sS --fail-with-body \
    -X POST \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${body}" \
    "${BASE_URL}${path}"
}

echo "1/5 获取当前用户..."
current_user_json="$(api_get "/api/v1/projects/me")"
owner_user_id="$(echo "${current_user_json}" | jq -r '.userId')"

echo "2/5 创建项目..."
create_project_body="$(jq -n \
  --arg key "${PROJECT_KEY}" \
  --arg name "${PROJECT_NAME}" \
  --arg owner "${owner_user_id}" \
  '{projectKey:$key, name:$name, description:"e2e demo", ownerUserId:$owner, visibility:1}')"
project_json="$(api_post "/api/v1/projects" "${create_project_body}")"
project_id="$(echo "${project_json}" | jq -r '.id')"

echo "3/5 查询 main 分支（项目创建时自动初始化）..."
branches_json="$(api_get "/api/v1/projects/${project_id}/branches")"
main_branch_id="$(echo "${branches_json}" | jq -r '.[] | select(.name=="main") | .id')"
if [[ -z "${main_branch_id}" ]]; then
  echo "未找到 main 分支，流程中止。"
  exit 1
fi

echo "4/5 创建 feature 分支并打 checkpoint..."
create_branch_body="$(jq -n \
  --arg name "${FEATURE_BRANCH_NAME}" \
  --arg basedOnBranchId "${main_branch_id}" \
  '{name:$name, branchType:2, basedOnBranchId:$basedOnBranchId}')"
feature_branch_json="$(api_post "/api/v1/projects/${project_id}/branches" "${create_branch_body}")"
feature_branch_id="$(echo "${feature_branch_json}" | jq -r '.id')"

create_checkpoint_body="$(jq -n --arg snapshotRef "s3://demo/${project_id}/checkpoint-1.tar.zst" \
  '{title:"checkpoint-1", description:"first checkpoint", snapshotRef:$snapshotRef, snapshotSizeBytes:1024, fileCount:5}')"
checkpoint_json="$(api_post "/api/v1/projects/${project_id}/branches/${feature_branch_id}/checkpoints" "${create_checkpoint_body}")"
checkpoint_id="$(echo "${checkpoint_json}" | jq -r '.id')"

echo "5/5 发布到 main..."
publish_body="$(jq -n \
  --arg sourceBranchId "${feature_branch_id}" \
  --arg targetBranchId "${main_branch_id}" \
  --arg sourceCheckpointId "${checkpoint_id}" \
  '{sourceBranchId:$sourceBranchId, targetBranchId:$targetBranchId, sourceCheckpointId:$sourceCheckpointId, publishStatus:2}')"
publish_json="$(api_post "/api/v1/projects/${project_id}/publish-records" "${publish_body}")"

echo
echo "联调完成："
echo "${publish_json}" | jq -r \
  '"projectId=\(.projectId)\nsourceBranchId=\(.sourceBranchId)\ntargetBranchId=\(.targetBranchId)\nsourceCheckpointId=\(.sourceCheckpointId)\npublishStatus=\(.publishStatus)"'
