#!/usr/bin/env python3
"""本地无依赖演示脚本。

这个脚本不依赖 FastAPI、PyMySQL、Qdrant 或 Neo4j，适合在环境还没启动时快速展示效果。
它不会替代正式服务，只是把项目的核心思路用样例数据跑给你看：

1. 企业知识库 RAG：问题 -> 实体抽取 -> 文档证据召回 -> 带引用回答。
2. 微服务故障诊断：故障描述 -> 日志/代码/工单证据 -> 根因和处理建议。
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR))

from shared.entity_extractor import extract_entities


def load_text_files(directory: Path) -> list[dict[str, str]]:
    """读取目录下的 Markdown/TXT 文件。"""

    files = list(directory.glob("*.md")) + list(directory.glob("*.txt"))
    return [{"title": file.name, "content": file.read_text(encoding="utf-8")} for file in files]


def score_document(question: str, content: str) -> int:
    """非常轻量的关键词打分，用来模拟检索排序。"""

    score = 0
    for entity in extract_entities(question):
        if entity["name"] in content:
            score += 5
    for token in ["支付", "回调", "订单", "接口", "优惠券", "错误码", "500"]:
        if token in question and token in content:
            score += 1
    return score


def demo_rag(question: str) -> dict[str, object]:
    """演示知识库 RAG 效果。"""

    docs = load_text_files(ROOT_DIR / "datasets" / "kb_docs")
    ranked = sorted(docs, key=lambda doc: score_document(question, doc["content"]), reverse=True)
    evidence = ranked[0]
    entities = extract_entities(question + "\n" + evidence["content"])

    if "PAY_5001" in question:
        answer = (
            "PAY_5001 表示支付回调签名校验失败。优先检查商户密钥、sign 字段、"
            "请求体编码是否被网关改写，以及灰度环境回调地址是否正确。"
        )
    elif "couponId" in question:
        answer = "couponId 是可选字段。没有优惠券时可以不传，服务端也应该跳过优惠券校验。"
    else:
        answer = "已根据知识库证据生成回答，建议查看引用文档确认细节。"

    return {
        "question": question,
        "entities": entities,
        "answer": answer,
        "citations": [
            {
                "title": evidence["title"],
                "preview": evidence["content"][:180],
                "source": "local_demo",
            }
        ],
        "retrieval_order": ["Qdrant", "Neo4j GraphRAG", "MySQL FULLTEXT", "mock"],
    }


def demo_incident(question: str) -> dict[str, object]:
    """演示 Java 微服务故障诊断效果。"""

    log_text = (ROOT_DIR / "datasets" / "logs" / "order-error.log").read_text(encoding="utf-8")
    code_text = (ROOT_DIR / "datasets" / "demo-order-service" / "OrderCreateService.java").read_text(encoding="utf-8")
    ticket_text = (ROOT_DIR / "datasets" / "tickets" / "order_npe_ticket.md").read_text(encoding="utf-8")

    return {
        "question": question,
        "summary": "订单创建接口 500 大概率由 couponId 为空导致 OrderCreateService.createOrder 触发 NullPointerException。",
        "root_causes": [
            {
                "cause": "couponId 是可选字段，但服务端没有做空值判断，仍调用 CouponClient.validate。",
                "confidence": 0.86,
                "evidence": "日志、代码和历史工单均出现 couponId/NullPointerException 线索。",
            },
            {
                "cause": "调用方传参缺失或接口契约未说明可选字段处理方式。",
                "confidence": 0.42,
                "evidence": "订单接口文档显示 couponId 可选，需要服务端防御。",
            },
        ],
        "actions": [
            "在 OrderCreateService.createOrder 中增加 couponId 空值判断。",
            "无优惠券场景跳过 CouponClient.validate。",
            "补充无优惠券创建订单的单元测试和回归测试。",
            "如果错误率持续升高，先回滚最近订单服务变更或临时关闭优惠券分支。",
        ],
        "evidences": [
            {"source": "log", "preview": log_text.splitlines()[0]},
            {"source": "code", "preview": code_text[:180]},
            {"source": "ticket", "preview": ticket_text[:180]},
        ],
    }


def main() -> None:
    """执行两个核心演示。"""

    rag_result = demo_rag("PAY_5001 是什么意思，应该怎么处理？")
    incident_result = demo_incident("traceId=demo-trace-001 的订单创建接口 500，帮我分析根因")

    print("=== 企业知识库 RAG Agent 演示 ===")
    print(json.dumps(rag_result, ensure_ascii=False, indent=2))
    print("\n=== Java 微服务故障诊断 Agent 演示 ===")
    print(json.dumps(incident_result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

