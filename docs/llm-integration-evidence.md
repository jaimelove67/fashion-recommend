# 百炼大模型接入与调用证据

本文说明穿搭推荐如何调用阿里云百炼、如何约束 prompt 和模型输出，并保留一次可通过 provider call ID 核验的真实调用记录。

## 推荐调用流程

~~~mermaid
flowchart TD
    A["用户提交场合、城市和风格要求"] --> B["后端读取天气、风格档案和当前用户衣橱"]
    B --> C["构造 LlmRecommendationContext"]
    C --> D{"已配置 DASHSCOPE_API_KEY？"}
    D -- "否" --> R["规则引擎选择 2-4 件可组合衣物"]
    D -- "是" --> E["发送 system prompt + 结构化 user JSON"]
    E --> F["百炼 qwen-plus 返回 json_object"]
    F --> G{"字段、长度、数量和 ID 校验通过？"}
    G -- "否" --> R
    G -- "是" --> H["保存推荐，engine=llm"]
    R --> I["保存推荐，engine=development-rule-v1"]
    H --> J["前端展示单品、理由、天气和生成来源"]
    I --> J
~~~

流程的关键不是“调用成功就直接展示”，而是模型结果必须先通过服务端约束。外部服务不可用或内容不可信时，系统保留业务可用性，同时通过 engine 明确标记来源。

## Prompt 约束

推荐 prompt 使用四层约束：

1. 指令与数据分离：system prompt 明确声明场合、风格提示、天气、风格档案和衣橱条目都是数据，不是可执行指令，降低用户输入覆盖系统规则的风险。
2. 候选范围限制：模型只能从 wardrobe 中选择衣物，不得编造或修改 ID；数量必须是 2-4 件。
3. 输出结构限制：请求 response_format=json_object，结果必须且只能包含 summary、reason、itemIds，不接受 Markdown、代码围栏或附加文字。
4. 服务端二次校验：解析时检查精确字段集合、文本非空与长度、ID 类型、数量、去重，以及所有 ID 是否属于当前用户衣橱。任一条件失败都会进入规则 fallback。

核心输出契约如下：

~~~json
{
  "summary": "不超过 500 字的推荐摘要",
  "reason": "不超过 1200 字的推荐理由",
  "itemIds": [22, 23, 24]
}
~~~

视觉识别使用独立 prompt，只允许 name、category、color、style 四个字符串字段，category 只能是上装、下装、鞋履、外套或配饰。视觉能力默认关闭，详见开发期边界文档。

## 真实调用记录

本次调用从运行中的应用读取 demo-user 的 8 件衣物和风格档案，再使用与后端一致的 OpenAI 兼容请求契约调用百炼。宿主机通过本地代理访问 provider，密钥只存在于未提交的 .env 和请求内存中。

| 字段 | 记录 |
| --- | --- |
| 调用时间 | 2026-07-14 16:32:24 至 16:32:28，Asia/Shanghai |
| Provider | Alibaba Cloud Bailian |
| 模型 | qwen-plus |
| Call ID | chatcmpl-66fad4c4-9e16-9dbf-b200-e4a4af795e67 |
| Finish reason | stop |
| Token 使用 | prompt 617，completion 226，total 843 |
| 场景 | 毕业答辩现场 |
| 衣橱范围 | demo-user，共 8 件 |
| 返回单品数 | 4 |

返回的 itemIds 为 22、23、24、28，分别解析为暖白牛津纺衬衫、雾蓝轻薄针织衫、石墨灰直筒西裤和黑色方头乐福鞋。四个 ID 互不重复，均来自当前 demo-user 衣橱。

完整脱敏记录见 [bailian-recommendation-2026-07-14.json](llm-evidence/bailian-recommendation-2026-07-14.json)。该文件保留调用 ID、模型、usage、输入摘要、结果和校验结论，不包含 API Key、Authorization 头、完整环境变量或图片内容。

## 可核验代码

- 请求和 prompt 构造：backend/src/main/java/com/fashion/recommendation/recommendation/BailianRecommendationClient.java
- 业务校验和 fallback：backend/src/main/java/com/fashion/recommendation/recommendation/RecommendationService.java
- 视觉识别约束：backend/src/main/java/com/fashion/recommendation/recognition/BailianGarmentRecognitionService.java
- JSON 对抗测试：backend/src/test/java/com/fashion/recommendation/recommendation/BailianRecommendationClientTest.java
- 跨用户和非法 ID 测试：backend/src/test/java/com/fashion/recommendation/RecommendationControllerTest.java
