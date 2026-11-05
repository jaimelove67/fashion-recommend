import { outfitTechniques } from './outfitTechniques.js'

// Editorial cases illustrate the existing methods; they are separate from source paraphrases.
const cases = {
  'color-context-from-posts': {
    summary: '先分清整身撞色与近脸明暗，再找到该调整的那一件。这个案例先看蓝色上衣与红包之间的关系。',
    context: '日常出门 · 蓝色上衣与红色肩包',
    question: '衣服和包都喜欢，搭在一起却只看见包？',
    problem: '蓝色针织衫和牛仔裤已经穿好，背上亮红包后，视线总被包吸引。你想保留蓝色这身搭配，又希望整套看起来柔和一些，不知道该从哪里改。',
    goal: '保留蓝色主调，减弱包与上衣之间的强对比。',
    solutionTitle: '先保留衣服，只把包的颜色换柔和',
    solution: '上衣、裤子和光线保持一致，先用已有的低饱和豆沙棕包替换亮红包。比较的是包的颜色带来的变化；如果在意的是脸边的明暗，则另行比较领口内搭。',
    beforeTitle: '亮红包成为视觉焦点',
    afterTitle: '低饱和包收敛了对比',
    before: '蓝色上衣与高饱和红色包形成强烈对比，包在整套中很突出。',
    after: '换成低饱和豆沙棕包，保留原来的蓝色搭配，让包的存在感柔和一些。',
    palette: [
      { label: '调整前', colors: [{ label: '蓝色上衣', color: '#165c9c' }, { label: '深蓝牛仔', color: '#30465d' }, { label: '亮红包', color: '#cc353b' }] },
      { label: '调整后', colors: [{ label: '蓝色上衣', color: '#165c9c' }, { label: '深蓝牛仔', color: '#30465d' }, { label: '豆沙棕包', color: '#ad827b' }] }
    ],
    reasoning: [
      { title: '上衣与牛仔裤先组成蓝色主调', body: '针织衫是鲜明的蓝色，牛仔裤是更深、较灰的蓝色。两件属于相近的蓝色系，靠深浅差别区分上下，又让整身大面积颜色保持联系。因此，这套搭配已经有了明确的主色，不需要再增加一个同样强烈的色彩中心。' },
      { title: '亮红包同时带来了色相差和鲜艳度', body: '色相是红、蓝这样的颜色类别；饱和度是颜色鲜艳还是偏灰。蓝色偏冷，红包偏暖，红蓝之间的色相差很明显；图中的上衣与包又都比较鲜艳。红包虽然面积小，却是大面积蓝色中独立的一块暖色，加上包带贯穿上身，容易成为醒目的点缀。' },
      { title: '豆沙棕包保留暖色点缀，但降低了竞争感', body: '调整后的包带有偏灰的粉棕色，比亮红柔和，仍与蓝色形成冷暖区别，但不再用同样鲜艳的色块争夺注意力。这样能保留蓝色上衣的存在感，让包退到辅助位置。这里同时改变了包的色相与饱和度，不能把全部变化只归因于“降低饱和度”。' },
      { title: '选择取决于你想让谁成为主角', body: '想要活泼、醒目的红蓝撞色，可以保留红包，再减少其他亮色配饰；想突出蓝色衣服，则更适合这类柔和的粉棕、灰褐或其他低饱和包。关键是安排主色与点缀色的关系，红蓝搭配本身并没有搭错。' }
    ],
    images: [
      { url: '/assets/techniques/color-before-wide.jpg', alt: 'AI 示意：蓝色针织上衣搭配高饱和红色肩包' },
      { url: '/assets/techniques/color-after-wide.jpg', alt: 'AI 示意：保留蓝色上衣，改用低饱和豆沙棕肩包' }
    ]
  },
  'proportion-goal-from-posts': {
    summary: '比例不只取决于腰线。先决定想强调分界，还是让上下连贯，再调整色差和衣摆。',
    context: '日常通勤 · 浅上衣与深色裤装',
    question: '想穿得上下连贯，腰间的深浅分界却很抢眼？',
    problem: '你穿了浅色衬衫和深色直筒裤，衣服都合身，但上下像被颜色分成了两块。今天想要的是流畅、连贯的感觉，又不想一直靠塞衣角或提高腰线来调整。',
    goal: '减弱腰部色差，让上下装的颜色自然衔接。',
    solutionTitle: '先让上下近色，再判断是否需要动衣摆',
    solution: '保留同一件浅色上衣、同样的衣摆位置和鞋，先换一条版型与裤长接近的浅米色裤子。这样能先看清颜色分界的变化，再决定要不要调整收腰或衣摆。',
    beforeTitle: '深浅色块分界明显',
    afterTitle: '近色衔接更加连贯',
    before: '浅色上衣与深蓝裤装反差明显，视线容易停在上下装的交界处。',
    after: '浅色上衣搭配浅米色裤装，裤型和衣摆保持接近，先减弱颜色分界。',
    reasoning: [
      { title: '明显的明暗差，会把腰部变成分界线', body: '左图上衣浅、裤子深，两个大色块在衣摆和裤腰附近交接。眼睛很容易先注意这个明暗突变的位置，再把上下看成两个区域。它能帮助强调腰线，但如果你想要上下连贯，这条横向分界就会比预期突出。' },
      { title: '浅米色接近上衣，减弱的是颜色边界', body: '右图用浅米色裤装接住偏暖的浅色衬衫，两件的明暗和底色更接近，视线从上往下移动时，颜色变化不那么突然。衣摆、裤腰和身材比例并没有因此消失，改变的是上下被颜色分开的程度；不应把这种连贯感直接等同于一定显高。' },
      { title: '颜色连贯与腰线清楚，是两种可以选择的效果', body: '希望分界清楚，可以保留深浅配色，再让衣摆落在你想强调的位置；希望整体连贯，可以先缩小上下色差，再用面料纹理、衣摆或腰带保留层次。比较时尽量固定裤型与裤长，才容易分清变化来自颜色，还是来自衣服形状。' }
    ],
    images: [
      { url: '/assets/techniques/proportion-comparison.png', panel: 'left', alt: 'AI 示意：浅色衬衫配深蓝直筒裤，腰间颜色分界明显' },
      { url: '/assets/techniques/proportion-comparison.png', panel: 'right', alt: 'AI 示意：同一件浅色衬衫改配浅米色直筒裤，上下颜色连贯' }
    ]
  },
  'shoe-trouser-from-posts': {
    summary: '裤脚堆积不一定只是裤子太长。把鞋面、裤口和裤长放在一起看，先找到形成褶皱的位置。',
    context: '出门换鞋 · 裤脚在鞋面上堆起',
    question: '裤子没换，换双鞋后裤脚怎么突然堆起来了？',
    problem: '同一条直筒裤，穿上体积较大的运动鞋后，裤料被鞋面托住，脚踝附近多了几道褶。你想让裤腿自然落下，却拿不准是鞋的影响，还是应该直接改短裤子。',
    goal: '先找出鞋面支撑对裤脚的影响，再决定是否改裤长。',
    solutionTitle: '裤子先不动，用不同体积的鞋做对照',
    solution: '保持裤腰位置、裤长与裤脚处理一致，换成鞋面较低、轮廓更简洁的已有鞋，观察裤料落点。如果仍然拖地或妨碍走路，再单独处理裤长。',
    beforeTitle: '鞋面托住了裤料',
    afterTitle: '裤脚有了下落空间',
    before: '较厚大的鞋面顶住裤脚，布料在脚踝附近堆叠。',
    after: '换成低鞋面款式后，观察褶皱是否减少；裤长和裤腰位置保持一致。',
    reasoning: [
      { title: '布料落不下去，就会在接触点上方堆积', body: '裤腿向下垂落时，如果裤口碰到较高、较宽的鞋面，不能顺着鞋面继续落下，多余布料就会在脚踝附近折起来。左图主要是在裤脚接触鞋的地方出现褶皱，因此鞋面支撑是需要先排查的因素，而不只是看裤子标注的长度。' },
      { title: '较低的鞋面，给同一裤口留下更多下落空间', body: '右图鞋型较薄、鞋面轮廓较低，裤口受到的阻挡减少，布料更容易沿腿部方向下垂，脚踝附近的线条也较连续。但鞋底厚度同样会改变裤脚离地距离：鞋变薄后，过长的裤脚仍可能拖地，不能把换鞋当作改裤长的替代。' },
      { title: '把裤长、裤口宽度和鞋面当成一组关系', body: '裤长决定有多少布料落到鞋附近，裤口宽度决定能否越过鞋面，面料软硬则影响褶皱怎样形成。若换低鞋面后仍拖地，就要处理裤长；若想保留厚鞋，可以比较能容纳鞋面的裤口或更合适的裤长。目标是得到想要的轮廓，并保证走路不踩裤脚。' }
    ],
    images: [
      { url: '/assets/techniques/shoe-trouser-comparison.png', panel: 'left', alt: 'AI 示意：同一条灰色直筒裤落在厚大运动鞋上，脚踝处积料' },
      { url: '/assets/techniques/shoe-trouser-comparison.png', panel: 'right', alt: 'AI 示意：同一条灰色直筒裤搭配低鞋面休闲鞋，裤脚较自然地下落' }
    ]
  },
  'layering-from-post': {
    summary: '叠穿先要穿得舒适，再谈层次。先给中间层留出空间，再整理领口的高低与材质关系。',
    context: '换季叠穿 · 衬衫、开衫与外套',
    question: '多加一层是暖和了，肩背和领口却都挤在一起？',
    problem: '衬衫外加了厚开衫，再套外套后，肩背开始紧绷，抬手不自在。几个领口又堆在同一处，看不出层次。你希望保留叠穿的温度和感觉，而不是继续往外加衣服。',
    goal: '先恢复肩背活动空间，再让领口边缘清楚一些。',
    solutionTitle: '先减薄中间层，再把领口错开',
    solution: '先把厚开衫换成已有的薄针织开衫，或按温度减少一层，抬手确认外套不再明显牵扯。活动舒服后，再整理衬衫领与针织领的高度，让各层边缘错开。',
    beforeTitle: '中间层太厚，边缘拥挤',
    afterTitle: '减轻体积，领口分层',
    before: '厚开衫占用了外套内部空间，肩部紧绷，领口边缘堆在一起。',
    after: '换薄中间层后，再整理领口高低；先解决活动空间，再处理看得见的层次。',
    reasoning: [
      { title: '外套里面的空间有限，厚针织会先占掉活动余量', body: '同一件外套的肩背、袖窿和上臂空间基本固定。粗厚开衫放进去后，增加的不只是保暖层数，还有针织实际占用的体积；外套可能被撑起，抬手时更容易牵扯。左图肩袖的拥挤，需要先通过减薄中间层或更换容量合适的外层解决。' },
      { title: '领口错开，才能看清每层从哪里开始', body: '几层厚边缘挤在同一高度时，领口会形成一团集中的体积。右图薄开衫的 V 领低于衬衫领，外套翻领又在外侧展开；浅蓝、米色和深蓝的边缘依次可见，眼睛能辨认出三层的关系。层次来自可见的高低、颜色和材质区别，而不是单纯多穿一件。' },
      { title: '先解决容量，再整理边缘，两步各有作用', body: '换薄开衫主要改善外套内部的拥挤；整理领口主要改善视觉边界，拉开领口本身不能解决肩背太紧。先确认活动舒适，再决定露出多少衬衫领和针织领，才能同时保留实用性与清楚的层次。天气需要厚中间层时，也可以改用空间更充足的外套。' }
    ],
    images: [
      { url: '/assets/techniques/layering-comparison.png', panel: 'left', alt: 'AI 示意：衬衫和厚开衫挤在深蓝外套内，肩背与领口拥挤' },
      { url: '/assets/techniques/layering-comparison.png', panel: 'right', alt: 'AI 示意：改用薄开衫后外套更舒展，衬衫领与针织领错开' }
    ]
  },
  'formality-from-posts': {
    summary: '单品是否协调，先看场合和细节。以通勤为例，比较同一套衬衫西裤换鞋后的风格变化。',
    context: '普通通勤 · 衬衫、西裤与鞋',
    question: '衬衫西裤已经选好，鞋一穿上却像要去运动？',
    problem: '你为普通工作日选了衬衫和带裤线的西裤，穿上厚底、亮色细节的跑鞋后，觉得整套的运动感比预期强。你希望更利落地通勤，又不想只靠“西裤一定配皮鞋”来做决定。',
    goal: '让鞋与当前裤装的正式程度更接近，同时满足步行需求。',
    solutionTitle: '保留衬衫和裤子，先比较鞋的风格细节',
    solution: '先把厚底跑鞋换成线条简洁的乐福鞋，看鞋面、鞋底和裤线的关系是否更符合当天通勤目标。若需要较多步行，也可以比较已有的简洁休闲鞋；有明确着装要求时，再以场合规范为准。',
    beforeTitle: '跑鞋强化了运动感',
    afterTitle: '简洁鞋型呼应裤装',
    before: '跑鞋的厚鞋底和亮色细节，与衬衫、西裤形成了明显的运动风反差。',
    after: '换成简洁深棕乐福鞋，让鞋与裤装的正式度接近；是否保留反差取决于当天目标。',
    reasoning: [
      { title: '正式感来自细节，而不是“西裤”这个名称', body: '这套衬衫领口整齐，裤装有清楚的裤线，表面颜色也比较安静，传达的是整洁、有秩序的通勤感。左图跑鞋则有分块鞋面、较厚的运动鞋底和亮色细节，容易让人联想到跑步装备；两组细节放在一起，运动与通勤的反差就比较突出。' },
      { title: '简洁鞋型接住裤线，颜色也收在整套的低调范围内', body: '右图乐福鞋的鞋面较完整、鞋底轮廓较薄，裤线向下延伸时，不会突然遇到很复杂的鞋面分块。深棕鞋与深蓝裤虽然色相不同，但都偏深、较克制，脚部也没有亮色块跳出来。因此，形状和配色都更接近这套衣服原本的通勤表达。' },
      { title: '需要休闲感时，可以主动保留一处反差', body: '衬衫西裤并不只能配皮鞋。想穿休闲鞋通勤，可以比较鞋底体积适中、配色较少、鞋面更简洁的款式，让反差主要集中在鞋的休闲属性；想强调运动混搭，也可以保留跑鞋。要先明确当天想表达什么，再判断这种反差是否合适，并兼顾场合要求与步行舒适度。' }
    ],
    images: [
      { url: '/assets/techniques/formality-comparison.png', panel: 'left', alt: 'AI 示意：浅蓝衬衫和深蓝西裤搭配有亮色细节的厚底跑鞋' },
      { url: '/assets/techniques/formality-comparison.png', panel: 'right', alt: 'AI 示意：保留衬衫西裤，改用线条简洁的深棕色乐福鞋' }
    ]
  }
}

export function getOutfitTechniqueCase(technique) {
  if (!technique || technique.generationMode === 'weekly-ai') return null
  const original = outfitTechniques.find(item => item.id === technique.id)
  // Do not attach a fixed illustration to a rewritten API lesson or a different method.
  if (!original || technique.title !== original.title || technique.example?.before !== original.example.before
      || technique.example?.after !== original.example.after) return null
  const sourceUrls = (technique.sources || []).map(source => source.url).sort()
  if (JSON.stringify(sourceUrls) !== JSON.stringify(original.sources.map(source => source.url).sort())) return null
  return cases[technique.id] || null
}
