from pathlib import Path
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.section import WD_SECTION_START
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(r"C:\other\新建文件夹\毕设\基于大模型（LLM）的智能穿搭推荐")
OUT = ROOT / "docx" / "基于大语言模型的智能穿搭推荐系统_毕业设计成果.docx"
ASSETS = ROOT / ".codex_tmp" / "graduation-assets"
ASSETS.mkdir(parents=True, exist_ok=True)
FONT = r"C:\Windows\Fonts\msyh.ttc"

def f(size, bold=False):
    return ImageFont.truetype(FONT, size, index=0)

def editorial_f(size, active=False):
    return ImageFont.truetype(r"C:\Windows\Fonts\STZHONGS.TTF" if active else r"C:\Windows\Fonts\STSONG.TTF", size)

def set_font(run, name="宋体", size=10.5, bold=False, color=None):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    run._element.rPr.rFonts.set(qn("w:ascii"), "Times New Roman")
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = RGBColor(*color)

def shade(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)

def border_cell(cell, color="A6A6A6"):
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = tc_pr.first_child_found_in("w:tcBorders")
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tc_pr.append(borders)
    for edge in ("top", "left", "bottom", "right"):
        tag = qn(f"w:{edge}")
        element = borders.find(tag)
        if element is None:
            element = OxmlElement(f"w:{edge}")
            borders.append(element)
        element.set(qn("w:val"), "single")
        element.set(qn("w:sz"), "4")
        element.set(qn("w:color"), color)

def set_cell(cell, text, bold=False, center=False, size=9):
    cell.text = ""
    p = cell.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER if center else WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.space_after = Pt(0)
    r = p.add_run(str(text))
    set_font(r, "宋体", size, bold)
    cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
    shade(cell, "F2F4F7" if bold else "FFFFFF")
    border_cell(cell)

def set_repeat_table_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    elem = OxmlElement("w:tblHeader")
    elem.set(qn("w:val"), "true")
    tr_pr.append(elem)

def set_table_widths(table, widths):
    table.autofit = False
    for row in table.rows:
        for i, cell in enumerate(row.cells):
            if i < len(widths):
                cell.width = Cm(widths[i])

def add_table(doc, headers, rows, widths=None):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.style = "Table Grid"
    for i, value in enumerate(headers):
        set_cell(table.rows[0].cells[i], value, bold=True, center=True, size=8.5)
    set_repeat_table_header(table.rows[0])
    for values in rows:
        cells = table.add_row().cells
        for i, value in enumerate(values):
            set_cell(cells[i], value, center=i == 0, size=8.5)
    if widths:
        set_table_widths(table, widths)
    doc.add_paragraph()
    return table

def caption(doc, text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(8)
    r = p.add_run(text)
    set_font(r, "宋体", 9)

def text(doc, value, indent=True):
    p = doc.add_paragraph()
    p.style = "BodyText"
    p.paragraph_format.first_line_indent = Cm(0.74) if indent else Cm(0)
    p.paragraph_format.line_spacing = 1.5
    p.paragraph_format.space_after = Pt(5)
    r = p.add_run(value)
    set_font(r, "宋体", 10.5)
    return p

def bullet(doc, value):
    p = doc.add_paragraph(style="BodyText")
    p.paragraph_format.left_indent = Cm(0.74)
    p.paragraph_format.first_line_indent = Cm(-0.37)
    p.paragraph_format.line_spacing = 1.4
    r = p.add_run("• " + value)
    set_font(r, "宋体", 10.5)

def heading(doc, value, level=1):
    p = doc.add_paragraph(style=f"Heading {level}")
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.space_before = Pt(13 if level == 1 else 9)
    p.paragraph_format.space_after = Pt(7)
    r = p.add_run(value)
    set_font(r, "黑体", 16 if level == 1 else (14 if level == 2 else 12), bold=True)
    return p

def add_page_field(paragraph):
    fld = OxmlElement("w:fldSimple")
    fld.set(qn("w:instr"), "PAGE")
    paragraph._p.append(fld)

def add_toc_field(paragraph):
    fld = OxmlElement("w:fldSimple")
    fld.set(qn("w:instr"), 'TOC \\o "1-3" \\h \\z \\u')
    paragraph._p.append(fld)

def diagram_flow(path):
    im = Image.new("RGB", (1800, 620), "white")
    d = ImageDraw.Draw(im)
    nodes = [(70,240,300,370,"登录与偏好\n设置"),(390,240,650,370,"场景输入\n天气/场合/风格"),(740,240,1000,370,"衣橱检索与\n规则过滤"),(1090,240,1350,370,"LLM 生成\n搭配方案"),(1440,240,1730,370,"展示、收藏\n与反馈")]
    for x1,y1,x2,y2,label in nodes:
        d.rounded_rectangle((x1,y1,x2,y2), radius=18, fill="#F5F7FA", outline="#34495E", width=4)
        for j, line in enumerate(label.split("\n")):
            box=d.textbbox((0,0),line,font=f(31)); w=box[2]-box[0]
            d.text(((x1+x2-w)//2, y1+30+j*42), line, fill="#17212B", font=f(31))
    for a,b in zip(nodes,nodes[1:]):
        d.line((a[2]+8,305,b[0]-15,305), fill="#557A95", width=5)
        d.polygon([(b[0]-15,305),(b[0]-35,293),(b[0]-35,317)], fill="#557A95")
    d.text((70,65), "智能穿搭推荐核心业务流程", fill="#17212B", font=f(48))
    d.text((70,135), "结构化输入 -> 可解释检索 -> 受约束的模型生成 -> 用户反馈闭环", fill="#52616B", font=f(25))
    im.save(path)

def diagram_arch(path):
    im = Image.new("RGB", (1800, 1050), "white")
    d = ImageDraw.Draw(im)
    d.text((70,45), "系统总体架构", fill="#17212B", font=f(48))
    layers=[("表现层",["Vue 3 + Vite","Lucide Vue 图标","业务页面与弹窗"],"#EEF4F7"),("业务服务层",["Spring Boot 3","Spring Security + Session","Spring JDBC / 业务编排"],"#F6F2EA"),("智能推荐层",["场景解析与提示词模板","衣橱检索与规则过滤","Qwen 兼容 LLM 接口"],"#F2F0F6"),("数据与基础设施层",["PostgreSQL 16","MinIO 对象存储","Caffeine 进程内缓存"],"#EEF5EF")]
    y=145
    for title,items,color in layers:
        d.rounded_rectangle((90,y,1710,y+170), radius=18, fill=color, outline="#596D7C", width=3)
        d.text((125,y+60),title,fill="#17212B",font=f(31))
        x=410
        for item in items:
            d.rounded_rectangle((x,y+42,x+360,y+128),radius=12,fill="white",outline="#9AA7B1",width=2)
            box=d.textbbox((0,0),item,font=f(23)); w=box[2]-box[0]
            d.text((x+(360-w)//2,y+71),item,fill="#273746",font=f(23))
            x+=405
        y+=210
    im.save(path)

def diagram_er(path):
    im=Image.new("RGB",(1800,980),"white"); d=ImageDraw.Draw(im)
    d.text((65,42),"核心数据实体关系",fill="#17212B",font=f(48))
    boxes=[(90,150,390,320,"app_users\n用户与账户"),(525,150,850,320,"recommendations\n推荐记录"),(960,150,1260,320,"recommendation_feedback\n收藏与反馈"),(90,480,390,650,"wardrobe_items\n单件衣物"),(525,480,850,650,"recommendation_items\n推荐明细"),(90,810,390,980,"style_profiles\n个人风格档案")]
    for x1,y1,x2,y2,label in boxes:
        d.rounded_rectangle((x1,y1,x2,y2),radius=15,fill="#F7F8FA",outline="#455A64",width=3)
        lines=label.split("\n")
        for j,line in enumerate(lines):
            font=f(22) if j==0 else f(27)
            bb=d.textbbox((0,0),line,font=font); d.text(((x1+x2-(bb[2]-bb[0]))//2,y1+55+j*43),line,fill="#17212B",font=font)
    links=[((240,320),(240,480),"1:N"),((390,240),(525,240),"1:N"),((690,320),(690,480),"1:N"),((850,240),(960,240),"1:N"),((390,570),(525,570),"1:N"),((240,650),(240,810),"1:1")]
    for (a,b,label) in links:
        d.line((*a,*b),fill="#617D8A",width=4); d.text(((a[0]+b[0])//2-20,(a[1]+b[1])//2-40),label,fill="#617D8A",font=f(20))
    im.save(path)

def diagram_ui(path):
    im=Image.new("RGB",(1800,950),"#FBFAF8"); d=ImageDraw.Draw(im)
    d.text((75,55),"知己 / 钟表式功能切换",fill="#17212B",font=f(46))
    # Labels are fixed like clock-face markers. Only the two hands rotate with the active route.
    cx,cy=865,715
    labels=[("知己",805,165,"#D2CEC8",False),("衣橱",320,370,"#D2CEC8",False),("风潮",175,655,"#D2CEC8",False),("档案",1190,655,"#D2CEC8",False),("搭配",1195,360,"#1A1715",True),("设定",835,760,"#D2CEC8",False)]
    for lab,x,y,col,active in labels:
        d.text((x,y),lab,fill=col,font=editorial_f(56 if active else 46, active))
    d.line((cx,cy,1085,475),fill="#9CA0A1",width=4)
    d.line((cx,cy,1360,390),fill="#9CA0A1",width=4)
    d.ellipse((cx-10,cy-10,cx+10,cy+10),fill="#333333")
    im.save(path)

def build():
    for fn, maker in [("flow.png",diagram_flow),("architecture.png",diagram_arch),("er.png",diagram_er),("ui.png",diagram_ui)]: maker(ASSETS/fn)
    doc=Document()
    sec=doc.sections[0]
    sec.page_width=Cm(21); sec.page_height=Cm(29.7)
    sec.left_margin=sec.right_margin=Cm(2.5); sec.top_margin=Cm(3.2); sec.bottom_margin=Cm(2.5)
    styles=doc.styles
    styles["Normal"].font.name="宋体"; styles["Normal"]._element.rPr.rFonts.set(qn("w:eastAsia"),"宋体"); styles["Normal"].font.size=Pt(10.5)
    if "BodyText" not in styles: styles.add_style("BodyText",1)
    styles["BodyText"].font.name="宋体"; styles["BodyText"]._element.rPr.rFonts.set(qn("w:eastAsia"),"宋体")
    for name in ("Heading 1","Heading 2","Heading 3"):
        styles[name].font.name="黑体"; styles[name]._element.rPr.rFonts.set(qn("w:eastAsia"),"黑体")
    footer=sec.footer.paragraphs[0]; footer.alignment=WD_ALIGN_PARAGRAPH.CENTER; set_font(footer.add_run("第 "),"宋体",9); add_page_field(footer); set_font(footer.add_run(" 页"),"宋体",9)
    # Cover
    for _ in range(4): doc.add_paragraph()
    p=doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER; r=p.add_run("湖南科技职业学院"); set_font(r,"黑体",24,True)
    p=doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER; r=p.add_run("2026届学生毕业设计成果"); set_font(r,"黑体",25,True)
    doc.add_paragraph(); doc.add_paragraph()
    p=doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER; r=p.add_run("知己 - 基于大语言模型的智能穿搭推荐系统"); set_font(r,"黑体",23,True)
    p=doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER; r=p.add_run("（企业软件方向）"); set_font(r,"宋体",14)
    for _ in range(4): doc.add_paragraph()
    meta=[("学生姓名","【待填写】"),("学    号","【待填写】"),("二级学院","软件学院"),("专业班级","【待填写】"),("指导教师","【待填写】"),("完成日期","2026年  月  日")]
    t=doc.add_table(rows=len(meta),cols=2); t.alignment=WD_TABLE_ALIGNMENT.CENTER; set_table_widths(t,[4,9])
    for row,(k,v) in zip(t.rows,meta): set_cell(row.cells[0],k,True,True,11); set_cell(row.cells[1],v,False,True,11)
    doc.add_page_break()
    # statement
    p=doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER; r=p.add_run("毕业设计真实性承诺及指导教师声明"); set_font(r,"黑体",18,True)
    text(doc,"本人郑重声明：本毕业设计在指导教师指导下独立完成。文档中的技术路线、设计说明、引用资料均应在提交前由本人结合实际实现与测试记录复核；未注明的内容不应作为已完成事实使用。",False)
    text(doc,"学生签名：____________________      指导教师签名：____________________",False)
    text(doc,"日期：2026年____月____日",False)
    doc.add_page_break()
    p=doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER; r=p.add_run("目  录"); set_font(r,"黑体",18,True)
    p=doc.add_paragraph(); add_toc_field(p)
    heading(doc,"1 设计任务",1)
    heading(doc,"1.1 项目背景",2)
    text(doc,"服饰搭配通常需要同时考虑天气、场合、个人风格、已有衣物和颜色协调等因素。传统电商推荐多以商品相似度或热门程度为依据，难以围绕用户已有衣橱给出可执行的成套方案。大语言模型具备自然语言理解、约束整合与解释生成能力，可将用户的场景描述转化为结构化搭配条件，并输出包含单品、配色、理由和替代方案的推荐结果。")
    text(doc,"本项目拟设计并实现一个面向普通用户的智能穿搭推荐系统。系统以用户衣橱为主要数据来源，结合天气和场合信息，通过检索、规则校验与大语言模型协同生成搭配方案。项目的根本目标不是生成泛化的时尚文案，而是在用户可选衣物范围内给出可追溯、可修改、可收藏的推荐。")
    heading(doc,"1.2 项目范围",2)
    text(doc,"系统面向注册用户。前台围绕一次完整的“录入衣物 - 描述场景 - 获得方案 - 反馈优化”闭环设计。第一期不包含内容审核后台、在线交易、虚拟试衣和社交发布等高成本功能。")
    for title,body in [
        ("1.2.1 用户认证与偏好管理","支持账号注册、登录、服务端 Session 会话校验和个人偏好维护。偏好包含常用场合、风格偏好、色彩倾向和配色建议；敏感认证信息采用 BCrypt 不可逆密码散列保存，浏览器只持有 HttpOnly Session Cookie，写请求通过 CSRF Token 保护。"),
        ("1.2.2 我的衣橱管理","用户可上传衣物图片，维护名称、品类、颜色、风格和可穿状态等标签，并按品类和颜色筛选。图片文件仅保存 MinIO 私有对象存储地址，业务数据库保存元数据；识别失败或未启用识别时记录进入待人工确认状态。"),
        ("1.2.3 智能搭配推荐","用户输入城市、出行场合和可选风格要求后，系统读取天气与个人衣橱，使用规则先过滤明显不适配组合，再请求大语言模型生成结构化方案。结果包括推荐单品、推荐理由和摘要；模型结果必须通过字段、数量、所属衣橱和类别多样性校验，失败时回退到明确标记的规则引擎。"),
        ("1.2.4 搭配收藏与反馈","用户可收藏方案、提交满意度评分与简短反馈，并查看带分页的历史推荐。反馈用于调整后续排序，不直接把用户原始文本作为模型长期训练数据。"),
        ("1.2.5 风潮趋势聚合","风潮页面优先展示已配置的授权 JSON 趋势源，页面区分“全局风潮”与“我的风格”两个区域。全局风潮只聚合标题或话题、热度、原始链接和采集时间；后端仅通过配置的授权数据源获取信息，不绕过登录、验证码、访问限制或反爬措施。未配置或数据不合规时返回明确标注的开发样本，不冒充实时平台热度。"),
        ("1.2.6 个人风格分析","用户完成基础信息、风格偏好、色彩倾向和衣橱标签后，系统使用当前已实现的确定性 development fallback 生成并持久化结构化个人风格档案。档案包含推荐风格、可尝试风格、适配颜色、推荐单品和简短理由；风潮页面读取该档案并与全局趋势分区展示，不将全局热度直接视为个人偏好。百炼风格分析属于后续生产化扩展，不作为当前已实现能力。")]:
        heading(doc,title,3); text(doc,body)
    heading(doc,"1.3 项目风险分析",2)
    text(doc,"项目风险主要集中在模型输出不稳定、衣物标签质量、第三方模型接口可用性以及个人信息保护。系统采用“结构化输出约束 + 业务规则二次校验 + 降级提示”的方式控制风险。")
    caption(doc,"表1.1 项目风险分析表")
    add_table(doc,["序号","风险","概率","影响","应对措施"],[
        ["1","模型返回非 JSON 或遗漏单品","中","高","限定 JSON Schema，解析失败时返回明确标记的规则引擎降级结果。"],
        ["2","衣橱为空或标签缺失","高","中","要求至少两件不同类别且已完善的衣物；条件不满足时返回明确错误而非伪造结果。"],
        ["3","LLM 接口超时或限流","中","高","设置连接/读取超时；超时或响应不合规时回退到规则引擎，不产生成功记录。"],
        ["4","图片与隐私数据泄露","低","高","Session 鉴权、对象存储私有桶、图片按用户隔离、最小化日志和删除后异步清理。"],
        ["5","功能范围扩张","中","中","以衣橱和推荐闭环为验收边界，虚拟试衣等功能列为后续迭代。"],
        ["6","趋势源不可用或数据不合规","中","中","整批严格校验，失败时返回明确标注的开发样本，不把部分脏数据标记为实时趋势。"]],[1,4,1.5,1.5,8])
    heading(doc,"1.4 任务分配",2)
    text(doc,"本项目按个人毕业设计组织。需求分析、原型设计、前后端开发、测试与文档由学生独立完成；指导教师负责技术路线与阶段成果审核。")
    caption(doc,"表1.2 任务分配表")
    add_table(doc,["阶段","主要任务","责任人","交付物"],[
        ["需求分析","用户场景、范围、用例和原型","学生","需求与设计说明"],["概要设计","架构、数据库、接口与提示词约束","学生","技术方案"],["详细实现","Java 后端、Vue 3 前端、模型接入","学生","可运行系统"],["测试验收","单元、接口和核心链路测试","学生","测试记录"],["过程指导","方案审阅与质量把关","指导教师","指导意见"]],[2.2,5.5,2.5,4.2])
    heading(doc,"1.5 项目所需资源",2)
    caption(doc,"表1.3 资源需求表")
    add_table(doc,["资源","版本或规格","用途"],[
        ["JDK","17 LTS","编译和运行 Spring Boot 服务"],["Node.js","20 LTS","Vue 3 前端构建"],["PostgreSQL","16","业务数据持久化（Flyway 管理迁移）"],["MinIO","开源 S3 兼容对象存储","私有桶保存衣物图片"],["Caffeine","进程内缓存","天气与趋势结果缓存"],["Docker Compose","v2","本地一致化部署"],["wttr.in / Open-Meteo","开源天气接口，无需 Key","按城市获取实时天气，辅助搭配规则过滤"],["阿里云百炼通义千问 API","由 DASHSCOPE_API_KEY 配置","个人风格分析与搭配文本生成"]],[3.3,4.2,6.9])
    heading(doc,"2 设计思路与技术方案",1)
    text(doc,"系统采用前后端分离架构。前端负责场景表单、衣橱维护和结果呈现；后端负责鉴权、数据管理、检索编排、规则校验和模型调用。模型只输出受约束的推荐候选，最终展示的方案必须通过数量、类别多样性和用户衣橱归属校验。")
    heading(doc,"2.1 系统核心业务流程图",2)
    doc.add_picture(str(ASSETS/"flow.png"),width=Cm(16)); doc.paragraphs[-1].alignment=WD_ALIGN_PARAGRAPH.CENTER; caption(doc,"图2.1 智能搭配推荐核心业务流程图")
    heading(doc,"2.2 系统用例图",2)
    text(doc,"普通用户可注册登录、维护衣橱、上传图片、提交场景、获取推荐、收藏方案和提交反馈。推荐用例依赖用户鉴权、衣橱候选检索和模型服务三个前置能力。")
    caption(doc,"表2.1 系统主要用例")
    add_table(doc,["角色","主要用例","说明"],[
        ["普通用户","注册登录、偏好设置、衣橱管理","维护个人可用于推荐的衣物与偏好。"],["普通用户","场景推荐、收藏反馈、历史查看","获取并调整搭配方案，形成个人历史。"]],[2.2,5.2,7])
    heading(doc,"2.3 用例分析",2)
    for index,(code,name,desc,pre,flow,post) in enumerate([
        ("UC-01","用户登录认证","用户使用账号和密码进入系统并获得可访问个人数据的会话。","用户已注册且账号未被禁用。","输入账号密码；后端校验 BCrypt 密码散列；创建服务端 Session；浏览器保存 HttpOnly Cookie。","用户进入首页，可访问自己的衣橱和历史方案。"),
        ("UC-02","新增衣物","用户上传衣物图片并维护属性，形成推荐候选。","用户已登录，图片格式符合限制。","选择图片；填写名称、品类、颜色等标签；后端校验；保存 MinIO 图片地址与衣物记录。","衣物出现在个人衣橱列表。"),
        ("UC-03","生成智能搭配","用户提交场景和约束，系统返回成套搭配方案。","用户已登录，衣橱至少有可选衣物。","解析场景；读取天气与衣橱；规则过滤；请求模型；解析 JSON；校验单品归属与类别多样性；保存方案。","用户看到可解释方案，可收藏或反馈。")], start=1):
        heading(doc,f"2.3.{index} {name}",3); caption(doc,f"表2.{index} {name}用例描述")
        add_table(doc,["内容","说明"],[["用例编号",code],["用例名称",name],["用例说明",desc],["前置条件",pre],["基本流程",flow],["异常路径","身份失效时返回 401；参数不完整时返回 400；模型服务不可用时触发降级提示。"],["后置条件",post]],[3.2,11])
    heading(doc,"2.4 技术方案",2)
    text(doc,"前端采用 Vue 3、Vite 与 Lucide Vue 图标库；后端采用 Java 17、Spring Boot 3.4、Spring Security、Spring JDBC、Validation 与 Flyway。模型能力通过阿里云百炼通义千问 API 接入，天气能力由服务层调用 wttr.in（失败后回退 Open-Meteo）并统一转换为业务天气对象；风潮数据通过可配置的授权 JSON 趋势源适配器获取并严格校验。数据层使用 PostgreSQL 16 存储业务数据，MinIO 保存私有图片对象，Caffeine 提供进程内缓存。部署时使用 Docker Compose 编排前端静态资源、后端服务、数据库与对象存储。")
    heading(doc,"2.4.1 系统架构图",3)
    doc.add_picture(str(ASSETS/"architecture.png"),width=Cm(16)); doc.paragraphs[-1].alignment=WD_ALIGN_PARAGRAPH.CENTER; caption(doc,"图2.2 系统总体架构图")
    heading(doc,"2.4.2 数据库命名规则",3)
    text(doc,"数据库表名使用小写下划线，例如 wardrobe_items、recommendations；主键统一为 id；外键字段采用 xxx_id；创建和更新时间采用 created_at、updated_at。业务表不保存明文密码、模型密钥或可直接识别用户的原始敏感信息。")
    heading(doc,"2.4.3 类命名规则",3)
    text(doc,"Java 类名使用 UpperCamelCase，例如 RecommendationService、WardrobeController、WardrobeItemRequest。实体、DTO、Service 和 Repository 使用后缀区分职责，避免以模糊缩写命名。")
    heading(doc,"2.4.4 方法、参数、成员变量、局部变量命名规则",3)
    text(doc,"方法名、参数名、成员变量和局部变量统一使用 lowerCamelCase，例如 generateOutfitPlan、userId、sceneRequest。布尔变量以 is、has 或 can 开头；不使用单字符业务变量，不在方法中混合数据库访问、模型调用和响应组装。")
    heading(doc,"2.4.5 包名结构",3)
    text(doc,"后端包结构划分为 com.fashion.recommendation 下的 auth、config、common、recognition、recommendation、security、storage、style、trend、wardrobe、weather。前端按 views、composables 等划分，页面不直接拼接接口地址。")
    heading(doc,"3 设计内容（过程）与说明",1)
    heading(doc,"3.1 数据库设计",2)
    text(doc,"数据库以“用户 - 衣物 - 推荐方案”为核心主线。app_users 与 app_authorities 保存认证账号；app_users 与 wardrobe_items 为一对多关系；recommendations 记录一次完整推荐，recommendation_items 保存方案明细快照，recommendation_feedback 保存评分与反馈；style_profiles 保存个人风格档案；image_cleanup_tasks 保存图片删除后的异步清理任务。结构由 Flyway 版本迁移管理：V1 为基础表与兼容补列，V2 增加图片清理任务表，V3 为 recommendations 增加审计元数据列（model_name、prompt_version、provider_call_id、三类 token、generation_latency_ms、fallback_reason）。设计中将高频查询字段建立复合索引，并为用户私有数据增加 user_id 过滤条件。")
    heading(doc,"3.1.1 数据库物理模型",3)
    doc.add_picture(str(ASSETS/"er.png"),width=Cm(16)); doc.paragraphs[-1].alignment=WD_ALIGN_PARAGRAPH.CENTER; caption(doc,"图3.1 核心实体关系图")
    heading(doc,"3.1.2 数据表设计",3)
    for no,name,rows in [
        ("3.1","app_users 用户表",[["username","varchar(32)","否","用户主键"],["password_hash","varchar(100)","否","BCrypt 密文"],["enabled","boolean","否","账号是否启用"]]),
        ("3.2","wardrobe_items 衣物表",[["id","bigint","否","衣物主键"],["user_id","varchar(100)","否","所属用户"],["name","varchar(120)","否","单品名称"],["category","varchar(40)","否","上装/下装等"],["color","varchar(40)","否","颜色"],["image_object_key","varchar(300)","是","MinIO 对象键"],["recognition_status","varchar(32)","否","识别状态"]]),
        ("3.3","recommendations 推荐表",[["id","bigint","否","推荐主键"],["user_id","varchar(100)","否","所属用户"],["occasion","varchar(80)","否","场合"],["city","varchar(80)","否","城市"],["summary","varchar(500)","否","推荐摘要"],["reason","varchar(1200)","否","推荐理由"],["engine","varchar(80)","否","llm 或 development-rule-v1"],["fallback_reason","varchar(64)","是","规则降级原因"],["saved","boolean","否","是否收藏"],["created_at","timestamp","否","生成时间"]])]:
        text(doc,f"（{no[-1]}）{name}",False); caption(doc,f"表{no} {name}")
        add_table(doc,["字段名","类型","可空","说明"],rows,[3.2,3.2,2,5.8])
    heading(doc,"3.2 界面设计",2)
    text(doc,"“知己”前台计划实现登录注册页、搭配页、衣橱页、风潮页、方案档案页与设定页等页面。首页以钟表式功能切换替代常规顶部菜单：衣橱、风潮、搭配、档案和设定以短词固定分布，当前功能使用深色文字与两根指针表示；账号和通知等低频操作保留在右上角。")
    heading(doc,"3.2.1 用户登录页面",3)
    text(doc,"登录页面提供账号、密码和登录按钮，错误凭证只提示认证失败，不区分账号不存在或密码错误。登录成功后创建服务端 Session，浏览器保存 HttpOnly Cookie，并跳转到首页。")
    heading(doc,"3.2.2 用户注册页面",3)
    text(doc,"注册页面要求填写用户名、密码与确认密码，并完成基础格式校验。后端对用户名设置唯一约束，对密码进行 BCrypt 散列后保存，禁止在前端或日志中记录明文。")
    heading(doc,"3.2.3 衣橱页面",3)
    text(doc,"衣橱页面按品类、颜色、季节和状态提供筛选，卡片展示衣物图片、标签和编辑入口。用户上传衣物后必须完成最少的品类与颜色标注，避免未经标注的数据直接进入推荐候选集。")
    heading(doc,"3.2.4 首页推荐与功能切换页面",3)
    doc.add_picture(str(ASSETS/"ui.png"),width=Cm(16)); doc.paragraphs[-1].alignment=WD_ALIGN_PARAGRAPH.CENTER; caption(doc,"图3.2 首页放射式功能切换原型")
    heading(doc,"3.2.5 档案页面",3); text(doc,"档案页展示已保存的搭配方案，支持按时间和场景筛选，并允许复用场景重新生成。页面不展示模型内部思维链，只展示面向用户的简短理由与规则命中说明。")
    heading(doc,"3.2.6 设定页面",3); text(doc,"设定页用于维护偏好、常用场景和隐私选项；用户可管理收藏反馈，并随时删除自己的反馈数据。")
    heading(doc,"3.2.7 风潮趋势页面",3)
    text(doc,"风潮页面分为“全局风潮”和“我的风格”两个区域。全局风潮优先展示已配置的授权 JSON 趋势源，按来源平台和主题标签展示趋势卡片，卡片包括标题或话题、热度、来源、发布时间或采集时间和原文跳转链接；页面必须标注“聚合自公开来源”，不复制受限全文、不展示个人账号资料，也不将其他平台内容伪装为本站原创。我的风格区域读取已保存的本地确定性风格档案，展示当前推荐风格、推荐搭配、建议单品、颜色搭配和可尝试风格。趋势源不可用或数据不合规时，返回明确标注的开发样本，不把内置样本冒充实时平台数据。")
    heading(doc,"3.3 类设计",2)
    text(doc,"后端按控制层、应用服务层、领域对象与基础设施层分离。RecommendationService 负责协调场景解析、天气读取、候选检索、规则校验和模型调用；WardrobeService 只负责衣物业务；LlmRecommendationClient 隔离具体模型供应商差异。这样的划分避免让控制器承担业务流程，也使模型服务可替换。")
    heading(doc,"3.3.1 衣橱管理业务类设计",3)
    text(doc,"衣橱管理由 WardrobeController、WardrobeService、WardrobeRepository 与 WardrobeItem 构成。服务层完成归属校验、识别状态转换和 MinIO 图片地址保存，仓储仅负责持久化访问。")
    heading(doc,"3.3.2 智能推荐业务类设计",3)
    text(doc,"智能推荐由 RecommendationController、RecommendationService、RecommendationRepository、LlmRecommendationClient 与 RecommendationAudit 协作完成。生成前必须取得候选衣物，生成后必须再次校验衣物 ID、数量与类别多样性；每次生成都会持久化审计元数据（provider call ID、模型名、prompt 版本、三类 token 与降级原因），并通过响应中的嵌套 generationAudit 字段返回，规则降级不伪造模型元数据。")
    caption(doc,"表3.4 核心类职责说明")
    add_table(doc,["类/组件","职责","关键约束"],[
        ["RecommendationController","接收推荐请求并返回统一响应","不直接访问数据库或拼接提示词。"],["RecommendationService","编排推荐流程、校验与结果保存","必须校验模型返回的衣物 ID、数量与类别多样性；记录审计元数据。"],["WardrobeRepository","按用户查询候选衣物","查询始终附加用户条件。"],["LlmRecommendationClient","封装百炼通义千问兼容 API","超时、限流与异常统一转换为领域错误。"],["TrendService / ConfiguredJsonTrendSourceAdapter","读取授权 JSON 趋势源并严格校验","整批校验失败时返回明确标注的开发样本。"],["PersonalStyleProfileService","根据用户基础信息和手动衣橱标签生成、缓存风格档案","仅输出固定 Schema；输入变化后使旧档案失效。"]],[4,5.2,5])
    heading(doc,"3.3.3 天气数据服务设计",3)
    text(doc,"天气数据优先使用开源项目 wttr.in（https://github.com/chubin/wttr.in）的公开接口，失败后回退到 Open-Meteo（https://github.com/open-meteo/open-meteo）的地理编码与天气接口。毕业设计为非商业使用，不需要配置 API Key；项目不复制、修改或自建其服务端。后端不得让前端直接调用第三方接口，而是由 WeatherService 统一处理地址、超时、字段映射和错误转换，避免第三方响应结构泄漏到业务层。")
    caption(doc,"表3.5 天气服务调用契约")
    add_table(doc,["步骤","第三方地址","入参","处理结果"],[
        ["主查询","https://wttr.in/{city}","format=j1","成功时返回温度、体感、降水、天气码与风速；失败进入回退。"],
        ["城市解析","https://geocoding-api.open-meteo.com/v1/search","name, count=1, language=zh, format=json","取首个结果的 latitude、longitude；无结果返回 WEATHER_LOCATION_NOT_FOUND。"],
        ["天气查询","https://api.open-meteo.com/v1/forecast","latitude, longitude, current=..., timezone=Asia/Shanghai","映射为温度、体感温度、降水、天气现象和风速。"]],[2,5.6,4.3,7.3])
    text(doc,"WeatherSnapshot 字段为 city、observedAt、temperatureC、apparentTemperatureC、precipitationMm、weatherCode、windSpeedKmh 和 source。weatherCode 保留 WMO 数值供规则层判断；前端展示文案由后端字典转换，不能让模型自行猜测天气含义。搭配规则至少使用体感温度、降水和风速：低温提高保暖层级，降水提示防水外层或雨具，较大风速提示防风外套。")
    text(doc,"查询成功结果缓存 15 分钟，缓存键包含标准化城市名。连接与读取超时均设置为 3 秒，失败后回退到备用 provider；仍失败则返回 WEATHER_SERVICE_UNAVAILABLE，不将旧天气伪装成实时数据。离线答辩演示默认关闭：仅当两个真实 provider 都失败且请求城市与配置城市一致时，才返回静态快照，source=configured-demo，前端明确标注“配置演示天气/非实时”，绝不冒充实时天气；城市未找到（NOT_FOUND）仍返回 404，不被静态快照掩盖。")
    heading(doc,"3.3.4 风潮趋势聚合设计",3)
    text(doc,"TrendService 优先调用 ConfiguredJsonTrendSourceAdapter 读取配置的授权 JSON 趋势源。根对象、字段集合、条目数量、重复 ID、标签、热度、ISO-8601 时间和 HTTP(S) URL 都经过整批严格校验；成功结果在进程内按 TREND_CACHE_TTL 缓存。未配置、请求失败或任一条目不合规时，回退到三条明确标注的开发样本并设置 demoMode=true。")
    text(doc,"有效实时源经过页面筛选后即使结果为空，仍保持 demoMode=false，避免把“没有匹配项”误报为数据源故障。项目不会在没有授权时抓取第三方平台或编造实时热度。采集日志只记录来源、时间、HTTP 状态和条数，不记录用户个人资料或完整正文。")
    heading(doc,"3.3.5 个人风格档案设计",3)
    text(doc,"PersonalStyleProfileService 在用户首次完善基础信息或主动刷新时调用百炼通义千问 API。输入仅包含用户主动填写的风格偏好、色彩倾向、常用场合、预算敏感度和手动衣橱标签；输出必须满足 StyleProfileSchema，包含 styleTags、tryStyleTags、colorSuggestions、itemSuggestions 和 reasonSummary。模型不得编造用户未提供的体型、身份或消费能力，也不得将风潮热度当作用户偏好。")
    text(doc,"当前实现把用户资料和偏好列表归一化后生成确定性档案并保存，页面读取已保存档案，不在每次进入风潮页时调用模型。档案状态来自服务端保存结果；未来接入外部模型时，才需要增加 sourceProfileHash、超时、非法 JSON、旧档案保留和 stale 标记等治理逻辑。")
    heading(doc,"3.3.6 全系统后端代码包图",3)
    text(doc,"系统代码包以 Controller 接收 HTTP 请求，Service 承担业务编排，Repository 访问 PostgreSQL，recognition/recommendation 包封装模型能力，security 包维护认证授权。包之间通过接口和 DTO 传递数据，避免前端对象直接映射数据库实体。")
    heading(doc,"3.4 顺序图设计",2)
    text(doc,"生成推荐时，前端先提交场景与用户约束，后端完成身份校验；检索组件获取候选衣物，规则组件剔除不可用候选；模型适配器请求 LLM 返回 JSON；服务层对 JSON 做结构校验和所有权校验，成功后持久化方案并返回前端。任何一个外部调用失败均不得写入“已完成”的方案记录。")
    heading(doc,"3.4.1 用户登录认证顺序图",3)
    text(doc,"前端提交账号和密码，Spring Security 表单登录校验 BCrypt 密码散列，认证成功后创建服务端 Session 并返回用户信息；失败时不创建会话，也不暴露用户是否存在。写请求由 CSRF Token 保护。")
    heading(doc,"3.4.2 新增衣物顺序图",3)
    text(doc,"前端上传图片和衣物标签，WardrobeController 校验 Session 和文件类型，WardrobeService 保存 MinIO 对象地址及元数据，随后返回衣物详情。未勾选 AI 识别时不调用视觉服务。")
    heading(doc,"3.4.3 智能推荐顺序图",3)
    caption(doc,"表3.6 智能推荐顺序说明")
    add_table(doc,["顺序","参与者","操作"],[
        ["1","Vue 前端 -> RecommendationController","提交 occasion、city、styleHint 和约束。"],["2","Controller -> RecommendationService","校验 Session 与请求参数。"],["3","Service -> WardrobeRepository","检索当前用户可穿衣物。"],["4","Service -> 规则过滤","按类别顺序与历史反馈选出候选。"],["5","Service -> LlmRecommendationClient","发送受约束的数据上下文与 JSON Schema。"],["6","Service -> RecommendationRepository","校验并保存方案与明细，记录审计元数据。"],["7","Service -> Vue 前端","返回方案、理由、天气和生成来源。"]],[1.2,4.6,8.4])
    heading(doc,"3.4.4 收藏与反馈顺序图",3)
    text(doc,"用户对方案点击收藏或评分，前端提交推荐 ID 与评价，后端先校验方案归属，再写入 recommendation_feedback 记录。若方案不存在或属于其他用户，接口返回无权访问，不能以评分接口探测他人数据。")
    heading(doc,"3.5 API 接口设计",2)
    text(doc,"接口遵循 REST 风格，统一前缀为 /api/v1，使用 HTTPS 传输。成功响应返回 code、message 和 data；业务异常使用可枚举错误码；文件上传接口限制 MIME 类型、尺寸和鉴权。")
    caption(doc,"表3.7 核心接口描述")
    add_table(doc,["接口","方法","地址","关键入参","说明"],[
        ["登录","POST","/api/v1/auth/login","username, password","表单登录，创建服务端 Session。"],["注册","POST","/api/v1/auth/register","username, password","创建本地账号并返回用户信息。"],["新增衣物","POST","/api/v1/me/wardrobe","name, category, color","保存用户衣物及标签。"],["上传衣物图片","POST","/api/v1/me/wardrobe/upload","image, allowAiRecognition","multipart 上传图片到 MinIO 私有桶。"],["查询衣橱","GET","/api/v1/me/wardrobe","无","仅返回当前用户数据。"],["查询天气","GET","/api/v1/weather/current","city","后端调用 wttr.in/Open-Meteo，返回 WeatherSnapshot。"],["查询风潮","GET","/api/v1/trends","platform, topic","返回授权趋势源或明确标注的开发样本。"],["查询个人风格","GET","/api/v1/me/style-profile","无","返回已保存的确定性风格档案及状态。"],["刷新个人风格","POST","/api/v1/me/style-profile/refresh","无","按用户输入重新生成并保存本地 development fallback 档案。"],["生成推荐","POST","/api/v1/recommendations","occasion, city, styleHint","生成并保存结构化方案。"],["保存推荐","POST","/api/v1/me/recommendations/{id}/save","无","把推荐标记为已收藏。"],["提交反馈","POST","/api/v1/me/recommendations/{id}/feedback","rating, comment","保存个人反馈。"],["查询历史","GET","/api/v1/me/recommendations","page, size","分页返回当前用户推荐历史。"]],[2.4,1.5,3.4,4.3,3.8])
    heading(doc,"3.5.1 用户登录与注册模块",3)
    text(doc,"登录接口为 POST /api/v1/auth/login（Spring Security 表单登录），注册接口为 POST /api/v1/auth/register（JSON）。密码只可通过 HTTPS 传输，响应中不得返回 passwordHash。认证状态保存在服务端 Session，浏览器只持有 HttpOnly Cookie；写请求先取得 GET /api/v1/auth/csrf 的 CSRF Token 再随请求头发送。")
    heading(doc,"3.5.2 我的衣橱管理模块",3)
    text(doc,"衣物新增、修改、删除和查询接口统一使用 /api/v1/me/wardrobe，图片上传为 /api/v1/me/wardrobe/upload。所有查询都从认证上下文取得当前用户名，删除为物理删除并由后台调度器异步清理 MinIO 对象。")
    heading(doc,"3.5.3 智能推荐与反馈模块",3)
    text(doc,"推荐接口为 POST /api/v1/recommendations，保存为 POST /api/v1/me/recommendations/{id}/save，收藏与反馈为 POST /api/v1/me/recommendations/{id}/feedback，历史查询为 GET /api/v1/me/recommendations。推荐结果使用固定 JSON 结构返回，外部模型异常统一映射为可识别业务错误并回退到规则引擎。")
    heading(doc,"3.5.4 天气查询模块",3)
    text(doc,"天气查询接口为 GET /api/v1/weather/current?city={city}。响应 data 使用 WeatherSnapshot；city 为空时返回 400，城市无法解析时返回 404 和 WEATHER_LOCATION_NOT_FOUND，两个真实 provider 都不可用时返回 503 和 WEATHER_SERVICE_UNAVAILABLE。wttr.in 与 Open-Meteo 均未使用 API Key，因此不得在 .env、日志或接口响应中添加虚构的天气密钥字段。离线演示（默认关闭）仅在两个 provider 都失败且城市匹配配置城市时返回 source=configured-demo 的静态快照。")
    heading(doc,"3.5.5 风潮趋势模块",3)
    text(doc,"趋势查询接口为 GET /api/v1/trends?platform={platform}&topic={topic}。响应项包含 platform、title、topicTags、heatScore、publishedAt、fetchedAt、sourceUrl 和 demoMode，不返回受限全文或个人资料。platform 仅接受已配置来源；未配置、请求失败或数据不合规时返回明确标注的开发样本并设置 demoMode=true，不触发临时抓取，避免用户请求放大对第三方的访问压力。")
    heading(doc,"3.5.6 个人风格档案模块",3)
    text(doc,"GET /api/v1/me/style-profile 返回当前登录用户的已保存档案；POST /api/v1/me/style-profile/refresh 按用户输入重新生成并保存确定性 development fallback。响应包含 styleTags、tryStyleTags、colorSuggestions、itemSuggestions、reasonSummary、generatedAt 和 stale；当前 stale 固定为 false，未接入外部风格模型。")
    heading(doc,"3.6 项目测试",2)
    text(doc,"测试围绕真实业务约束设计，覆盖认证、跨用户隔离、CSRF、Flyway 迁移、LLM 结果校验与降级、推荐审计、图片删除清理、历史分页、输入长度、天气与离线天气降级、趋势源校验等边界。测试结论必须以当前执行记录为准：本轮主机回归已验证后端 Maven 测试与离线评估；Docker 未运行，因此前端 Playwright E2E 本轮标记为未执行，不把历史结果冒充为当前证据。")
    heading(doc,"3.6.1 后端自动化测试",3)
    caption(doc,"表3.8 后端测试结果（mvn test）")
    add_table(doc,["测试类","覆盖要点","测试数","结果"],[
        ["AuthenticationIntegrationTest","未认证 401、登录/退出会话、CSRF","2","通过"],["FlywayMigrationTest","旧库 baseline 升级、V2/V3 迁移、重复执行幂等","2","通过"],["BailianGarmentRecognitionServiceTest","识别结果解析与授权","1","通过"],["BailianRecommendationClientTest","JSON 解析、usage 解析、负 token 反例","12","通过"],["RecommendationControllerTest","推荐闭环、fallback、跨用户 404、分页、输入长度","19","通过"],["ConfiguredJsonTrendSourceAdapterTest","授权源整批校验、缓存","5","通过"],["TrendServiceTest","实时源优先、开发样本降级","2","通过"],["TrendControllerTest","趋势接口契约","1","通过"],["ImageCleanupSchedulerTest","删除任务调度与重试","2","通过"],["WeatherServiceTest","provider 降级、离线天气、配置校验","15","通过"]],[2.6,4.6,1.6,1.6])
    text(doc,"后端测试数量与结果以本轮 Surefire 报告为准；本轮执行记录为 62 项测试，0 失败、0 错误、0 跳过。若代码修复新增测试，应重新生成本文档后更新该数字。",False)
    heading(doc,"3.6.2 前端 E2E 测试",3)
    caption(doc,"表3.9 前端 E2E 测试结果（npm run test:e2e）")
    add_table(doc,["E2E 用例","覆盖要点","结果"],[
        ["添加衣物、生成推荐、保存、提交反馈","注册登录 -> 新增衣物 -> 生成推荐 -> 收藏 -> 反馈持久化复核","本轮未执行"],["未认证拒绝与会话清理","匿名访问个人接口 401；退出后清空会话","本轮未执行"],["无 AI 同意上传","未勾选识别时上传并补齐必填字段","本轮未执行"],["移动端视口","390x844 布局，登录、导航、上传弹窗不溢出","本轮未执行"]],[4.2,8.2,1.6])
    text(doc,"本轮因 Docker Desktop 未运行未执行 Playwright；历史记录中曾有 4 项通过，但不作为本轮验收结论。",False)
    heading(doc,"3.6.3 测试覆盖的业务边界",3)
    text(doc,"推荐结果必须来自当前用户衣橱、ID 唯一且类别互异；LLM 结果非法时回退到明确标记的规则引擎，不将规则结果冒充模型结果。每次生成持久化审计元数据：合法 LLM 结果保存真实 provider 元数据（provider call ID、模型名、prompt 版本与三类 token），规则降级只保存稳定的枚举式 fallback 原因。图片删除采用事务内写清理任务、后台调度器重试的方式，避免删除记录与对象存储不一致。离线天气演示默认关闭，仅在两个真实 provider 都失败且城市匹配配置城市时返回静态快照，不冒充实时天气。")
    heading(doc,"4 设计总结",1)
    heading(doc,"4.1 部署手册",2)
    text(doc,"系统部署到云服务器，采用 Docker Compose 编排。部署前需准备域名或服务器 IP、HTTPS 证书、PostgreSQL 数据卷、MinIO 对象存储配置和阿里云百炼 API Key。所有密钥通过环境变量注入，不提交到代码仓库。")
    heading(doc,"4.1.1 初始化数据服务",3)
    bullet(doc,"创建 PostgreSQL 16 数据库 fashion_recommendation，由 Flyway 依次执行 V1（基础表）、V2（图片清理任务表）、V3（推荐审计列）迁移。")
    bullet(doc,"创建 MinIO 私有桶 garments-private，并配置仅由后端访问。")
    heading(doc,"4.1.2 部署应用并启动服务",3)
    bullet(doc,"在服务器目录中配置 .env：POSTGRES_DB、POSTGRES_USER、POSTGRES_PASSWORD、MINIO_ROOT_USER、MINIO_ROOT_PASSWORD、DASHSCOPE_API_KEY、BAILIAN_MODEL、SESSION_COOKIE_SECURE 等。wttr.in 与 Open-Meteo 默认调用不需要 WEATHER_API_KEY；如需替换天气服务，只新增 WEATHER_PRIMARY_BASE_URL 等配置，不改动推荐业务类。趋势源通过 TREND_JSON_URL 配置授权 JSON 地址，禁止配置用户账号、密码或绕过访问限制的参数。")
    bullet(doc,"执行 docker compose --profile app up --build -d，检查 postgres、minio 的健康检查与 backend 的 /actuator/health；frontend 通过 HTTP 页面和 API 代理可访问性验证。Compose 未为 frontend/backend 声明 healthcheck，不应把它们写成 healthy。")
    bullet(doc,"访问 /actuator/health 验证后端健康状态；未配置 LLM Key 时推荐接口明确使用规则引擎并返回 engine=development-rule-v1。")
    heading(doc,"4.1.3 访问程序",3)
    text(doc,"在可访问服务器的浏览器中打开 https://{domain}/。首次使用时注册账号，上传至少三件已标注衣物，再进入“开始搭配”提交场景。正式部署必须启用 HTTPS、反向代理限流和数据库定期备份。")
    heading(doc,"4.2 用户操作手册",2)
    heading(doc,"4.2.1 用户登录",3); text(doc,"打开系统首页后输入已注册账号和密码，点击登录。登录失败时按提示检查凭证；连续失败保护策略应由实际部署配置决定。")
    heading(doc,"4.2.2 用户注册",3); text(doc,"没有账号的用户进入注册页面，填写用户名、密码和确认密码。注册成功后可返回登录页进行身份认证；用户名重复时系统应给出明确提示。")
    heading(doc,"4.2.3 新建衣橱",3); text(doc,"登录后进入“我的衣橱”，点击新增衣物，上传清晰图片并填写品类、颜色和适用季节。只有状态为“可穿”的衣物会进入推荐候选。")
    heading(doc,"4.2.4 获取智能搭配",3); text(doc,"在首页选择“开始搭配”，填写通勤、约会、旅行等场景，可补充温度、风格和禁忌。点击生成后查看推荐单品及理由；若衣橱缺少关键单品，系统应提示补充而不是虚构已有衣物。")
    heading(doc,"4.2.5 收藏与反馈",3); text(doc,"对满意方案点击收藏，可对方案评分或添加简短反馈。用户可在“历史方案”中查看之前的推荐并复用场景。删除衣物后，历史方案保留文字记录，但不再将该衣物作为新的推荐候选。")
    heading(doc,"4.3 致谢",2)
    text(doc,"本毕业设计的完成离不开指导教师在选题、技术方案和文档规范方面的指导，也感谢在需求分析、测试反馈和资料查阅过程中提供帮助的老师与同学。通过本项目，进一步理解了前后端分离架构、数据建模、AI 服务编排和软件测试中“可验证性优先”的工程原则。")
    heading(doc,"参考文献",1)
    refs=[
        "[1] Spring. Spring Boot Reference Documentation 3.3 [EB/OL]. 2024.",
        "[2] Vue.js Team. Vue.js Guide: Introduction [EB/OL]. 2025.",
        "[3] PostgreSQL Global Development Group. PostgreSQL 16 Documentation [EB/OL]. 2023.",
        "[4] Bai J, Bai S, Chu Y, et al. Qwen Technical Report [EB/OL]. 2023.",
        "[5] Lewis P, Perez E, Piktus A, et al. Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks [C]. NeurIPS, 2020.",
        "[6] OWASP Foundation. Application Security Verification Standard 4.0.3 [EB/OL]. 2021."
    ]
    for ref in refs: text(doc,ref,False)
    heading(doc,"附 录",1)
    heading(doc,"附录 A 推荐结果 JSON 示例",2)
    p=doc.add_paragraph(); p.paragraph_format.left_indent=Cm(0.74); p.paragraph_format.line_spacing=1.2
    r=p.add_run('{\n  "summary": "通勤推荐：针织衫、西裤与乐福鞋的组合。",\n  "reason": "雾蓝针织衫与石墨灰西裤形成稳定对比，乐福鞋适合通勤。",\n  "itemIds": [22, 23, 24]\n}')
    set_font(r,"Consolas",9)
    heading(doc,"附录 B 数据与模型使用边界",2)
    text(doc,"系统仅将用户主动提交的衣物标签、场景和偏好用于当前推荐服务。生产环境中应取得用户授权、提供数据删除入口，并对第三方模型接口的请求内容执行最小化传输。模型输出仅作为搭配建议，不构成医疗、消费或身份判断结论。")
    settings=doc.settings.element
    update=OxmlElement("w:updateFields"); update.set(qn("w:val"),"true"); settings.append(update)
    doc.save(OUT)
    print(OUT)

if __name__ == "__main__": build()
