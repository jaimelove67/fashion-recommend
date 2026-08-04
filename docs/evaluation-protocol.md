# 推荐评估协议（离线、可复现）

本协议定义如何对"推荐结果输入边界与规则引擎"做可复现的离线评估。协议默认只读取本地文件并计算，**不联网、不访问百炼或任何模型 API**，输出只包含脚本真实计算所得结果或明确的未执行状态。

协议版本：`evaluation-protocol-v1`。实现：`scripts/evaluation/evaluate_recommendations.py`。

## 1. 运行命令

```powershell
# 评估 fixture 数据集（默认入口）
python scripts/evaluation/evaluate_recommendations.py

# 指定数据集与输出报告
python scripts/evaluation/evaluate_recommendations.py --fixture scripts/evaluation/fixture_wardrobe.json --report report.json --pretty

# 对抗性测试（标准库 unittest）
python -m unittest discover -s scripts/evaluation -p "test_*.py"
```

脚本只依赖 Python 3 标准库，不新增重型依赖。任何参数都只影响本地计算，不会发起网络请求。

## 2. 数据集格式

数据集为单个 JSON 文件，根对象必须包含：

```json
{
  "meta": { "kind": "fixture", "note": "..." },
  "wardrobe": [
    { "id": 1, "name": "暖白牛津纺衬衫", "category": "上装", "color": "暖白", "style": "极简", "rating": 4.5 }
  ],
  "llm_results": [
    { "summary": "...", "reason": "...", "itemIds": [2, 3, 4, 6], "modelName": "qwen-plus", "providerCallId": "...", "promptTokens": 100, "completionTokens": 50, "totalTokens": 150 }
  ]
}
```

- `meta`：可选，数据集元数据，原样带入报告 `dataset_meta` 字段；`meta.kind` 如实反映在报告 `dataset` 字段。未提供 `meta` 或 `meta.kind` 时报告标记为 `unknown`，绝不臆造来源。
- `wardrobe`：样本衣橱。`id` 必须为正整数且全局唯一，`category` 必须为非空字符串（用于分组/比较）；`rating` 可选，用于规则引擎排序，缺省 3.0。结构非法时在计算前 fail-fast（见 3.3）。
- `llm_results`：待评估的 LLM 结果样本。`summary` / `reason` / `itemIds` 为业务字段；`modelName` / `providerCallId` / 三类 `*Tokens` 为审计元数据（评估校验不依赖它们）。

仓库内 `scripts/evaluation/fixture_wardrobe.json` 是 **fixture/demo**，用于演示脚本可运行性与指标计算，**不作为实验结论**。任何真实实验必须使用独立数据集并自行记录来源与运行命令；真实数据集应在 `meta` 中声明来源与 `kind`（如 `experiment`），报告将如实呈现，不得把 fixture 结果当作实验结论。

## 3. 输入边界

### 3.1 LLM 结果输入边界（与后端 `RecommendationService.validateLlmResult` 一致）

对每个 LLM 结果 `r = (summary, reason, itemIds)` 与衣橱 `W`：

1. **字段与文本**：`summary` 非空且去除首尾空白后不超过 500 字符；`reason` 非空且不超过 1200 字符；`itemIds` 必须为 2-4 个整数（布尔值不算整数）。
2. **唯一性**：`itemIds` 不得重复。
3. **衣橱归属**：所有 `itemIds` 必须属于当前衣橱 `W`。
4. **类别多样性**：选中衣物按类别归一到五大类别（外套/上装/下装/鞋履/配饰，鞋履以"鞋"子串匹配，其余按子串匹配）后，不同类别数必须 ≥ 2。

任一条件失败即视为非法，记入对应 fallback 原因：

| 失败条件 | fallback 原因 |
| --- | --- |
| 字段缺失 / 文本非法 / 数量越界 / 非整数 ID | `result-invalid` |
| `itemIds` 重复 | `duplicate-item-ids` |
| 含衣橱外 ID | `foreign-item-ids` |
| 类别不足 2 个 | `same-category` |

### 3.2 规则引擎（baseline）输入边界

规则引擎不消费外部输入，只消费 `W` 与温度 `T`：

- 类别优先顺序：`T < 18` 时为 [外套, 上装, 下装, 鞋履, 配饰]，否则为 [上装, 下装, 鞋履, 外套, 配饰]。
- 按 `rating` 降序稳定排序（缺省 3.0），逐类别取第一款未选中衣物，未满 4 件时用剩余衣物补齐。
- 输出契约：至少 2 件、至少 2 个不同类别、全部来自 `W`、无重复。每类别可各取一件，因此最多 5 件；2-4 件数量边界属于 LLM 输入边界，不属于规则引擎输出契约。

### 3.3 衣橱结构校验（fail-fast）

字典化、抽样或任何指标计算前，先对输入衣橱 `W` 做结构校验，任一失败即抛 `WardrobeValidationError`（`ValueError` 子类），CLI 返回非零，**不产出任何指标**：

- `id` 必须为正整数（`0`、负数、字符串、浮点、`bool` 均拒绝，`bool` 是 `int` 子类须显式拒绝）；
- `id` 全局唯一，重复即拒绝；
- `category` 必须存在且为非空字符串（用于分组/比较；缺失、非字符串、空白字符串均拒绝）。

空衣橱合法（`empty_wardrobe=true`）。LLM 结果中的外部/非法 ID 属于"结果无效，计入回退指标"，不在此校验范围，不构成数据集结构错误。

## 4. 指标定义

设 `R` 为 LLM 结果集合，`W` 为衣橱，`rule(W, T)` 为规则引擎输出，`valid(r)` 按 3.1 判定。

| 指标 | 定义 |
| --- | --- |
| `llm_count` | `|R|` |
| `llm_valid_count` | `|{r ∈ R : valid(r)}|` |
| `llm_valid_rate` | `llm_valid_count / |R|`（`R` 为空时为 0.0） |
| `fallback_count` | `|R| - llm_valid_count` |
| `fallback_rate` | `fallback_count / |R|` |
| `fallback_reasons` | 各 fallback 原因的出现次数分布 |
| `final_category_diversity_min` | 对每个最终推荐（合法 LLM 结果解析或 `rule(W, T)` 兜底）计算类别数，取最小值；空推荐记 0 |
| `final_category_diversity_mean` | 上述类别数的平均值 |
| `final_membership_rate` | 最终推荐中所有 ID 均属于 `W` 的比例；空推荐视为不满足 |
| `final_id_uniqueness` | 最终推荐无重复 ID 的比例；空推荐视为不满足 |
| `rule_engine_output_size` | `|rule(W, T)|` |
| `rule_engine_diversity` | `rule(W, T)` 的类别数 |
| `rule_engine_validity` | 按 seed 抽样若干衣橱子集后，`rule` 输出满足 3.2 输出契约的比例；抽样次数为 `baseline_trials` |

## 5. 固定随机种子

种子为常量 `SEED = 20260804`（可由 `--seed` 覆盖）。`random.Random(seed)` 实际控制：

- `baseline_trials` 个衣橱子集的抽样（每个子集先随机取大小 `k ∈ [4, |W|]`，再随机取 `k` 件衣物）；
- `--sample-size` 为 LLM 结果抽样时选出参与评估的结果。

相同 seed 必得完全相同的抽取与报告；不同 seed 会得到不同抽样。测试 `test_same_seed_is_reproducible` / `test_different_seed_changes_sampling` 验证这一性质。

## 6. 输出格式

默认输出 JSON 到 stdout，`--report` 指定文件。报告包含 `protocol`、`dataset`、`dataset_meta`、`seed`、`temperature`、`empty_wardrobe`、`no_recommendation`、`metrics`、`per_result` 与 `baseline_trials`。`dataset` 与 `dataset_meta` 如实反映输入数据集元数据（`dataset` 取 `meta.kind`，缺省 `unknown`），绝不臆造来源。`empty_wardrobe=true` 表示衣橱为空，此时规则引擎输出为空，`no_recommendation=true`，对应后端 422（`请先添加并完善至少两件衣物`）的离线复现。

## 7. 对抗性测试用例

`test_evaluate_recommendations.py` 用标准库 `unittest` 覆盖以下反例（均已在当前契约下验证）：

| 用例 | 输入 | 预期 |
| --- | --- | --- |
| 缺 requirement 字段 | 缺 `summary` / `reason` / `itemIds` | `result-invalid` |
| 空白文本 | `summary="   "` | `result-invalid` |
| 重复衣物 ID | `itemIds=[1,1,3,4]` | `duplicate-item-ids` |
| 衣橱外 ID | `itemIds=[1,3,999]` | `foreign-item-ids` |
| 同类别组合 | `itemIds=[1,2]`（均为上装） | `same-category` |
| 数量越界 | 1 件 / 5 件 / 字符串 ID | `result-invalid` |
| 长度越界 | `summary` 超 500 / `reason` 超 1200 | `result-invalid` |
| 空衣橱 | 衣橱为空 | `empty_wardrobe=true`、`no_recommendation=true`、规则输出为空 |
| 空 LLM 结果 | `llm_results=[]` | `llm_count=0`、`valid_rate=0.0`、`rule_engine_validity=None` |
| seed 可复现 | 相同 seed 两次运行 | `baseline_trials` 与 `metrics` 完全一致 |
| seed 控制抽样 | 不同 seed | `baseline_trials` 子集不同 |
| 规则引擎有效性 | fixture 衣橱 | 输出满足 3.2 契约；低温场景优先外套 |
| 数据集来源如实 | 未传 `meta` / `meta` 无 `kind` | `dataset="unknown"`、`dataset_meta=None`，不臆造来源 |
| 数据集来源如实 | `meta.kind="fixture"` | `dataset="fixture"` |
| 数据集来源如实 | `meta.kind="experiment"` | `dataset="experiment"`，`dataset_meta` 原样呈现 |
| 衣橱结构非法 | 重复 `id` / `id=0` / 负数 / 字符串 / 浮点 / `bool` / `category` 缺失 / 非字符串 / 空白 | 抛 `WardrobeValidationError`，不产出任何指标 |
| 衣橱结构合法 | 符号合法的衣橱 | 正常计算 |
| LLM 外部 ID 非结构错误 | `itemIds` 含衣橱外 ID | 不抛结构错误，计入 `foreign-item-ids` 回退指标 |

## 8. 与真实实验的区分

- 脚本只会输出真实计算出的指标，或 `empty_wardrobe` / `no_recommendation` 等明确状态。
- 真实实验必须：提供独立数据集与来源记录、运行命令与 seed、输出报告；不得把 fixture 结果当作实验结论。
- 若某项真实评估未运行，文档必须写"未执行/无结论"，不得填模板数字。