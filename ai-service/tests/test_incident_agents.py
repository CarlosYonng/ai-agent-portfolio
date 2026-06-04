"""故障诊断 Agent 单元测试。"""

from __future__ import annotations

from app.agents.incident_agents import collect_evidences, rank_root_causes


def test_collect_evidences_keeps_source_name():
    """聚合证据时应保留工具来源，方便前端分组展示。"""

    evidences = collect_evidences(
        {"tool": "search_logs", "results": [{"message": "NullPointerException"}]},
        {"tool": "search_code", "results": [{"file_path": "OrderCreateService.java"}]},
    )

    assert evidences == [
        {"source": "search_logs", "message": "NullPointerException"},
        {"source": "search_code", "file_path": "OrderCreateService.java"},
    ]


def test_rank_root_causes_detects_coupon_null_pointer():
    """出现 couponId/NPE 证据时，应把空值校验缺失排在根因首位。"""

    causes = rank_root_causes(
        [
            {"message": "NullPointerException at OrderCreateService.createOrder"},
            {"preview": "couponId 为空时没有跳过 CouponClient.validate"},
        ]
    )

    assert causes[0]["confidence"] > 0.8
    assert "couponId" in causes[0]["cause"]
