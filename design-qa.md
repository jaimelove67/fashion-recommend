# Design QA

## Comparison Target

- Source visual truth:
  - `docx/reference_photo/屏幕截图 2026-07-10 210843.png` for the editorial home composition.
  - `docx/reference_photo/trend_page.png` for the trend hierarchy.
  - `docx/reference_photo/wardrobe_page.png` for wardrobe navigation, metrics, filters, and item layout.
  - `docx/reference_photo/recommendation_page.png` for recommendation metrics, generation controls, and result hierarchy.
  - `docx/reference_photo/personal_page.png` for profile scoring, profile dimensions, and color guidance.
- Rendered implementation:
  - `C:/Users/jaime/.codex/visualizations/2026/07/13/019f5a30-2a82-79a2-a32a-a63dab8ac169/final-home-desktop.png`
  - `C:/Users/jaime/.codex/visualizations/2026/07/13/019f5a30-2a82-79a2-a32a-a63dab8ac169/final-trend-desktop.png`
  - `C:/Users/jaime/.codex/visualizations/2026/07/13/019f5a30-2a82-79a2-a32a-a63dab8ac169/final-wardrobe-desktop.png`
  - `C:/Users/jaime/.codex/visualizations/2026/07/13/019f5a30-2a82-79a2-a32a-a63dab8ac169/final-recommendation-desktop.png`
  - `C:/Users/jaime/.codex/visualizations/2026/07/13/019f5a30-2a82-79a2-a32a-a63dab8ac169/final-profile-desktop.png`
- Viewports: 1440x900 desktop, 390x844 mobile, and 1122x1402 native reference comparison.
- State: live `demo-user` data. Wardrobe and recommendation history are empty; profile data exists; trend data is explicitly marked as a development sample.

The populated reference screens and the live empty-data implementation are not the same data state. Layout, tokens, hierarchy, controls, and empty-state behavior were compared directly; populated item imagery and sample counts were not treated as an exact-content target.

## Evidence

- Full-view comparisons:
  - `compare-home.png`
  - `compare-trend.png`
  - `compare-wardrobe.png`
  - `compare-recommendation.png`
  - `compare-profile.png`
- Focused comparison: `compare-focused-details.png`
- Mobile evidence:
  - `final-home-mobile.png`
  - `final-wardrobe-mobile.png`
  - `final-profile-mobile.png`
- Evidence directory: `C:/Users/jaime/.codex/visualizations/2026/07/13/019f5a30-2a82-79a2-a32a-a63dab8ac169`

## Fidelity Review

- Fonts and typography: Chinese display copy uses Songti/Georgia-style serif fallbacks; navigation, labels, forms, and data use a deliberate system sans stack. Desktop and mobile headings wrap without clipping, negative tracking, or browser-default control sizes.
- Spacing and layout rhythm: the horizontal product shell, wide content gutters, restrained card radii, metric rails, editorial home collage, wardrobe side rail, recommendation form, and three-column profile analysis preserve the reference hierarchy. Mobile tracks collapse without root overflow.
- Colors and visual tokens: warm white, ink black, fine gray borders, restrained tan-gold, muted green focus treatment, and coral notification state map consistently across all views. No unrelated gradients or decorative blobs were introduced.
- Image quality and asset fidelity: all visible photography uses real repository assets or backend item URLs with stable object-fit framing and explicit broken-image fallbacks. The available subjects differ from the neutral reference photography; this is an intentional asset constraint, not a CSS or placeholder substitution.
- Copy and content: reference-only example counts such as 128 items, 92 percent, and 12.8w posts were not copied. Statistics are derived from live wardrobe/history/profile/trend data. Profile scoring is explicitly named `档案完整度`, not an AI style score.
- Icons and controls: Lucide Vue icons are used consistently. Navigation, search, notifications, trend filters, wardrobe filters, grid/list controls, add/edit modals, weather preview, generation, save, rating, history filters, and profile editing all have semantic labels and working state changes.
- Responsiveness and accessibility: 390x844, 1122x1402, and 1440x900 checks show no root horizontal overflow. Focus indicators, dialog semantics, body scroll locking, modal cleanup on deactivation, reduced-motion handling, labels, and empty/loading/error states are present.

## Findings

No actionable P0, P1, or P2 findings remain.

### Accepted Product Constraints

- The live wardrobe and history are empty, while the references show populated demo data. The implementation keeps the real empty state instead of fabricating ownership, wear, rating, or weekly-plan data.
- The repository's three fashion photos do not match the reference's neutral studio subjects exactly. Image generation and public-asset search were unavailable in this environment, so existing project assets were retained and framed consistently.
- The profile reference includes height, skin tone, body type, and a personal model image that do not exist in the backend contract. The implementation uses persisted preferences, occasions, profile completeness, color guidance, wardrobe data, and feedback instead.

## Patches Made

- Replaced the left vertical shell with the shared horizontal WEAVESELF-style header and hash-aware navigation.
- Added home, trend, wardrobe, recommendation, history, and profile views with real derived statistics.
- Fixed saving an old history item so it cannot overwrite the current recommendation.
- Removed the duplicate frontend weather request during recommendation generation.
- Fixed 390px wardrobe root overflow by constraining grid tracks and keeping category scrolling inside its own region.
- Fixed profile title spacing, dialog background scrolling, and dialog cleanup when cached views deactivate.
- Fixed wardrobe dialog cleanup when cached views deactivate.
- Reduced and balanced the mobile home heading to prevent an orphaned final character.

## Above-The-Fold Copy Diff

The implementation intentionally uses product-specific copy instead of copying reference mock text. Navigation labels and requested page identities are preserved. No fake metrics, unsupported profile attributes, invented AI claims, or prompt-like explanatory UI text were added.

## Follow-up Polish

- P3: Replace the three existing fashion photos with a coordinated neutral studio set when approved source assets or image generation become available.
- P3: Add a real user/profile image only after the backend or product contract supplies one.

final result: passed

## 2026-09-11 个人形象页联合分析流程复核

- Source visual truth: `docx/reference_photo/personal_page.png` plus the selected “中心画像仪表盘” direction generated from that reference.
- Implementation evidence: live Codex in-app browser at `http://localhost:5173/#profile`, authenticated as the local `demo-user`; desktop viewport measured 1280px wide with document scroll width 1265px, so the profile page has no root horizontal overflow.
- Visual state: warm paper-like background, horizontal WEAVESELF header, left copy block, centered personal portrait with height/weight/photo fact cards, right AI analysis panel, three lower guidance modules, and a profile timeline. The reference’s AI score/BMI-style claims were intentionally replaced with editable qualitative analysis and explicit “not an appearance rating” copy.

### Interaction and state checks

- Height and weight are required before the second step; native 80–250 cm and 20–300 kg bounds plus a custom “请填写身高和体重后再继续” guard were verified.
- The example portrait is not treated as the user’s upload. Starting analysis without a user-selected image is blocked with “请先选择个人照片，再开始分析”。
- JPG/PNG/WEBP file selection, the combined-input summary, the “正在整理你的形象参考” state, the completed state, inline analysis editing, recommendation CTA, and the post-analysis notice were verified through the live accessibility tree.
- Dialog semantics are present (`role=dialog`, `aria-modal=true`); body scrolling is locked while open and restored after close.
- Browser runtime logs contained no warning or error entries during the final pass.

### Findings

No actionable P0, P1, or P2 findings remain.

### Accepted Product Constraint

- The current page implements the requested front-end flow with local demo persistence. The existing backend profile contract still does not expose height, weight, photo, or structured face-analysis fields; wiring these values to the real LLM endpoint remains a separate API/data-model task.

final result: passed

## 2026-09-10 推荐页一周穿搭工作台复核

- Source visual truth: `docx/reference_photo/recommendation_page.png` (1132 × 1389 px).
- Implementation evidence: live Codex in-app browser at `http://localhost:8090/?preview=20260910&fallback=1#recommend`, authenticated as `demo-user`; captured viewport was 653 × 694 CSS px. DOM metrics reported one weekly section, one selected-day detail, one generator, one generated-result region, and one signal grid; document scroll width stayed at 638 px, so there was no root horizontal overflow at this viewport.
- State: current week `9月7日 — 9月13日`, Thursday selected, generator expanded. A second pass generated local recommendation `#33` under the fallback rule engine, saved it, rated it 5 stars, and confirmed it on the history page.

### Intentional redesign boundary

- Preserved from the reference: seven-day browsing, day-level weather/context, outfit item grouping, match score, and the ability to move across the week.
- Redesigned per request: the header/hero, overview rail, selected-day detail, generator workbench, closet signal, trend signal, and usage notes. The new visual direction uses a warm paper surface, olive operational accents, brick action accents, editorial serif headlines, and tighter workbench grouping instead of copying the source page pixel-for-pixel.

### Mandatory comparison passes

- Typography: serif display treatment is reserved for the page promise and selected-day headline; metadata and controls use a compact sans system. The hero wraps cleanly at the captured width without clipped characters.
- Spacing and layout: the weekly tabs remain the primary browsing band; the selected-day detail is visually separated from the generator; the generator collapses to a compact summary and expands back to its labeled fields. No overlapping regions were observed in the live DOM metrics.
- Viewport resilience: at 653 px, the weekly cards use an intentional horizontal track while the page itself remains within the viewport. The responsive rules stack the detail/workbench regions below the weekly planner and preserve usable controls.
- Colors and tokens: warm paper background, dark ink, olive selected states, brick action states, and low-contrast borders are applied consistently; no unrelated gradients or decorative blobs were introduced.
- Image quality: visible outfit imagery uses existing repository assets and real wardrobe item URLs with fixed object-fit framing and alt text. The available project assets do not exactly reproduce the reference subjects; this remains an accepted asset constraint.
- Copy and content: the main title remains `从你的衣橱，生成今天的答案。`; dynamic weekly details use the authenticated demo wardrobe and weather data. Generated result #33 explicitly identified `规则引擎降级 / 模型调用已关闭`.
- Icons and controls: Lucide icons are used for weather, navigation, outfit states, save, rating, and section controls. Tabs expose semantic `role=tab`; form controls retain accessible labels.
- States and interactions: verified day selection, next-week navigation, return-to-current-week, generator collapse/expand, generation, save, 5-star rating, and history persistence. Browser console warnings/errors were empty after the final page load.
- Accessibility: labeled inputs, semantic buttons/tabs, disabled saved state, visible selection state, and no root horizontal overflow were verified in the live page.

### Findings

No actionable P0, P1, or P2 findings remain.

### Accepted Product Constraints

- The source reference is a populated marketing-style mockup while the implementation is driven by the authenticated demo wardrobe/history contract; content and counts intentionally come from live local data.
- The live browser capture is available in the Codex in-app browser session rather than as a persisted PNG artifact; the page was inspected visually and through its accessibility tree/DOM metrics.
- External model calls were disabled for this verification run through the existing E2E compose override; the real user-visible generation, save, rating, and persistence states were still exercised.

final result: passed

## 2026-09-08 自动轮播节奏最终复核

- 行为目标：风潮画廊每 1000ms 自动切换一次；鼠标悬浮任意图片时暂停，移出后恢复。
- 实现证据：`frontend/src/views/TrendView.vue` 的定时器为 `setInterval(..., 1000)`，并通过 `.gallery-card` 事件委托处理 `mouseover`/`mouseout`；键盘聚焦、页面隐藏和 reduced-motion 保护仍然有效。
- 交互证据：无悬停等待 1.3 秒后焦点发生变化；悬浮图片等待 1.3 秒前后图片标题一致；移出图片等待 1.3 秒后焦点再次变化。
- 工程证据：前端生产构建通过，Docker 前端容器健康启动，浏览器控制台 0 个错误；本轮仅改变轮播节奏和暂停边界，不改变已通过的桌面/移动视觉布局。

final result: passed

## 2026-09-08 Top 10 画廊视觉与自动轮播精修

- Source visual truth: `C:/Users/jaime/AppData/Local/Temp/codex-clipboard-abbc4c01-d752-4199-921f-d966608019a2.png` (2159 × 1200 px).
- Implementation evidence: desktop stage `C:/other/新建文件夹/毕设/基于大模型（LLM）的智能穿搭推荐/.playwright-cli/element-2026-09-08T09-01-44-749Z.png` (1241 × 404 px at 1440 × 900 CSS px, device scale factor 1); mobile stage `C:/other/新建文件夹/毕设/基于大模型（LLM）的智能穿搭推荐/.playwright-cli/element-2026-09-08T08-57-33-152Z.png` (343 × 381 px at 390 × 844 CSS px, device scale factor 1). Combined focused comparison: `C:/other/新建文件夹/毕设/基于大模型（LLM）的智能穿搭推荐/.playwright-cli/compare-gallery-final2.png`.
- State: authenticated `demo-user`, trend feed in explicit development-sample mode, gallery at its automatic-rotation state. The source stage was cropped from the reference's x=69..1834 and y=219..897 region before being normalized to the desktop evidence height; the implementation was captured as the component region.

### Fidelity surfaces

- Typography: “风潮穿搭精选” keeps the existing serif display treatment and the small uppercase `DAILY TOP 10` label; no large position counter remains.
- Spacing and layout: the dedicated pale stage fill, rounded panel, bottom caption, hint, arrows, and numeric tabs are removed. Desktop gaps measure about 30–46px; mobile gaps measure about 22–39px without root overflow.
- Colors and tokens: the stage is transparent (`rgba(0, 0, 0, 0)`), letting the page surface show through; card borders and shadows remain restrained so the image row remains dominant.
- Imagery: the same three repository fashion images remain the known development-asset constraint and are framed with `object-fit: cover`; no CSS-drawn imagery or placeholder shape was introduced.
- Copy and content: the heading is now “风潮穿搭精选”; below-stage caption, interaction hint, `01 / 10`, and 01–10 navigation are absent as requested. The development-mode note remains above the gallery to keep the data boundary honest.
- Icons and accessibility: arrow icons/buttons are removed. Image cards retain accessible names and click behavior; the stage keeps keyboard shortcuts, and reduced-motion disables autoplay while preserving access to the cards.

### Comparison history

- Initial refinement pass: removed the requested chrome and transparentized the stage; desktop capture showed the intended wider gaps, but the first mobile capture (`element-2026-09-08T08-56-20-656Z.png`) exposed card overlap.
- Fix pass: calculated mobile offset from the rendered card width plus 16px; rebuilt the container and recaptured the final mobile evidence above. The final desktop/mobile captures show no actionable P0/P1/P2 mismatch.
- Interaction pass: after a 5-second wait, the active image changed from “同色系丹宁的干净轮廓” to “黑色乐福鞋的利落收尾”, confirming autoplay; the static screenshot was captured while hovering the stage so the transition could settle. DOM checks confirmed zero arrows, captions, dots, or hints, and browser console reported 0 errors.

final result: passed

## 2026-09-08 Top 10 弧形画廊复核（精修前基线，已被后续结果取代）

- Comparison target: `C:/Users/jaime/AppData/Local/Temp/codex-clipboard-73954b98-3246-43a6-9633-6907d8bcab84.png`.
- Desktop evidence: `.playwright-cli/element-2026-09-08T08-41-56-366Z.png`; mobile evidence: `.playwright-cli/element-2026-09-08T08-43-10-044Z.png`.
- The earlier baseline followed the reference's pale stage, portrait image cards, raised center, lowered/rotated outer cards, and photo-only visual treatment. Its controls and captions were intentionally removed in the later refinement above per the new user request.
- Interaction evidence: next button moved `01 / 10` to `02 / 10`; keyboard `ArrowRight` moved it to `03 / 10`; all 10 cards and 10 tabs remained exposed to the accessibility tree.
- Responsive evidence: at 390px, the stage measured 342.67px × 380px and the document did not exceed its layout width; no browser page errors were emitted.

final result: passed
