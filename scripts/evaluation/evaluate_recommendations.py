"""可复现的离线推荐评估协议（默认离线，不调用任何付费模型/API）。

本脚本只读取本地文件并计算，不发网络请求、不访问百炼或任何模型 API、
不内置任何实验结论。输出只包含脚本真实计算所得结果或明确的未执行状态。

协议版本见 PROTOCOL_VERSION。数据集、指标、输入边界与运行命令的完整定义见
docs/evaluation-protocol.md。

用法：
    python scripts/evaluation/evaluate_recommendations.py
    python scripts/evaluation/evaluate_recommendations.py --fixture <json> --report <json> --pretty
"""

import argparse
import json
import random
import sys
from pathlib import Path

PROTOCOL_VERSION = "evaluation-protocol-v1"
DEFAULT_SEED = 20260804
MAX_SUMMARY_LENGTH = 500
MAX_REASON_LENGTH = 1200
MIN_ITEM_COUNT = 2
MAX_ITEM_COUNT = 4
MIN_DISTINCT_CATEGORIES = 2
CANONICAL_CATEGORIES = ("外套", "上装", "下装", "鞋履", "配饰")
DEFAULT_FIXTURE = Path(__file__).with_name("fixture_wardrobe.json")


def valid_text(value, max_length):
    """与后端 validText 一致：非空且去除首尾空白后长度不超过 max_length。"""
    if value is None or not isinstance(value, str):
        return False
    stripped = value.strip()
    return len(stripped) > 0 and len(stripped) <= max_length


def matches_category(category, target):
    """与后端 matchesCategory 一致：鞋履包含"鞋"，其余目标作为子串匹配。"""
    if category is None:
        return False
    normalized = str(category).strip()
    if target == "鞋履":
        return "鞋" in normalized
    return target in normalized


def canonical_category(category):
    """与后端 canonicalCategory 一致：归一到五大类别，未匹配类别按原样保留。"""
    if category is None:
        return ""
    normalized = str(category).strip()
    for canonical in CANONICAL_CATEGORIES:
        if matches_category(normalized, canonical):
            return canonical
    return normalized


class WardrobeValidationError(ValueError):
    """衣橱结构非法（重复/非正整数 id、类别不可用等），拒绝计算任何指标。"""


def validate_wardrobe(wardrobe):
    """在计算前校验输入衣橱结构，失败抛 WardrobeValidationError。

    协议 2 节要求 id 为正整数且全局唯一、category 为非空字符串（用于分组/比较）。
    空衣橱合法（empty_wardrobe=true）。任何非法输入都必须在产出指标前失败；
    LLM 结果中的外部/非法 ID 属于"结果无效计入回退指标"，不在此校验范围。
    """
    seen = set()
    for index, item in enumerate(wardrobe):
        if not isinstance(item, dict):
            raise WardrobeValidationError("wardrobe item %d is not a JSON object" % index)
        item_id = item.get("id")
        if isinstance(item_id, bool) or not isinstance(item_id, int) or item_id <= 0:
            raise WardrobeValidationError(
                "wardrobe item %d has invalid id %r (must be a positive integer)"
                % (index, item_id))
        if item_id in seen:
            raise WardrobeValidationError("duplicate wardrobe id %r" % (item_id,))
        seen.add(item_id)
        category = item.get("category")
        if not isinstance(category, str) or not category.strip():
            raise WardrobeValidationError(
                "wardrobe item id %r has invalid category %r (must be a non-empty string)"
                % (item_id, category))


def validate_llm_result(result, wardrobe_by_id):
    """校验单个 LLM 结果的输入边界，返回 (valid, reason)。

    与后端 RecommendationService.validateLlmResult 的约束一一对应：
    1. 字段与文本：summary 非空且 <=500 字符，reason 非空且 <=1200 字符；
       itemIds 必须为 2-4 个整数（布尔值不算）。
    2. 重复 ID：itemIds 出现重复 -> duplicate-item-ids。
    3. 衣橱归属：itemIds 含当前衣橱之外的 ID -> foreign-item-ids。
    4. 类别多样性：选中衣物归一到五大类别后不足 2 个 -> same-category。
    任一失败返回 (False, reason)；全部通过返回 (True, None)。
    """
    if result is None or not isinstance(result, dict):
        return False, "result-invalid"
    if not valid_text(result.get("summary"), MAX_SUMMARY_LENGTH):
        return False, "result-invalid"
    if not valid_text(result.get("reason"), MAX_REASON_LENGTH):
        return False, "result-invalid"
    item_ids = result.get("itemIds")
    if not isinstance(item_ids, list):
        return False, "result-invalid"
    if not all(isinstance(i, int) and not isinstance(i, bool) for i in item_ids):
        return False, "result-invalid"
    if not (MIN_ITEM_COUNT <= len(item_ids) <= MAX_ITEM_COUNT):
        return False, "result-invalid"
    if len(set(item_ids)) != len(item_ids):
        return False, "duplicate-item-ids"
    unknown = [iid for iid in item_ids if iid not in wardrobe_by_id]
    if unknown:
        return False, "foreign-item-ids"
    categories = {canonical_category(wardrobe_by_id[iid].get("category")) for iid in item_ids}
    if len(categories) < MIN_DISTINCT_CATEGORIES:
        return False, "same-category"
    return True, None


def select_items(wardrobe, temperature, ratings):
    """最小规则引擎，与后端 RecommendationService.selectItems 行为一致。

    低温（<18°C）类别优先顺序为 外套/上装/下装/鞋履/配饰，否则为上装/下装/鞋履/外套/配饰。
    按评分（稳定排序，缺省 3.0）排序后逐类别取第一款未选中的衣物，不足 4 件时用剩余衣物补齐。
    衣橱为空时返回空列表。
    """
    if not wardrobe:
        return []
    category_order = ("外套", "上装", "下装", "鞋履", "配饰") if temperature < 18 \
        else ("上装", "下装", "鞋履", "外套", "配饰")
    ranked = sorted(wardrobe, key=lambda item: -ratings.get(item["id"], 3.0))
    selected = {}
    for category in category_order:
        for item in ranked:
            if matches_category(item.get("category"), category) and item["id"] not in selected:
                selected[item["id"]] = item
                break
    if len(selected) < 4:
        for item in ranked:
            selected.setdefault(item["id"], item)
            if len(selected) == 4:
                break
    return list(selected.values())


def final_recommendation(result, wardrobe_by_id, rule_output):
    """合法的 LLM 结果按其 itemIds 解析，否则使用规则引擎兜底输出。

    判定与 validate_llm_result 共用同一输入边界，避免"校验通过但解析出重复/外部 ID"
    的不一致。规则引擎输出（可能为空）原样作为兜底。
    """
    valid, _ = validate_llm_result(result, wardrobe_by_id)
    if valid:
        return [wardrobe_by_id[iid] for iid in result["itemIds"]]
    return rule_output


def evaluate(wardrobe, llm_results, temperature, seed, baseline_trials=5, sample_size=None,
             dataset_meta=None):
    """对给定衣橱与 LLM 结果计算协议指标。

    输入衣橱结构非法时（重复/非正整数 id、category 不可用）立即抛
    WardrobeValidationError，不产出任何指标。固定 seed 实际控制：baseline_trials
    的子集抽样与可选的 llm_results 抽样。任何随机行为都来自 random.Random(seed)，
    更换 seed 会得到不同的抽样结果。dataset_meta 为输入数据集根部的 meta 对象
    （可为 None），其 kind 字段如实反映在报告 dataset 字段中；未提供时标为
    unknown，绝不臆造来源。
    """
    validate_wardrobe(wardrobe)
    rng = random.Random(seed)
    if sample_size is not None and sample_size < len(llm_results):
        llm_results = rng.sample(llm_results, sample_size)

    wardrobe_by_id = {item["id"]: item for item in wardrobe}
    ratings = {item["id"]: item.get("rating", 3.0) for item in wardrobe}
    rule_output = select_items(wardrobe, temperature, ratings)

    per_result = []
    fallback_reasons = {}
    for result in llm_results:
        ok, reason = validate_llm_result(result, wardrobe_by_id)
        per_result.append({"valid": ok, "reason": reason})
        if not ok:
            fallback_reasons[reason] = fallback_reasons.get(reason, 0) + 1

    final_recommendations = [
        final_recommendation(result, wardrobe_by_id, rule_output) for result in llm_results
    ]

    final_diversities = [
        len({canonical_category(item.get("category")) for item in items}) if items else 0
        for items in final_recommendations
    ]
    final_membership = [
        bool(items) and all(item["id"] in wardrobe_by_id for item in items)
        for items in final_recommendations
    ]
    final_uniqueness = [
        bool(items) and len({item["id"] for item in items}) == len(items)
        for items in final_recommendations
    ]

    baseline_trials_results = []
    if baseline_trials > 0 and len(wardrobe) >= 4:
        for _ in range(baseline_trials):
            k = rng.randint(4, len(wardrobe))
            subset = rng.sample(wardrobe, k)
            output = select_items(subset, temperature, ratings)
            diversity = len({canonical_category(item.get("category")) for item in output})
            membership = all(item["id"] in wardrobe_by_id for item in output)
            uniqueness = len({item["id"] for item in output}) == len(output)
            baseline_trials_results.append({
                "subset_ids": sorted(item["id"] for item in subset),
                "output_ids": [item["id"] for item in output],
                "output_size": len(output),
                "diversity": diversity,
                "membership": membership,
                "uniqueness": uniqueness,
                # 规则引擎自身契约：至少 2 件、至少 2 个不同类别、全部来自衣橱、无重复。
                # 2-4 件数量边界属于 LLM 输入边界，不属于规则引擎输出契约。
                "valid": len(output) >= 2 and diversity >= MIN_DISTINCT_CATEGORIES
                          and membership and uniqueness,
            })

    llm_count = len(llm_results)
    valid_count = sum(1 for r in per_result if r["valid"])
    metrics = {
        "llm_count": llm_count,
        "llm_valid_count": valid_count,
        "llm_valid_rate": (valid_count / llm_count) if llm_count else 0.0,
        "fallback_count": llm_count - valid_count,
        "fallback_rate": ((llm_count - valid_count) / llm_count) if llm_count else 0.0,
        "fallback_reasons": fallback_reasons,
        "final_category_diversity_min": min(final_diversities) if final_diversities else 0,
        "final_category_diversity_mean": (
            sum(final_diversities) / len(final_diversities)) if final_diversities else 0.0,
        "final_membership_rate": (
            sum(1 for m in final_membership if m) / len(final_membership)) if final_membership else 0.0,
        "final_id_uniqueness": (
            sum(1 for u in final_uniqueness if u) / len(final_uniqueness)) if final_uniqueness else 0.0,
        "rule_engine_output_size": len(rule_output),
        "rule_engine_diversity": len({canonical_category(item.get("category")) for item in rule_output}),
        "rule_engine_validity": (
            sum(1 for t in baseline_trials_results if t["valid"]) / len(baseline_trials_results))
            if baseline_trials_results else None,
        "baseline_trials": len(baseline_trials_results),
    }

    dataset = "unknown"
    if isinstance(dataset_meta, dict):
        kind = dataset_meta.get("kind")
        if isinstance(kind, str) and kind.strip():
            dataset = kind.strip()

    return {
        "protocol": PROTOCOL_VERSION,
        "dataset": dataset,
        "dataset_meta": dataset_meta,
        "seed": seed,
        "temperature": temperature,
        "empty_wardrobe": len(wardrobe) == 0,
        "no_recommendation": len(rule_output) == 0,
        "metrics": metrics,
        "per_result": per_result,
        "baseline_trials": baseline_trials_results,
    }


def load_fixture(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", default=str(DEFAULT_FIXTURE),
                        help="本地数据集 JSON（默认 scripts/evaluation/fixture_wardrobe.json）")
    parser.add_argument("--temperature", type=float, default=26.0,
                        help="规则引擎温度场景（摄氏度），默认 26.0")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED,
                        help="固定随机种子，默认 %d" % DEFAULT_SEED)
    parser.add_argument("--baseline-trials", type=int, default=5,
                        help="规则引擎基准抽样子集数，0 表示关闭抽样")
    parser.add_argument("--sample-size", type=int, default=None,
                        help="从 LLM 结果中按 seed 抽样评估的数量（默认全部）")
    parser.add_argument("--report", default=None, help="报告 JSON 输出路径（默认 stdout）")
    parser.add_argument("--pretty", action="store_true", help="报告以缩进格式输出")
    args = parser.parse_args(argv)

    fixture = load_fixture(args.fixture)
    wardrobe = fixture.get("wardrobe", [])
    llm_results = fixture.get("llm_results", [])
    try:
        report = evaluate(
            wardrobe,
            llm_results,
            args.temperature,
            seed=args.seed,
            baseline_trials=args.baseline_trials,
            sample_size=args.sample_size,
            dataset_meta=fixture.get("meta"),
        )
    except WardrobeValidationError as error:
        print("error: %s" % error, file=sys.stderr)
        return 1
    if args.report:
        with open(args.report, "w", encoding="utf-8") as handle:
            json.dump(report, handle, ensure_ascii=False, indent=2 if args.pretty else None)
    else:
        json.dump(report, sys.stdout, ensure_ascii=False, indent=2 if args.pretty else None)
        sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())