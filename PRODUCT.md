# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Existing Vue 3 + Vite frontend with a Spring Boot/PostgreSQL backend. This request extends the existing application rather than creating a new stack.

## Users

登录后的个人用户，在每日推荐页面根据天气、场合和自己的衣橱决定当天穿什么。

## Product Purpose

知己把用户已经拥有的衣物、天气、场合和个人风格偏好组织成可解释的每日穿搭建议。成功意味着用户能看懂推荐依据，并确认推荐中的每件单品确实来自自己的衣橱。

## Positioning

推荐以用户私有衣橱为边界：模型或规则只能从当前用户可用的衣物中选择，不能用通用商品或外部参考图伪造拥有关系。

## Operating Context

用户先登录并维护个人资料与衣橱，再进入“推荐”页查看当天搭配、天气和推荐说明；衣物图片由前端通过现有同源接口展示，推荐结果可保存、评分并进入历史。

## Capabilities and Constraints

- 每日推荐信息区需要固定解释四个方面：天气适配、场合适配、风格方向、衣橱依据。
- 每日推荐主视觉按用户给出的参考效果组织为中央全身模特和四角单品卡，截图中的英文标签和按钮只作为视觉参考，不作为业务文案或数据来源。
- 每日推荐展示的单品必须来自当前用户衣橱中的可用衣物；待人工确认或不在衣橱的数据不能进入可穿搭配。
- 模特性别以用户个人资料为准；个人资料需要能够保存和返回性别字段。
- 每日模特图的原型资产固定参考 `docx/reference_photo/exec-0558d0a4-c680-497c-a315-ddecd63ad798.png` 与 `docx/reference_photo/exec-c2af0af1-828a-46c8-98fc-e8294ddfb2dc.png`。
- 现有文本推荐接口接受场合、城市和风格提示；本轮不改变其已有推荐安全校验。
- 每日人物生图通过后端阿里云万相接口按推荐记录触发；两张用户指定模特原型图只作为后端生图输入，当前衣橱图片和单品元数据作为参考输入。生图失败或未配置时保留画板与已确认衣橱单品并显式标注未生成状态，不把原型或静态过渡资产冒充运行时结果。

## Brand Commitments

品牌名称为“知己 / WEAVESELF”。现有推荐页使用暖纸张背景、编辑型标题、墨黑文字、橄榄绿和砖红点缀；本轮保持其产品语气与已有业务外壳。

## Evidence on Hand

- 用户提供的每日推荐视觉参考截图：`C:/Users/jaime/Pictures/Screenshots/屏幕截图 2026-09-16 093017.png`。
- 用户指定的男、女模特原型图：`docx/reference_photo/exec-0558d0a4-c680-497c-a315-ddecd63ad798.png` 与 `docx/reference_photo/exec-c2af0af1-828a-46c8-98fc-e8294ddfb2dc.png`。
- 现有演示衣橱单品资源位于 `frontend/public/assets/wardrobe/`，推荐数据来自用户衣橱与历史接口。
- 已接入的每日生图调用链：`RecommendationController` → `RecommendationVisualService` → `BailianImageGenerationClient` → 万相异步任务创建/轮询；测试覆盖请求体中的模型、原型 Base64、受限提示词、任务轮询和关闭开关。

## Product Principles

- 先证明拥有关系，再表达风格。
- 推荐理由要能追溯到天气、场合、风格和衣橱事实。
- 外部参考只影响灵感，不改变用户衣橱边界。
- 生成能力不可用时清楚表达状态，不制造成功假象。

## Accessibility & Inclusion

模特选择遵从用户已保存的性别偏好；未填写时不擅自推断，展示待补充状态并允许继续查看单品与文字推荐。每日推荐的状态、说明和单品信息应通过语义 HTML、可见焦点和移动端布局可访问。
