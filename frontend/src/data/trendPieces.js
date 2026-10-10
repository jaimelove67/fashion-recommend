// Guides enrich garments extracted from current social trend records. A guide never appears
// unless its garment is mentioned in a non-stale platform record.
export const pieceGuides = [
  {
    id: 'black-raw-denim',
    name: '黑色原牛',
    image: '/assets/trend-pieces/black-raw-denim.png',
    category: '裤装',
    aliases: [/黑(?:色)?原牛/i, /黑(?:色)?原色牛仔裤/i, /black\s+raw\s+denim\s+(?:jeans|pants|trousers)\b/i],
    description: '未经充分预洗的深色牛仔裤，适合用利落的上身和鞋型平衡厚实面料。',
    pairings: [
      { type: '上衣', items: '纯白或浅灰 T 恤、米白衬衫、浅蓝牛津纺衬衫', reason: '浅色上装与黑色裤装形成明暗对比，突出整体轮廓。', tones: [{ name: '纯白', key: 'white' }, { name: '浅灰', key: 'gray' }, { name: '米白', key: 'cream' }, { name: '浅蓝', key: 'blue' }] },
      { type: '鞋履', items: '黑色皮鞋、深棕靴、深色帆布鞋', reason: '深色鞋履与裤装保持色调衔接，平衡整体视觉比例。', tones: [{ name: '黑色', key: 'black' }, { name: '深棕', key: 'brown' }] },
      { type: '配饰', items: '黑色或深棕皮带、深色帆布包', reason: '皮带与鞋履采用相近色调，增强搭配的一致性。', tones: [{ name: '黑色', key: 'black' }, { name: '深棕', key: 'brown' }] }
    ],
    care: [
      '遵循实物洗标；可水洗款建议翻面清洗，使用冷水与温和洗涤剂，自然阴干。',
      '首次清洗与浅色衣物分开；局部污渍可先按洗标做小范围处理。'
    ],
    warning: '新黑色原牛可能发生摩擦掉色，应注意与浅色鞋履、包袋及座椅的接触，潮湿时尤需留意；未预缩面料可能出现洗后缩水。',
    tip: '腰围略松时，先把余量均匀拨向两侧和后腰，再让皮带依次穿过裤袢、逐步收紧，减少门襟处堆叠。若明显过大，改尺码或调整腰围更稳妥。'
  },
  {
    id: 'wide-leg-trousers',
    name: '阔腿裤',
    image: '/assets/trend-pieces/wide-leg-trousers.png',
    category: '裤装',
    aliases: [/阔腿裤/i, /wide[\s-]?leg\s+(?:pants|trousers|jeans)/i],
    description: '宽松裤管适合搭配有清晰肩线或收束下摆的上装。',
    pairings: [
      { type: '上衣', items: '白色短 T 恤、合身衬衫、灰色短针织', reason: '相对合身的上装可平衡宽松裤管，明确上下装比例。' },
      { type: '鞋履', items: '简洁运动鞋、乐福鞋、低跟短靴', reason: '选择鞋头清晰的款式，裤脚不容易显得拖沓。' },
      { type: '配饰', items: '窄皮带、小号肩包', reason: '以小面积配饰强调腰线，优化整体比例。' }
    ],
    care: ['按面料洗标清洗；晾晒前抖平裤管和裤缝。', '收纳时沿裤线折叠或使用裤架，减少不必要的折痕。'],
    warning: '裤脚过长容易踩踏或沾水；试穿时应连同常穿的鞋一起检查长度。',
    tip: '宽松裤型可搭配前摆轻塞的上装，明确腰线后再选择是否增加皮带。'
  },
  {
    id: 'white-shirt',
    name: '白衬衫',
    image: '/assets/trend-pieces/white-shirt.png',
    category: '上装',
    aliases: [/白(?:色)?衬衫/i, /white\s+shirt\b/i],
    description: '利落的白色上装，适合作为深色裤装或外套的明亮底层。',
    pairings: [
      { type: '裤装', items: '黑色原牛、深灰西裤、蓝色牛仔裤', reason: '深浅色对比清晰，适合通勤与日常休闲场合。' },
      { type: '鞋履', items: '黑色乐福鞋、棕色皮鞋、白色简洁运动鞋', reason: '根据正式程度选择鞋型。' },
      { type: '配饰', items: '深色皮带、细项链或简洁手表', reason: '少量配饰即可呼应领口和袖口。' }
    ],
    care: ['按洗标分色清洗；污渍先局部处理，再进行正常清洗。', '熨烫温度和方式以面料洗标为准，收纳前确保完全干燥。'],
    warning: '新深色牛仔裤可能发生颜色转移，应注意与白衬衫衣摆的接触。',
    tip: '衣摆只在前方轻塞一小段，侧面自然垂下，可以显出腰线而不必整理整圈。'
  },
  {
    id: 'knit-cardigan',
    name: '针织开衫',
    image: '/assets/trend-pieces/knit-cardigan.png',
    category: '上装',
    aliases: [/针织开衫/i, /knit(?:ted)?\s+cardigan/i],
    description: '柔软的开襟上装，适合给简洁内搭增加层次。',
    pairings: [
      { type: '内搭', items: '白色 T 恤、浅灰背心、细条纹上衣', reason: '低对比内搭能保留针织纹理。' },
      { type: '裤装', items: '深色直筒裤、蓝色牛仔裤', reason: '直线条裤型能平衡针织的柔软感。' },
      { type: '鞋履', items: '乐福鞋、简洁运动鞋', reason: '乐福鞋适合简洁得体的搭配，运动鞋更偏向日常休闲。' }
    ],
    care: ['护理前确认纤维成分与洗标，可水洗款采用适合面料的温和清洗方式。', '收纳前确认完全干燥，容易拉长的款式可折叠放置。'],
    warning: '部分针织面料会因悬挂或高温护理变形，具体以洗标为准。',
    tip: '可扣合开衫中部一至两颗纽扣，调整内搭露出面积，使层次更清晰。'
  },
  {
    id: 'blazer',
    name: '西装外套',
    image: '/assets/trend-pieces/blazer.png',
    category: '外套',
    aliases: [/西装外套/i, /(?:tailored\s+)?blazer/i],
    description: '有结构感的外套，可以给休闲下装增加利落感。',
    pairings: [
      { type: '内搭', items: '白色 T 恤、浅蓝衬衫、黑色薄针织', reason: '简洁内搭有助于突出外套剪裁。' },
      { type: '裤装', items: '黑色原牛、深灰直筒裤', reason: '正式和休闲面料混搭，适合日常场景。' },
      { type: '鞋履', items: '乐福鞋、简洁皮鞋、低调运动鞋', reason: '通过鞋型控制整体正式程度。' }
    ],
    care: ['按洗标选择清洁方式；穿后先通风，再用合适衣架挂放。', '局部污渍优先按面料要求处理，不自行用高温熨压未知面料。'],
    warning: '有衬里或肩部结构的款式不宜直接按普通衬衫方式洗涤。',
    tip: '外套偏宽时，先检查肩线是否合适；只靠卷袖无法修正明显不合身的肩部。'
  },
  {
    id: 'white-sneakers',
    name: '白色运动鞋',
    image: '/assets/trend-pieces/white-sneakers.png',
    category: '鞋履',
    aliases: [/白(?:色)?(?:运动鞋|球鞋|板鞋)/i, /white\s+sneakers?\b/i],
    description: '浅色鞋履可与深色裤装形成明暗对比，减轻整体视觉重量。',
    pairings: [
      { type: '裤装', items: '深蓝直筒牛仔裤、灰色阔腿裤、黑色原牛', reason: '鞋面和裤装形成清晰的颜色对比。' },
      { type: '上衣', items: '白色 T 恤、浅色衬衫、灰色卫衣', reason: '上装采用小面积浅色，与鞋履形成配色呼应。' },
      { type: '配饰', items: '浅色帆布包、银色小配饰', reason: '保持整体轻盈。' }
    ],
    care: ['按鞋面材质与品牌说明选择清洁方式，先以软布清除表面灰尘。', '完全晾干后再收纳，避免长时间潮湿。'],
    warning: '与新深色牛仔裤接触可能发生颜色转移，清洁方式应根据鞋面材质选择。',
    tip: '裤脚覆盖鞋面过多时，应先检查裤长，再酌情小幅卷边。'
  },
  {
    id: 'loafers',
    name: '乐福鞋',
    image: '/assets/trend-pieces/loafers.png',
    category: '鞋履',
    aliases: [/乐福鞋/i, /loafers?\b/i],
    description: '鞋面线条简洁，可提升休闲裤装搭配的正式程度。',
    pairings: [
      { type: '裤装', items: '深色直筒牛仔裤、九分西裤、阔腿裤', reason: '适当露出鞋面，有助于保持清晰的下装比例。' },
      { type: '上衣', items: '白衬衫、针织开衫、合身 T 恤', reason: '可以在正式与休闲之间切换。' },
      { type: '配饰', items: '与鞋子同色系的皮带或包', reason: '小面积同色配饰可增强整体配色的一致性。' }
    ],
    care: ['按皮革、麂皮或合成材质分别查看品牌护理说明。', '穿后擦去表面灰尘，完全干燥再收纳。'],
    warning: '麂皮与光面皮的清洁方式不同，不宜直接使用同一种清洁剂。',
    tip: '裤脚堆积在鞋面时，可调整裤长，避免遮挡鞋履轮廓。'
  },
  {
    id: 'black-belt',
    name: '黑色皮带',
    image: '/assets/trend-pieces/black-belt.png',
    category: '配饰',
    aliases: [/黑(?:色)?皮带/i, /black\s+(?:leather\s+)?belt/i],
    description: '用于衔接上装与裤装的配饰，同时可调节略宽松的裤腰。',
    pairings: [
      { type: '裤装', items: '黑色原牛、深灰西裤、蓝色直筒牛仔裤', reason: '深色腰线更容易融入裤装。' },
      { type: '鞋履', items: '黑色乐福鞋、黑色皮鞋', reason: '颜色相呼应，整体更统一。' },
      { type: '上衣', items: '白衬衫、灰色针织', reason: '浅色上装可突出深色腰线，使比例更清晰。' }
    ],
    care: ['先确认材质；皮革款按品牌说明清洁并保持干燥。', '收纳时平放或宽松卷放，避免反复折叠同一位置。'],
    warning: '过度勒紧可能让裤腰和裤袢变形；明显不合身的裤子应调整尺码。',
    tip: '将裤腰余量均匀分配至两侧与后腰，再逐步收紧皮带，可减少门襟处的面料堆积。'
  }
]

pieceGuides.push(
  {
    id: 'sweater',
    name: '毛衣',
    image: '/assets/trend-pieces/knit-cardigan.png',
    category: '上装',
    aliases: [/毛衣/i, /针织衫/i, /羊毛衫/i, /sweater\b/i, /jumper\b/i],
    description: '趋势内容中提及的针织上衣，建议结合气温与面料厚度选择。',
    pairings: [
      { type: '下装', items: '直筒牛仔裤、垂感西裤或过膝半裙', reason: '用利落下装平衡针织的体积。' },
      { type: '鞋履', items: '乐福鞋、短靴或简洁运动鞋', reason: '按针织厚度和场合调整鞋型。' },
      { type: '配饰', items: '简洁皮带或小型肩包', reason: '保持搭配轻盈，避免针织纹理旁堆叠过多配饰。' }
    ],
    seasons: {
      spring: {
        status: '按厚度选择',
        note: '春季温差较大时可选择薄针织，气温回升后优先轻薄上装。',
        pairings: [
          { type: '下装', items: '浅色直筒牛仔裤或棉质长裙', reason: '用轻盈下装配薄针织，避免整体显厚。' },
          { type: '鞋履', items: '乐福鞋或轻便运动鞋', reason: '适合春季日常步行。' }
        ]
      },
      summer: {
        status: '不建议穿',
        note: '夏季或高温天气建议减少厚针织穿着，气温降低后再按厚度选择。',
        pairings: []
      },
      autumn: {
        status: '适合',
        note: '秋季可选中等厚度针织；气温较高时优先薄款。',
        pairings: [
          { type: '下装', items: '直筒牛仔裤、垂感西裤或过膝半裙', reason: '下装线条简洁，给针织纹理留出重点。' },
          { type: '鞋履', items: '乐福鞋或低跟短靴', reason: '适合秋季的轻层次穿法。' },
          { type: '配饰', items: '简洁皮带或小型肩包', reason: '小面积点缀，不增加厚重感。' }
        ]
      },
      winter: {
        status: '适合',
        note: '冬季可把毛衣放在保暖内搭与外套之间，按体感增减层次。',
        pairings: [
          { type: '内搭', items: '轻薄保暖打底', reason: '贴身层保持干爽，减少厚针织直接接触皮肤。' },
          { type: '外套', items: '羊毛大衣或保暖棉服', reason: '外层负责挡风保暖，留意室内外温差。' },
          { type: '下装', items: '厚实直筒长裤或保暖半裙', reason: '与上身保暖程度相配。' }
        ]
      }
    },
    care: ['先看纤维成分和洗标；羊毛、混纺与棉针织的清洁方式不同。', '洗后平铺晾干，减少悬挂造成的拉长和肩部变形。'],
    warning: '针织纤维可能起球或被饰品勾丝；护理前先确认洗标。',
    tip: '室内偏热时可脱下外层并平放收纳，避免长时间挤压后出现折痕。'
  },
  {
    id: 'thermal-tights',
    name: '光腿神器 / 打底袜',
    image: '',
    category: '袜品',
    aliases: [/光腿神器/i, /打底袜/i, /连脚(?:款|袜)?/i, /踩脚(?:款|袜)?/i, /thermal tights?/i],
    description: '趋势测评内容中提及的连脚或踩脚打底袜。',
    pairings: [
      { type: '下装', items: '长度合适的半裙或连衣裙', reason: '选择与袜品厚度相配的裙长和面料。' },
      { type: '鞋履', items: '包脚短靴、乐福鞋或简洁单鞋', reason: '确认袜脚与鞋内空间合适，避免挤压。' },
      { type: '配色', items: '接近肤色的自然色调', reason: '颜色应贴近本人肤色和外层裙装。' }
    ],
    seasons: {
      spring: { status: '按气温选择', note: '春季转暖后可改成薄款；避免厚袜与轻薄裙装造成闷热。' },
      summer: { status: '不建议穿', note: '夏季或高温天气不建议穿加厚打底袜；可改用轻薄袜品或不穿保暖层。', pairings: [] },
      autumn: { status: '适合', note: '秋季降温后可按体感选择厚度，午间气温较高时优先轻薄款。' },
      winter: { status: '适合', note: '冬季选保暖厚度时，也要确认鞋内空间，避免脚趾受挤。' }
    },
    care: ['按洗标轻柔清洗，拉链、魔术贴等尖锐部件分开收纳。', '完全干燥后平放或轻卷收纳，减少勾丝和拉伸。'],
    warning: '过紧会影响舒适度；选择尺码时不要只按外观拉伸效果判断。',
    tip: '试穿连脚款和踩脚款时，重点比较脚踝是否堆褶、鞋内是否受挤。'
  }
)

export const pieceCategories = ['全部', '裤装', '上装', '外套', '裙装', '袜品', '鞋履', '配饰']

const socialPlatforms = new Set(['douyin', 'weibo'])
const seasonLabels = { spring: '春季', summer: '夏季', autumn: '秋季', winter: '冬季' }
const seasonalCategoryPairings = {
  spring: {
    '裤装': [
      { type: '上衣', items: '薄款长袖、棉质衬衫或轻针织', reason: '春季早晚保留一层，午间可方便增减。' },
      { type: '鞋履', items: '乐福鞋、帆布鞋或轻便运动鞋', reason: '应对温差和日常步行。' }
    ],
    '上装': [
      { type: '下装', items: '直筒长裤或棉质半裙', reason: '轻薄下装可平衡春季叠穿的层次与重量。' },
      { type: '外搭', items: '轻薄风衣或短外套', reason: '早晚降温时加一层，白天可脱下。' }
    ],
    '外套': [
      { type: '内搭', items: '棉质 T 恤或薄衬衫', reason: '方便随温度变化调整。' },
      { type: '下装', items: '直筒牛仔裤或轻薄西裤', reason: '保持层次利落，不叠加过厚面料。' }
    ],
    '鞋履': [
      { type: '下装', items: '九分直筒裤或过膝裙', reason: '留出轻盈的鞋面比例。' },
      { type: '上衣', items: '薄衬衫或棉质长袖', reason: '适合春季早晚温差。' }
    ],
    '配饰': [
      { type: '搭配', items: '薄外套、直筒长裤与轻便鞋履', reason: '按早晚温差增减外层。' }
    ]
  },
  summer: {
    '裤装': [
      { type: '上衣', items: '棉麻短袖、轻薄衬衫或透气背心', reason: '夏季优先轻薄、透气和宽松度。' },
      { type: '鞋履', items: '帆布鞋、透气运动鞋或凉鞋', reason: '按场合选择通风鞋型。' },
      { type: '配饰', items: '轻薄织带或少量金属配饰', reason: '减少厚重皮革和叠戴。' }
    ],
    '上装': [
      { type: '下装', items: '亚麻长裤、棉质短裤或轻薄半裙', reason: '下装选透气面料，减少闷热。' },
      { type: '鞋履', items: '帆布鞋、凉鞋或透气运动鞋', reason: '按步行量和场合选择。' }
    ],
    '外套': [
      { type: '内搭', items: '轻薄棉质 T 恤或背心', reason: '热天优先单层穿着；薄外搭只留给空调环境。' },
      { type: '下装', items: '亚麻长裤或轻薄半裙', reason: '选择透气、不贴身的面料。' }
    ],
    '裙装': [
      { type: '鞋履', items: '凉鞋、轻便单鞋或透气帆布鞋', reason: '结合步行量选舒适鞋型。' },
      { type: '配饰', items: '轻便小包与少量饰品', reason: '避免厚重叠搭。' }
    ],
    '鞋履': [
      { type: '下装', items: '亚麻长裤、棉质短裤或轻薄半裙', reason: '配合鞋型保持通风和活动舒适。' },
      { type: '上衣', items: '棉麻短袖或透气衬衫', reason: '减少高温下的厚重层次。' }
    ],
    '配饰': [
      { type: '搭配', items: '棉麻上衣、透气下装与轻便鞋履', reason: '夏季减少厚重皮革和多层叠戴。' }
    ]
  },
  autumn: {
    '裤装': [
      { type: '上衣', items: '薄针织、长袖衬衫或棉质卫衣', reason: '按气温调整厚度，早晚可加轻外套。' },
      { type: '鞋履', items: '乐福鞋、短靴或帆布鞋', reason: '适合秋季日常步行。' },
      { type: '配饰', items: '简洁皮带或轻薄围巾', reason: '以适量配饰增加层次，避免过度叠搭。' }
    ],
    '上装': [
      { type: '下装', items: '直筒牛仔裤、垂感西裤或过膝半裙', reason: '简洁的下装线条可平衡上装叠穿层次。' },
      { type: '鞋履', items: '乐福鞋或短靴', reason: '随温度和场合选择包脚鞋型。' }
    ],
    '外套': [
      { type: '内搭', items: '长袖 T 恤、衬衫或薄针织', reason: '室内外切换时可以增减。' },
      { type: '下装', items: '直筒裤或过膝半裙', reason: '让外套成为主要层次。' }
    ],
    '鞋履': [
      { type: '下装', items: '直筒长裤、九分西裤或过膝裙', reason: '按鞋帮高度调整裤脚和裙长。' },
      { type: '上衣', items: '长袖衬衫或轻针织', reason: '适合秋季叠穿。' }
    ],
    '配饰': [
      { type: '搭配', items: '薄针织、直筒下装与包脚鞋履', reason: '根据早晚温差增减一层。' }
    ]
  },
  winter: {
    '裤装': [
      { type: '上衣', items: '保暖打底、羊毛针织或厚衬衫', reason: '按室外温度叠穿，进入室内便于脱减。' },
      { type: '外套', items: '羊毛大衣、棉服或羽绒外套', reason: '优先保证挡风和保暖。' },
      { type: '鞋履', items: '包脚短靴或保暖运动鞋', reason: '寒冷天气注意鞋底防滑。' }
    ],
    '上装': [
      { type: '内搭', items: '贴身保暖打底', reason: '保持干爽，减少厚衣直接贴身。' },
      { type: '外套', items: '羊毛大衣、棉服或羽绒外套', reason: '按室外温度选择保暖程度。' },
      { type: '下装', items: '厚实长裤或保暖半裙', reason: '让上下身保暖程度相配。' }
    ],
    '外套': [
      { type: '内搭', items: '保暖打底和中层针织', reason: '用多层薄衣物应对室内外温差。' },
      { type: '下装', items: '厚实长裤或保暖裙装', reason: '减少冷风从下身进入。' }
    ],
    '裙装': [
      { type: '保暖', items: '按体感加保暖袜品与长外套', reason: '注意室外温度和鞋内空间。' },
      { type: '鞋履', items: '包脚短靴或防滑鞋', reason: '寒冷天气优先保暖与防滑。' }
    ],
    '袜品': [
      { type: '下装', items: '长度合适的半裙或连衣裙', reason: '选择与袜品厚度相配的裙长。' },
      { type: '鞋履', items: '留有脚趾空间的包脚鞋或短靴', reason: '袜品加厚后重新确认鞋内空间。' }
    ],
    '鞋履': [
      { type: '下装', items: '直筒长裤或保暖半裙', reason: '按鞋帮高度调整裤脚或裙长。' },
      { type: '搭配', items: '保暖袜品与挡风外套', reason: '兼顾鞋内舒适和室外保暖。' }
    ],
    '配饰': [
      { type: '搭配', items: '保暖外套、厚实下装与包脚鞋履', reason: '冬季优先保暖，再控制配饰层数。' }
    ]
  }
}

function calendarSeason(date) {
  const month = date.getMonth() + 1
  if (month >= 3 && month <= 5) return 'spring'
  if (month >= 6 && month <= 8) return 'summer'
  if (month >= 9 && month <= 11) return 'autumn'
  return 'winter'
}

export function resolveSeason(weather = null, date = new Date()) {
  const weatherIsLive = weather && weather.source !== 'configured-demo'
  const reportedTemperature = weather?.apparentTemperatureC ?? weather?.temperatureC
  const rawTemperature = weatherIsLive && reportedTemperature !== null && reportedTemperature !== undefined && reportedTemperature !== ''
    ? Number(reportedTemperature)
    : Number.NaN
  const key = Number.isFinite(rawTemperature) && rawTemperature >= 26
    ? 'summer'
    : Number.isFinite(rawTemperature) && rawTemperature <= 8
      ? 'winter'
      : calendarSeason(date)
  const temperature = Number.isFinite(rawTemperature) ? Math.round(rawTemperature) : null
  return {
    key,
    label: seasonLabels[key],
    temperature,
    description: temperature === null ? `按当前日期提供${seasonLabels[key]}参考` : `${temperature}°C · ${seasonLabels[key]}建议`
  }
}

function applySeason(guide, season) {
  const seasonal = guide.seasons?.[season.key]
  const categoryPairings = seasonalCategoryPairings[season.key]?.[guide.category]
  const temperatureNote = season.temperature >= 28
    ? `当前 ${season.temperature}°C，优先考虑轻薄、透气的面料，减少叠穿。`
    : season.temperature !== null && season.temperature <= 10
      ? `当前 ${season.temperature}°C，建议增加保暖层，并根据室内外温差增减衣物。`
      : ''
  return {
    ...guide,
    seasonStatus: seasonal?.status || '当季建议',
    seasonNote: seasonal?.note || temperatureNote,
    pairings: seasonal?.pairings ?? categoryPairings ?? guide.pairings
  }
}

function averageTrendHeat(mentions) {
  const scores = mentions.map((item) => Number(item.heatScore)).filter((score) => Number.isFinite(score) && score > 0)
  return scores.length ? scores.reduce((total, score) => total + score, 0) / scores.length : 0
}

function newestMention(mentions) {
  return mentions.reduce((latest, item) => {
    const published = Date.parse(item.publishedAt || '') || 0
    return Math.max(latest, published)
  }, 0)
}

export function collectTrendPieces(trends = [], season = resolveSeason()) {
  const grouped = pieceGuides.map((guide) => ({ ...applySeason(guide, season), mentions: [] }))

  for (const trend of trends) {
    if (!trend || typeof trend !== 'object' || !socialPlatforms.has(trend.platform) || trend.stale === true) continue
    // Match specific garment names only; generic style tags such as "丹宁" match no alias.
    const text = [trend.title, trend.summary, ...(Array.isArray(trend.topicTags) ? trend.topicTags : [])]
      .filter(Boolean).join(' ')
    for (const piece of grouped) {
      if (piece.aliases.some((alias) => alias.test(text))) piece.mentions.push(trend)
    }
  }

  return grouped
    .filter((piece) => piece.mentions.length)
    .map((piece) => ({ ...piece, heatScore: averageTrendHeat(piece.mentions) }))
    .sort((left, right) => right.heatScore - left.heatScore
      || right.mentions.length - left.mentions.length
      || newestMention(right.mentions) - newestMention(left.mentions))
}
