"""评估协议对抗性测试（标准库 unittest，完全离线）。

覆盖与当前推荐契约适配的反例：缺字段、重复衣物 ID、衣橱外 ID、同类别组合、
空数据，以及固定 seed 对抽样行为的实际控制。所有测试只读取本地文件/内存数据，
不联网、不调用百炼或任何模型 API。
"""

import json
import unittest
from pathlib import Path

from evaluate_recommendations import (
    DEFAULT_SEED,
    MAX_REASON_LENGTH,
    MAX_SUMMARY_LENGTH,
    WardrobeValidationError,
    canonical_category,
    evaluate,
    final_recommendation,
    select_items,
    validate_llm_result,
)

FIXTURE = Path(__file__).with_name("fixture_wardrobe.json")

# 与 fixture 一致的衣橱（8 件、跨 5 个类别）
WARDROBE = [
    {"id": 1, "name": "暖白牛津纺衬衫", "category": "上装", "rating": 4.5},
    {"id": 2, "name": "雾蓝轻薄针织衫", "category": "上装", "rating": 4.2},
    {"id": 3, "name": "石墨灰直筒西裤", "category": "下装", "rating": 4.8},
    {"id": 4, "name": "黑色方头乐福鞋", "category": "鞋履", "rating": 4.6},
    {"id": 5, "name": "米白宽腿休闲裤", "category": "下装", "rating": 3.9},
    {"id": 6, "name": "藏青薄款风衣", "category": "外套", "rating": 4.4},
    {"id": 7, "name": "黑色极简邮差包", "category": "配饰", "rating": 3.8},
    {"id": 8, "name": "浅灰羊绒围巾", "category": "配饰", "rating": 4.0},
]
WARDROBE_BY_ID = {item["id"]: item for item in WARDROBE}


def valid_result(item_ids):
    return {
        "summary": "通勤推荐",
        "reason": "适合当前天气与场景",
        "itemIds": item_ids,
    }


class ValidationBoundaryTest(unittest.TestCase):
    """LLM 结果输入边界：缺字段 / 重复 ID / 衣橱外 ID / 同类别 / 数量与长度。"""

    def test_missing_summary_field(self):
        result = {"reason": "仅理由", "itemIds": [1, 3, 4]}
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "result-invalid")

    def test_missing_reason_field(self):
        result = {"summary": "仅摘要", "itemIds": [1, 3, 4]}
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "result-invalid")

    def test_missing_item_ids_field(self):
        result = {"summary": "有摘要", "reason": "有理由"}
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "result-invalid")

    def test_blank_summary_is_invalid(self):
        result = {"summary": "   ", "reason": "理由", "itemIds": [1, 3, 4]}
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "result-invalid")

    def test_duplicate_item_ids(self):
        result = valid_result([1, 1, 3, 4])
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "duplicate-item-ids")

    def test_foreign_item_ids(self):
        result = valid_result([1, 3, 999])
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "foreign-item-ids")

    def test_same_category(self):
        result = valid_result([1, 2])
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "same-category")

    def test_too_few_items(self):
        result = valid_result([1])
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "result-invalid")

    def test_too_many_items(self):
        result = valid_result([1, 2, 3, 4, 5])
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "result-invalid")

    def test_string_item_ids_rejected(self):
        result = {"summary": "x", "reason": "y", "itemIds": ["1", 3, 4]}
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "result-invalid")

    def test_oversized_summary_rejected(self):
        result = {"summary": "长" * (MAX_SUMMARY_LENGTH + 1), "reason": "y", "itemIds": [1, 3, 4]}
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "result-invalid")

    def test_oversized_reason_rejected(self):
        result = {"summary": "x", "reason": "长" * (MAX_REASON_LENGTH + 1), "itemIds": [1, 3, 4]}
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertFalse(valid)
        self.assertEqual(reason, "result-invalid")

    def test_valid_result_accepted(self):
        result = valid_result([1, 3, 4])
        valid, reason = validate_llm_result(result, WARDROBE_BY_ID)
        self.assertTrue(valid)
        self.assertIsNone(reason)


class RuleEngineTest(unittest.TestCase):
    """规则引擎输出边界：类别多样性、数量、空数据与温度分支。"""

    def test_rule_engine_valid_on_fixture(self):
        output = select_items(WARDROBE, 26.0, {item["id"]: item.get("rating", 3.0) for item in WARDROBE})
        # 规则引擎按五大类别各取一款，最多 5 件；仅在 LLM 兜底时用作最终推荐。
        self.assertTrue(2 <= len(output) <= 5)
        categories = {canonical_category(item["category"]) for item in output}
        self.assertGreaterEqual(len(categories), 2)
        self.assertTrue(all(item["id"] in WARDROBE_BY_ID for item in output))

    def test_rule_engine_cold_prefers_outerwear(self):
        ratings = {item["id"]: item.get("rating", 3.0) for item in WARDROBE}
        warm = select_items(WARDROBE, 26.0, ratings)
        cold = select_items(WARDROBE, 10.0, ratings)
        self.assertEqual(warm[0]["category"], "上装")
        self.assertEqual(cold[0]["category"], "外套")

    def test_rule_engine_empty_wardrobe(self):
        output = select_items([], 26.0, {})
        self.assertEqual(output, [])

    def test_rule_engine_single_category_wardrobe(self):
        single = [{"id": 1, "category": "上装"}, {"id": 2, "category": "上装"}]
        output = select_items(single, 26.0, {1: 4.0, 2: 3.0})
        self.assertEqual(len(output), 2)


class EvaluationReportTest(unittest.TestCase):
    """评估报告：指标只包含真实计算结果，空数据状态明确，seed 实际控制抽样。"""

    @classmethod
    def setUpClass(cls):
        with open(FIXTURE, encoding="utf-8") as handle:
            cls.fixture = json.load(handle)

    def test_fixture_shapes(self):
        self.assertIn("meta", self.fixture)
        self.assertEqual(self.fixture["meta"]["kind"], "fixture")
        self.assertGreaterEqual(len(self.fixture["wardrobe"]), 4)
        self.assertGreaterEqual(len(self.fixture["llm_results"]), 1)

    def test_report_metrics_are_computed(self):
        report = evaluate(
            self.fixture["wardrobe"], self.fixture["llm_results"], 26.0, seed=DEFAULT_SEED)
        metrics = report["metrics"]
        self.assertEqual(metrics["llm_count"], len(self.fixture["llm_results"]))
        self.assertEqual(
            metrics["llm_valid_count"] + metrics["fallback_count"], metrics["llm_count"])
        self.assertAlmostEqual(
            metrics["llm_valid_rate"] + metrics["fallback_rate"], 1.0)
        self.assertAlmostEqual(metrics["final_membership_rate"], 1.0)
        self.assertAlmostEqual(metrics["final_id_uniqueness"], 1.0)
        self.assertGreaterEqual(metrics["final_category_diversity_min"], 2)
        self.assertGreater(metrics["rule_engine_output_size"], 0)

    def test_same_seed_is_reproducible(self):
        first = evaluate(self.fixture["wardrobe"], self.fixture["llm_results"], 26.0, seed=DEFAULT_SEED)
        second = evaluate(self.fixture["wardrobe"], self.fixture["llm_results"], 26.0, seed=DEFAULT_SEED)
        self.assertEqual(first["baseline_trials"], second["baseline_trials"])
        self.assertEqual(first["metrics"], second["metrics"])

    def test_different_seed_changes_sampling(self):
        first = evaluate(self.fixture["wardrobe"], self.fixture["llm_results"], 26.0, seed=DEFAULT_SEED)
        second = evaluate(self.fixture["wardrobe"], self.fixture["llm_results"], 26.0, seed=1)
        self.assertNotEqual(first["baseline_trials"], second["baseline_trials"])

    def test_sample_size_uses_seed(self):
        results = self.fixture["llm_results"]
        full = evaluate(self.fixture["wardrobe"], results, 26.0, seed=DEFAULT_SEED, sample_size=None)
        sampled = evaluate(self.fixture["wardrobe"], results, 26.0, seed=DEFAULT_SEED, sample_size=3)
        self.assertEqual(sampled["metrics"]["llm_count"], 3)
        self.assertNotEqual(sampled["metrics"]["llm_count"], full["metrics"]["llm_count"])
        sampled_again = evaluate(
            self.fixture["wardrobe"], results, 26.0, seed=DEFAULT_SEED, sample_size=3)
        self.assertEqual(sampled["metrics"]["fallback_reasons"], sampled_again["metrics"]["fallback_reasons"])

    def test_empty_wardrobe_state(self):
        report = evaluate([], [valid_result([1, 3, 4])], 26.0, seed=DEFAULT_SEED, baseline_trials=0)
        self.assertTrue(report["empty_wardrobe"])
        self.assertTrue(report["no_recommendation"])
        self.assertEqual(report["metrics"]["rule_engine_output_size"], 0)
        self.assertEqual(report["metrics"]["final_category_diversity_min"], 0)
        self.assertEqual(report["metrics"]["final_membership_rate"], 0.0)

    def test_empty_llm_results_state(self):
        report = evaluate(self.fixture["wardrobe"], [], 26.0, seed=DEFAULT_SEED, baseline_trials=0)
        self.assertEqual(report["metrics"]["llm_count"], 0)
        self.assertEqual(report["metrics"]["llm_valid_rate"], 0.0)
        self.assertEqual(report["metrics"]["fallback_rate"], 0.0)
        self.assertIsNone(report["metrics"]["rule_engine_validity"])

    def test_final_recommendation_falls_back_for_invalid(self):
        rule_output = select_items(self.fixture["wardrobe"], 26.0, {})
        result = {"summary": "x", "reason": "y", "itemIds": [999]}
        final = final_recommendation(result, WARDROBE_BY_ID, rule_output)
        self.assertIs(final, rule_output)
        valid = final_recommendation(valid_result([1, 3, 4]), WARDROBE_BY_ID, rule_output)
        self.assertEqual([item["id"] for item in valid], [1, 3, 4])

    def test_fixture_file_reports_fixture_dataset(self):
        # 仓库默认 fixture 文件自身的 meta.kind=fixture，端到端标签必须仍是 fixture。
        report = evaluate(
            self.fixture["wardrobe"], self.fixture["llm_results"], 26.0, seed=DEFAULT_SEED,
            dataset_meta=self.fixture.get("meta"))
        self.assertEqual(report["dataset"], "fixture")


class DatasetMetadataTest(unittest.TestCase):
    """报告 dataset 字段如实反映输入元数据，绝不臆造来源或结论。"""

    def test_default_fixture_reports_fixture(self):
        report = evaluate(
            WARDROBE, [valid_result([1, 3, 4])], 26.0, seed=DEFAULT_SEED,
            dataset_meta={"kind": "fixture", "note": "demo"})
        self.assertEqual(report["dataset"], "fixture")
        self.assertEqual(report["dataset_meta"]["kind"], "fixture")

    def test_custom_meta_reported_verbatim(self):
        meta = {"kind": "experiment", "source": "user-collected", "note": "真实数据"}
        report = evaluate(
            WARDROBE, [valid_result([1, 3, 4])], 26.0, seed=DEFAULT_SEED, dataset_meta=meta)
        self.assertEqual(report["dataset"], "experiment")
        self.assertIs(report["dataset_meta"], meta)

    def test_missing_meta_not_mislabeled_as_fixture(self):
        report = evaluate(WARDROBE, [valid_result([1, 3, 4])], 26.0, seed=DEFAULT_SEED)
        self.assertEqual(report["dataset"], "unknown")
        self.assertIsNone(report["dataset_meta"])

    def test_meta_without_kind_not_mislabeled(self):
        report = evaluate(
            WARDROBE, [valid_result([1, 3, 4])], 26.0, seed=DEFAULT_SEED,
            dataset_meta={"note": "no kind field"})
        self.assertEqual(report["dataset"], "unknown")

    def test_blank_kind_not_mislabeled(self):
        report = evaluate(
            WARDROBE, [valid_result([1, 3, 4])], 26.0, seed=DEFAULT_SEED,
            dataset_meta={"kind": "   "})
        self.assertEqual(report["dataset"], "unknown")

    def test_non_string_kind_not_mislabeled(self):
        report = evaluate(
            WARDROBE, [valid_result([1, 3, 4])], 26.0, seed=DEFAULT_SEED,
            dataset_meta={"kind": 42})
        self.assertEqual(report["dataset"], "unknown")


class WardrobeValidationTest(unittest.TestCase):
    """衣橱结构 fail-fast：非法输入必须在产出任何指标前抛 WardrobeValidationError。"""

    VALID = [{"id": 1, "category": "上装"}, {"id": 2, "category": "下装"}]

    def test_invalid_wardrobe_rejects_before_metrics(self):
        cases = [
            [{"id": 1, "category": "上装"}, {"id": 1, "category": "下装"}],
            [{"id": 0, "category": "上装"}, {"id": 2, "category": "下装"}],
            [{"id": -1, "category": "上装"}, {"id": 2, "category": "下装"}],
            [{"id": "1", "category": "上装"}, {"id": 2, "category": "下装"}],
            [{"id": 1.0, "category": "上装"}, {"id": 2, "category": "下装"}],
            [{"id": True, "category": "上装"}, {"id": 2, "category": "下装"}],
            [{"id": 1}, {"id": 2, "category": "下装"}],
            [{"id": 1, "category": 42}, {"id": 2, "category": "下装"}],
            [{"id": 1, "category": "   "}, {"id": 2, "category": "下装"}],
        ]
        for wardrobe in cases:
            with self.subTest(wardrobe=wardrobe):
                with self.assertRaises(WardrobeValidationError):
                    evaluate(wardrobe, [valid_result([1, 2])], 26.0, seed=DEFAULT_SEED)

    def test_valid_wardrobe_accepted(self):
        report = evaluate(
            self.VALID, [valid_result([1, 2])], 26.0, seed=DEFAULT_SEED, baseline_trials=0)
        self.assertEqual(report["metrics"]["llm_count"], 1)

    def test_bool_int_collision_rejected(self):
        # True 与 1 相等且哈希相同，字典键会互相覆盖；两种顺序都必须显式拒绝 bool。
        cases = [
            [{"id": 1, "category": "上装"}, {"id": True, "category": "下装"}],
            [{"id": True, "category": "上装"}, {"id": 1, "category": "下装"}],
        ]
        for wardrobe in cases:
            with self.subTest(wardrobe=wardrobe):
                with self.assertRaises(WardrobeValidationError):
                    evaluate(wardrobe, [valid_result([1, 2])], 26.0, seed=DEFAULT_SEED)

    def test_duplicate_id_at_end_rejected(self):
        # 重复 id 出现在列表末尾时，字典推导会静默覆盖前值；必须在建字典前 fail-fast。
        wardrobe = [
            {"id": 1, "category": "上装"},
            {"id": 2, "category": "下装"},
            {"id": 1, "category": "配饰"},
        ]
        with self.assertRaises(WardrobeValidationError):
            evaluate(wardrobe, [valid_result([1, 2])], 26.0, seed=DEFAULT_SEED)

    def test_empty_wardrobe_is_valid_state(self):
        report = evaluate([], [], 26.0, seed=DEFAULT_SEED, baseline_trials=0)
        self.assertTrue(report["empty_wardrobe"])

    def test_foreign_llm_id_is_not_wardrobe_error(self):
        # LLM 结果里的外部 ID 属于"结果无效记入回退指标"，不是衣橱结构错误。
        report = evaluate(
            self.VALID, [valid_result([1, 999])], 26.0, seed=DEFAULT_SEED, baseline_trials=0)
        self.assertEqual(report["metrics"]["llm_valid_count"], 0)
        self.assertEqual(report["metrics"]["fallback_count"], 1)
        self.assertEqual(report["metrics"]["fallback_reasons"]["foreign-item-ids"], 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)