// Editorial analysis of the exact images inspected in docs/research, not account-wide rules.
const analyses = {
  '5347432265156146': {
    title: '白T和牛仔裤，怎样通过外搭增加层次？',
    scope: '原帖第4张 · 白色内搭与浅蓝牛仔裤',
    applicationHint: '白色上衣与白鞋呼应，浅蓝牛仔裤配深色外搭；腰间系衣可按繁简偏好取舍',
    images: [{
      id: 'main', url: '/assets/trend-analysis/savis-autumn.jpg', label: '原帖第4张', width: 517, height: 690,
      pins: [{ point: 'color', x: 53, y: 54 }, { point: 'waist', x: 54, y: 68 }, { point: 'shape', x: 49, y: 91 }]
    }],
    points: [
      {
        id: 'color', title: '明暗分出内外，白色在上下呼应',
        brief: '白内搭被深格纹衬衫围住，白鞋在下方重复；内外层次清楚，单品之间也有联系。',
        observation: '胸前白色被深格纹衬衫包围，鞋子也是白色；黑包重复了外搭的深色。',
        reason: '胸前明暗边界让内搭与衬衫容易分辨；白色上下重复，把分散在身体两端的单品联系起来。浅蓝裤承担中间的大面积颜色。',
        tradeoff: '颜色呼应是一个可用的方法。若更喜欢集中的深色上身，也可以降低内搭与衬衫之间的对比。',
        test: '固定衬衫、裤子和鞋，只换版型近似的白色与深色内搭，在相近光线下比较。'
      },
      {
        id: 'waist', title: '系衣提供分界，也增加腰间体积',
        brief: '浅色系衣把宽松上下装分开，衣片叠在裤子前面；它也让腰胯处多了布料与结。',
        observation: '浅米色衣物横跨腰间，结与垂下衣片盖住部分裤腰和裤装。',
        reason: '宽松的上下装之间多了一条横向浅色带，视觉分界更清楚；前侧衣片又增加可辨认的前后层次。',
        tradeoff: '布料和结也增加腰胯处的体积。这个分界不等于真实腰线，不能由此承诺显瘦或显高。',
        test: '分别拍系与不系两张照片，比较腰间厚度和走动便利；取下后记得整理衬衫下摆。'
      },
      {
        id: 'shape', title: '宽裤口与鞋的体积相互衔接',
        brief: '宽裤口下露出的鞋头和鞋底也有体积，轮廓没有突然收窄；先检查裤脚是否影响走路。',
        observation: '裤腿宽，裤脚接近鞋面；鞋头圆钝，鞋底在画面中可见。',
        reason: '从宽裤口到脚部的宽度变化较缓。换成细窄鞋头，宽窄反差就会更明显，可以根据自己喜欢的轮廓选择。',
        tradeoff: '宽裤并不必须配厚鞋。实际裤长与鞋底高度要一起检查，避免裤脚拖地或踩住鞋跟。',
        test: '固定裤子，换两双体积不同的鞋，分别看正面和侧面，并实际走几步。'
      }
    ],
    changes: [
      { id: 'waist', label: '取下腰间系衣', kept: '白内搭与白鞋的呼应、衬衫与内搭的明暗边界仍在。', changed: '少一条浅色横带和前侧衣片，腰间更简单，叠穿细节也减少。', test: '固定其他单品，比较系与不系的局部厚度和活动便利，并重新整理衬衫下摆。' },
      { id: 'inner', label: '白内搭换深色', kept: '裤子、鞋、外搭、系衣及宽松轮廓保持接近。', changed: '胸前对比减弱，上身颜色更集中；浅色系衣与裤子相对更突出。', test: '只换款式近似的深色内搭，在相近光线下看你更喜欢哪种层次。' },
      { id: 'jeans', label: '浅蓝裤换深蓝', kept: '尽量保留裤型与裤长，白内搭与白鞋的呼应仍在。', changed: '整套深色面积增加，白色对比更强；颜色变化不能单独证明更正式或显瘦。', test: '固定上衣、鞋和拍照角度，比较版型接近的两条牛仔裤。' }
    ],
    steps: ['先用已有的浅色上衣、牛仔裤和浅色鞋建立主体。', '再加入深色外搭，检查内外颜色是否符合自己想要的对比。', '最后决定腰间是否增加系衣，优先考虑繁简偏好和活动便利。'],
    note: '原文仅简述北京初秋。以上为对已查看原图的分析，并非博主原话。照片的光线、站姿和镜头也影响比例；替换建议尚未经过真人对照试穿。'
  },
  '5310383998634818': {
    title: '同一件马甲，颜色分界怎样改变重点？',
    scope: '原帖前2张 · 白裤与近似沙色裤的两种组合',
    applicationHint: '固定马甲比较白色裤与近似色裤，先选上下颜色分界，再决定短袖内搭和颈部配饰',
    images: [
      { id: 'a', url: '/assets/trend-analysis/sanmu-vest-a.jpg', label: 'A · 白裤与蓝白颈巾', width: 453, height: 690, pins: [{ point: 'color', x: 59, y: 52 }, { point: 'neck', x: 61, y: 32 }, { point: 'line', x: 58, y: 77 }] },
      { id: 'b', url: '/assets/trend-analysis/sanmu-vest-b.jpg', label: 'B · 近似色裤与白色内搭', width: 460, height: 690, pins: [{ point: 'color', x: 57, y: 59 }, { point: 'neck', x: 58, y: 39 }, { point: 'line', x: 55, y: 82 }] }
    ],
    points: [
      { id: 'color', title: '白裤分清上下，近似色降低腰部色差', brief: 'A的沙色马甲接白裤，腰部色差较明显；B的上下近似色更容易看成一组。', observation: 'A为沙色马甲与白裤，B为沙色马甲与接近沙色的裤装，B另有白色短袖内搭。', reason: 'A在腰部的颜色变化更清楚，容易辨认上衣结束的位置；B的颜色跳变较小，上下更容易被读作连续的一组。', tradeoff: '颜色接近不等于实体边界消失，也不能单独承诺显高；衣长、裤长和站姿仍然参与画面比例。', test: '固定马甲、内搭与鞋，只换版型和裤长接近的白裤与近似色裤，比较自己喜欢的分界。' },
      { id: 'neck', title: '颈巾与白T，把细节放在不同位置', brief: 'A用少量深蓝条纹点出颈部；B的白色领口和袖子，让内搭这一层更清楚。', observation: 'A有蓝白条纹颈巾，B有白色T恤和小面积项链；两套都有草帽和露趾鞋。', reason: 'A的深蓝颜色较深、面积较小且靠近脸部，成为局部识别点；B的白色在领口与两侧袖子可见，把内外搭关系展示出来。', tradeoff: '配饰数量依个人偏好决定。想减少装饰可以先取下颈巾；加短袖时要检查马甲肩部与袖口是否拥挤。', test: '先固定主体，分别比较不加颈部配饰、加颈巾或加项链，再决定是否增加。' },
      { id: 'line', title: '看衣长、裤长和实际活动空间', brief: '两套裤脚都露出脚踝，马甲都有前襟竖向细节；不能把全部差异归因于裤色。', observation: '两张裤脚均露出脚踝，马甲前襟与纽扣形成竖向细节；帽子、包、姿势和背景也变了。', reason: 'A的白裤让马甲下摆更容易区分；B的近似色减小腰部色差，但衣物边界仍存在。这是两套完整组合，涉及多个变化。', tradeoff: '两张图不是只换裤子的实验，不能证明某种颜色单独造成全部观感差异，也不核定单品材质或套装关系。', test: '对照自己相近角度的照片，同时记录衣长与裤长差异；实际坐下、抬手和走动检查便利。' }
    ],
    changes: [
      { id: 'trousers', label: '只换裤子颜色', kept: '固定马甲、内搭与鞋，尽量保留裤型和裤长。', changed: '白裤与马甲的分界更清楚；近似色裤降低腰部色差。', test: '只比较裤色；若两条裤子版型差别很大，也把这个差异记录下来。' },
      { id: 'scarf', label: '先取下颈巾', kept: 'A的沙色马甲、白裤与鞋包关系保留。', changed: '脸部附近少了深蓝条纹，局部装饰减少；主体配色仍成立。', test: '固定其他配饰，比较有无颈巾是否达到自己想要的繁简程度。' },
      { id: 'tee', label: '加入白色短袖', kept: '固定已有马甲与裤装。', changed: '袖子和领口成为可见的一层，也增加上臂覆盖。', test: '检查肩部、袖口是否挤或堆，再通过抬手和坐下确认活动空间。' }
    ],
    steps: ['先固定马甲、内搭和鞋，只比较白裤与近似色裤。', '再按覆盖需求和活动便利决定是否加入短袖内搭。', '最后尝试一种颈部配饰，看是否还需要增加细节。'],
    note: '原帖主题为一衣多穿。两图同时改变内搭、配饰、姿势、背景与光线；分析展示组合关系，不把全部差异归给单一颜色。替换建议为推演，未核定材质与品牌。'
  }
}

function sourcePostId(trend) {
  try {
    const url = new URL(trend?.sourceUrl)
    if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) return ''
    if (['www.sina.cn', 'sina.cn'].includes(url.hostname)) return url.pathname.match(/^\/news\/detail\/(\d+)\.html$/)?.[1] || ''
    if (['weibo.com', 'www.weibo.com', 'm.weibo.cn'].includes(url.hostname)) return url.pathname.match(/\/(?:detail|status)\/(\d+)\/?$/)?.[1] || ''
  } catch { /* Unknown source: do not attach another post's analysis. */ }
  return ''
}

export function trendOutfitAnalysis(trend) {
  return analyses[sourcePostId(trend)] || null
}

export function isTrendAnalysisExample(trend) {
  return trend?.evidence?.contentScope === 'analysis-example'
    && Boolean(trendOutfitAnalysis(trend))
}

export function createTrendAnalysisExamples(origin) {
  return [
    { post: '5347432265156146', author: 'SavisLook', publishedAt: '2026-09-26T16:17:00+08:00', tags: ['日常休闲', '牛仔裤', '白色', '衬衫'] },
    { post: '5310383998634818', author: '三木三木呢', publishedAt: '2026-06-16T10:41:00+08:00', tags: ['一衣多穿', '马甲', '白色', '同色搭配'] }
  ].map(({ post, author, publishedAt, tags }) => {
    const analysis = analyses[post]
    const imageUrl = new URL(analysis.images[0].url, origin).href
    return {
      id: `analysis-example:${post}`, platform: 'weibo', title: analysis.title,
      publishedAt, topicTags: tags, sourceUrl: `https://www.sina.cn/news/detail/${post}.html`, imageUrl,
      evidence: { author, mediaType: 'image', contentScope: 'analysis-example', images: [imageUrl], fullBodyImageUrl: imageUrl }
    }
  })
}
