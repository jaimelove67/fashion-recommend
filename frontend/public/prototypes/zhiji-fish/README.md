# 双鱼游动登录 · 可行性原型

在 frontend 目录运行 `npm run dev`，打开 `/prototypes/zhiji-fish/index.html`。也可以直接用浏览器打开此目录的 index.html。当前预览服务使用 http://127.0.0.1:5188/prototypes/zhiji-fish/index.html 。

点击中央 Logo → 双鱼沿弧线游向两侧 → 输入演示账号 → 模拟登录成功 → 双鱼继续游走、遮罩退开 → 首页预览。底部可慢放、重播，以及选择模拟登录失败。账号密码只留在当前页面内存，不发请求、不保存。刷新恢复演示值。

实现：原生 Canvas、按用户黑色箭头绘制的三次贝塞尔路径（深绿鱼向右后转向右下，浅绿鱼向左后沿左侧转向上方），鱼头朝向沿路径切线计算、沿鱼身分条绘制的尾部波动。图集由两条完整鱼组成，浏览器分别取左右半幅；左右半幅不是对原 Logo 的裁切。支持移动布局、键盘焦点、系统减少动态效果设置，并取消重播前尚未结束的模拟登录结果。

原型验证实时动画和交互时序。开场保留原始 Logo；游动素材使用内置 image_gen 根据原图生成，有形状差异，通过短暂交叉淡化过渡，并非精确拆解或骨骼绑定。正式版需确认此视觉取舍，或改用精修分层素材。首页为演示画面，不接真实认证。

素材：assets/original-logo.png 为用户提供图片的副本；assets/fish-atlas.png 为内置 image_gen 输出，保留透明通道。没有覆盖正式登录页或原有品牌图片。

生成提示词：

> Edit target: attached Zhiji two fish logo. Create a transparent PNG animation sprite atlas, exactly two separate complete fish placed side by side with generous transparent gap, dark jade fish on left half and pale sage fish on right half. Preserve original elegant curled teardrop fish shapes, leaflike flowing tails, paper watercolor texture, tiny round contrasting eyes, and restrained gold edge accents. Each fish must be an independent continuous complete body with its own flowing tail, NOT a cropped half circle. Both fish face to the RIGHT, head at right and long flowing curved tail toward left, suitable for animated swimming. Same scale both fish, each wholly contained in its own half of the canvas with padding. No circular frame, no text, no letters, no watermark, no checkerboard, genuinely transparent background. Wide landscape sprite sheet.
