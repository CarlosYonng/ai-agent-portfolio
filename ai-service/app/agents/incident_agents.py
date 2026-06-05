"""Java 微服务故障诊断 Agent。

链路：
1. 查日志（search_logs）
2. 查相关代码（search_code，全文 + 向量混合搜索）
3. 查历史工单（search_tickets，向量语义匹配）
4. LLM 汇总证据、排序根因、生成处理建议

通过 HTTP 调用 mcp-server 的工具接口。工具不可用时返回空结果，主流程继续。
"""

from __future__ import annotations

import time
import uuid
from typing import Any

import httpx

from app.core.settings import settings
from app.core.model_client import model_client
from app.schemas.incident import IncidentDiagnoseRequest


async def call_tool(path: str, payload: dict[str, Any]) -> dict[str, Any]:
    """调用 MCP 风格工具。

    本地直接运行 ai-service 时，mcp-server 可能不可用；此时返回空结果，主流程继续。
    """

    try:
        async with httpx.AsyncClient(timeout=8) as client:
            response = await client.post(f"{settings.mcp_base_url}{path}", json=payload)
            response.raise_for_status()
            return response.json()
    except Exception:
        return {"tool": path, "query": payload, "results": []}


async def run_incident_agent(request: IncidentDiagnoseRequest) -> dict[str, Any]:
    """运行故障诊断 Agent。"""

    trace_id = f"inc_{int(time.time())}_{uuid.uuid4().hex[:8]}"

    # 故障诊断的核心不是"让大模型猜"，而是先把日志、代码、历史工单证据拉齐。
    # 这能体现你 Java 后端经验：排障一定要基于可观测数据和变更上下文。
    logs = await call_tool(
        "/api/tools/search_logs",
        {
            "service": request.service,
            "trace_id": request.trace_id,
            "time_range": request.time_range,
            "level": "ERROR",
        },
    )
    code = await call_tool(
        "/api/tools/search_code",
        {
            "service": request.service,
            "query": request.question,
        },
    )
    tickets = await call_tool(
        "/api/tools/search_tickets",
        {
            "service": request.service,
            "symptom": request.question,
        },
    )

    evidences = collect_evidences(logs, code, tickets)
    summary = await build_summary(request.question, evidences)
    return {
        "trace_id": trace_id,
        "summary": summary,
        "root_causes": await rank_root_causes(evidences),
        "actions": await build_actions(evidences),
        "evidences": evidences,
    }


def collect_evidences(*tool_results: dict[str, Any]) -> list[dict[str, Any]]:
    """把多个工具返回统一成证据列表。"""

    evidences: list[dict[str, Any]] = []
    for result in tool_results:
        tool = result.get("tool", "unknown")
        for item in result.get("results", []):
            # source 字段保留证据来源，后续前端可以按"日志/代码/工单"分组展示。
            evidences.append({"source": tool, **item})
    return evidences


async def build_summary(question: str, evidences: list[dict[str, Any]]) -> str:
    """基于证据生成诊断摘要。"""

    if not evidences:
        return "未检索到足够日志、代码或历史工单证据。建议先确认 traceId、服务名和时间范围。"

    evidence_text = "\n".join(f"- {item}" for item in evidences[:6])
    system_prompt = "你是 Java 微服务故障诊断助手，请基于日志、代码和工单证据给出根因分析。"
    user_prompt = f"故障问题：{question}\n\n证据：\n{evidence_text}\n\n请输出一句摘要。"
    return await model_client.generate(system_prompt, user_prompt)


async def rank_root_causes(evidences: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """根据证据用 LLM 分析根因并排序。"""

    if not evidences:
        return []

    evidence_text = "\n".join(f"- {item}" for item in evidences[:10])
    system_prompt = "你是 Java 微服务故障诊断专家。基于日志、代码和工单证据，分析故障根因并排序。"
    user_prompt = (
        f"证据：\n{evidence_text}\n\n"
        f"请输出 JSON 数组，每项包含 cause（根因描述）、confidence（0-1 置信度）、"
        f"evidence（引用哪些证据）。按置信度降序，最多输出 3 条。"
    )
    text = await model_client.generate(system_prompt, user_prompt)
    try:
        import json
        causes = json.loads(text)
        return causes if isinstance(causes, list) else []
    except json.JSONDecodeError:
        return []


async def build_actions(evidences: list[dict[str, Any]]) -> list[str]:
    """基于证据用 LLM 生成可执行处理建议。"""

    if not evidences:
        return ["未检索到有效证据，建议先确认 traceId、服务名和时间范围。"]

    evidence_text = "\n".join(f"- {item}" for item in evidences[:8])
    system_prompt = "你是 Java 微服务运维专家。根据故障证据，按优先级输出可操作的故障处理步骤。"
    user_prompt = (
        f"证据：\n{evidence_text}\n\n"
        f"请输出 JSON 字符串数组，每步是一个具体操作（例如回滚、加日志、加校验等），最多 5 步。"
    )
    text = await model_client.generate(system_prompt, user_prompt)
    try:
        import json
        actions = json.loads(text)
        return actions if isinstance(actions, list) else []
    except json.JSONDecodeError:
        return []
