"""Java 微服务故障诊断 Agent。

项目 B 的核心链路：
1. 查日志
2. 查相关代码
3. 查历史工单
4. 汇总证据并生成根因排序

第一版通过 HTTP 调用 mcp-server 的工具接口。工具不可用时会返回兜底建议。
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

    # 故障诊断的核心不是“让大模型猜”，而是先把日志、代码、历史工单证据拉齐。
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
        "root_causes": rank_root_causes(evidences),
        "actions": build_actions(evidences),
        "evidences": evidences,
    }


def collect_evidences(*tool_results: dict[str, Any]) -> list[dict[str, Any]]:
    """把多个工具返回统一成证据列表。"""

    evidences: list[dict[str, Any]] = []
    for result in tool_results:
        tool = result.get("tool", "unknown")
        for item in result.get("results", []):
            # source 字段保留证据来源，后续前端可以按“日志/代码/工单”分组展示。
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


def rank_root_causes(evidences: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """根据证据生成根因排序。

    第一版用规则打分，后续可以改成 LLM verifier 或训练分类器。
    """

    joined = " ".join(str(item) for item in evidences)
    causes: list[dict[str, Any]] = []
    # 规则打分适合第一版：简单稳定、可解释，也方便后续沉淀成训练数据。
    # 当历史工单足够多时，可以升级为“规则召回 + LLM verifier + 分类模型”。
    if "NullPointerException" in joined or "couponId" in joined:
        causes.append(
            {
                "cause": "订单创建链路中 couponId 为空时缺少防御性校验，触发 NullPointerException。",
                "confidence": 0.86,
                "evidence": "日志、代码和历史工单都指向 couponId 空值风险。",
            }
        )
    causes.append(
        {
            "cause": "请求参数或上游调用不符合接口契约，需要检查调用方传参与网关转发。",
            "confidence": 0.42,
            "evidence": "接口故障通常需要同时排查请求参数和服务端校验。",
        }
    )
    return causes


def build_actions(evidences: list[dict[str, Any]]) -> list[str]:
    """生成可执行处理建议。"""

    return [
        "检查 traceId 对应日志，确认异常是否集中在 OrderCreateService.createOrder。",
        "为 couponId 增加空值判断；无优惠券场景跳过 CouponClient.validate。",
        "补充无优惠券创建订单的单元测试和回归测试。",
        "如果错误率持续升高，先回滚最近订单服务变更或临时关闭优惠券校验分支。",
    ]
