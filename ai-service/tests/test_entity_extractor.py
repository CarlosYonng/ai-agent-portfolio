"""实体抽取器单元测试。"""

from __future__ import annotations

from app.core.entity_extractor import extract_entities


def test_extracts_rule_entities_for_rag_graph():
    text = "PAY_5001 调用 /api/pay/callback 失败。"

    entities = extract_entities(text, domain_terms={})
    by_name = {entity["name"]: entity for entity in entities}

    assert by_name["PAY_5001"]["type"] == "ERROR_CODE"
    assert by_name["PAY_5001"]["source"] == "rule"
    assert by_name["PAY_5001"]["mentions"][0]["text"] == "PAY_5001"
    assert by_name["/api/pay/callback"]["type"] == "API_ENDPOINT"


def test_extracts_business_terms_from_dictionary():
    text = "支付回调需要先做签名校验。"

    entities = extract_entities(
        text,
        domain_terms={
            "支付回调": "BUSINESS_TERM",
            "签名校验": "BUSINESS_TERM",
        },
    )
    names = {entity["name"] for entity in entities}

    # 字典术语必须被抽取；附加的中文术语不影响核心断言
    assert "支付回调" in names
    assert "签名校验" in names


def test_deduplicates_repeated_mentions():
    text = "支付系统调用支付系统后返回 CUSTOMER_INVITE_CODE_GENERATE_FAILED。"

    entities = extract_entities(text)
    payment_system = next(entity for entity in entities if entity["name"] == "支付系统")
    error = next(entity for entity in entities if entity["name"] == "CUSTOMER_INVITE_CODE_GENERATE_FAILED")

    assert payment_system["type"] == "SYSTEM"
    assert payment_system["source"] == "dictionary"
    assert payment_system["occurrence_count"] == 2
    assert error["type"] == "ERROR_CODE"


def test_ignores_uppercase_config_like_tokens_as_error_codes():
    entities = extract_entities("MYSQL_DSN 未配置，但这不是业务错误码。", domain_terms={})

    # 不应把 MYSQL_DSN 识别为错误码
    error_codes = [e for e in entities if e["type"] == "ERROR_CODE"]
    api_endpoints = [e for e in entities if e["type"] == "API_ENDPOINT"]
    assert error_codes == []
    assert api_endpoints == []
