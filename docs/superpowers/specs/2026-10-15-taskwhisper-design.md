# TaskWhisper · 一句话待办 — 设计文档

日期：2026-10-15
定位：2027 届求职项目（Java 后端为主、全栈可用），替代旧项目 4Scar 的「Java 后端」格位。

## 1. 项目定位与目标

「一句话待办」任务管理系统：注册登录、任务增删改查、分类/优先级/状态管理、统计，核心卖点是 **AI 自然语言建任务** —— 输入「明天下午三点提醒我开会，高优先级」，由 DeepSeek 解析成结构化字段并落库。

一份代码、两种简历用法：
- **投后端**：Spring Boot + MyBatis-Plus + Spring Security + MySQL 的完整后端叙事；
- **投全栈**：再加上 Vue 3 + Element Plus 前端，与已有的 React 项目（迷你大富翁）形成「Vue/React 双框架」覆盖，命中 JD 中「Vue/React 二选一」的全部岗位。

硬约束（继承 4Scar 教训）：技术栈全部真实、可被面试官沿 pom.xml / 表结构 / 接口逐层追问；所有数字实测回填；不写没做的功能。

## 2. 技术栈（2026-10-15 经 4 线调研 + 4 线对抗核查定稿）

| 层 | 选择 | 关键依据（核查结论） |
|---|---|---|
| JDK | Eclipse Temurin 21 LTS（21.0.12） | Boot 4 基线 Java 17，21 向下全覆盖；虚拟线程是 21 独有简历亮点，已进入校招 JD 文本 |
| 后端框架 | Spring Boot 4.1.x（当前 4.1.1，构建时取最新补丁） | 3.5.x 免费维护已于 2026-06-30 结束，新项目选 3.5 = 从第一天选停维版本；4.x 已 GA 近一年 |
| 构建 | Maven 3.9.16（settings.xml 走阿里云镜像） | 国内主流 |
| ORM | MyBatis-Plus 3.5.17 + mybatis-plus-spring-boot4-starter + 显式引入 mybatis-plus-jsqlparser | 国内中厂事实标准；「2024 许可争议」经核查为谣言（全程 Apache-2.0）；分页插件自 3.5.9 拆包必须显式引入 |
| 数据库 | MySQL 8.0.46（本机便携版，utf8mb4） | 面试通用 |
| 鉴权 | Spring Security + JWT（自定义 OncePerRequestFilter + BCrypt + SecurityContextHolder） | 核查结论：手写过滤器不扣分，但 Spring Security 才能接住面试必挖的深问（token 刷新、双 token、注入防护） |
| AI | DeepSeek API（chat/completions，response_format=json_object） | 用户已有真实 Key 与接入经验；无 Key 时 503 诚实报错 |
| 接口文档 | Knife4j（与 Boot 4/Jackson 3 兼容性构建时验证，不兼容则退 springdoc-openapi） | 国内标配 |
| 前端 | Vue 3（组合式）+ Vite + Element Plus + Pinia + Vue Router 4 + Axios | 中小厂全栈 JD 最常见组合；用户已有 React 经验，形成双框架覆盖 |
| 测试 | JUnit 5 + MockMvc + H2（MySQL 兼容模式） | 全绿可复现，IDEA 一键跑 |

**面试话术要点（调研成果，沉淀进 README）**：
- 为什么 Boot 4 不是 3.5：3.5 OSS 停维（2026-06-30）；编程模型一致，差异在 Jackson 3（tools.jackson 包名）与模块化拆分；需准备 javax→jakarta、JDK 17 基线等 3.x 差异话题。
- 为什么 JDK 21：Boot 4 基线 17；21 覆盖 17 全部语法；项目实际开启虚拟线程（显式开关 spring.threads.virtual.enabled，官方文档核实非默认开启）。
- MyBatis-Plus 许可：如被问「2024 争议」，回应核查事实——全程 Apache-2.0、2024 零变更、唯一插曲 2020 反996协议数日删除。
- Spring Security 深挖预案：认证流程、token 刷新、双 token 设计、XSS/SQL 注入防护（面经实证必问）。

## 3. 功能范围 v1

1. 注册 / 登录（JWT）
2. 任务增删改查：标题、描述、分类、优先级（高/中/低）、状态（待办/进行中/已完成）、截止时间
3. 列表查询：状态/分类/优先级过滤 + 分页
4. 统计：各状态计数、分类分布（**手写 XML 复杂 SQL**，面试「会不会写原生 SQL」直接有答案）
5. AI 一句话建任务（核心卖点）
6. 前端页面（登录、任务列表、新建/编辑、一句话建任务、统计卡片）
7. 接口文档页（Knife4j）

## 4. 数据模型（3 张表，真实索引设计）

```sql
t_user     (id BIGINT PK AUTO_INCREMENT, username VARCHAR(32) UNIQUE NOT NULL,
            password_hash VARCHAR(100) NOT NULL, created_at DATETIME NOT NULL)

t_category (id BIGINT PK AUTO_INCREMENT, user_id BIGINT NOT NULL,
            name VARCHAR(32) NOT NULL, created_at DATETIME NOT NULL,
            CONSTRAINT uk_category_user_name UNIQUE (user_id, name))
            -- 组合唯一键已覆盖 user_id 前缀，无需单列索引

t_task     (id BIGINT PK AUTO_INCREMENT, user_id BIGINT NOT NULL,
            category_id BIGINT NULL, title VARCHAR(100) NOT NULL,
            description VARCHAR(500), priority VARCHAR(8) NOT NULL DEFAULT '中',
            status VARCHAR(16) NOT NULL DEFAULT '待办',
            due_time DATETIME NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
            INDEX idx_task_user (user_id), INDEX idx_task_status (status),
            INDEX idx_task_due (due_time), INDEX idx_task_category (category_id))
```

- 表名 `t_` 前缀（国内后端惯例；且 `user` 是 H2 保留字，加前缀让 schema.sql 一份脚本双库通用）。
- 引擎 InnoDB（MySQL 默认），字符集 utf8mb4；外键：t_task.user_id → t_user.id（ON DELETE CASCADE）、t_task.category_id → t_category.id（ON DELETE SET NULL）。
- 数据隔离：所有查询强制带 user_id 条件（来自 JWT 的当前用户），不允许横向越权。

## 5. API 设计（统一响应 {code, message, data}）

| 方法/路径 | 说明 |
|---|---|
| POST /api/auth/register | 注册（用户名唯一冲突 → 409） |
| POST /api/auth/login | 登录，返回 JWT |
| GET /api/tasks | 列表（status/categoryId/priority 过滤 + page/size 分页） |
| POST /api/tasks | 新建 |
| GET /api/tasks/{id} | 详情（只能查自己的） |
| PUT /api/tasks/{id} | 编辑（含状态流转） |
| DELETE /api/tasks/{id} | 删除 |
| GET /api/tasks/stats | 状态计数 + 分类分布 |
| GET /api/categories / POST /api/categories | 分类列表 / 新建 |
| POST /api/tasks/nl | 一句话建任务 {text} → 创建并返回任务 |

错误码约定：400 参数错误 / 401 未认证 / 403 越权 / 404 不存在 / 409 用户名冲突 / 502 AI 上游失败 / 503 未配置 API Key。

## 6. AI 功能设计（POST /api/tasks/nl）

- 调用 DeepSeek `chat/completions`（模型 deepseek-chat，temperature=0，`response_format={"type":"json_object"}`），系统提示词定义严格 JSON 输出：
  `{"title": string, "description": string|null, "priority": "高|中|低", "due_time": "yyyy-MM-dd HH:mm:ss"|null, "category": string|null}`
- 分类处理：输出的分类名与当前用户已有分类匹配则复用，否则新建。
- **LLM 输出先校验再落库**（title 非空且 ≤100 字、priority/状态枚举合法、due_time 可解析）——LLM 输出不可信是真实工程点，校验失败返回 502 并附原因。
- 无 Key（`DEEPSEEK_API_KEY` 未配置）→ 503 + 明确提示，**不假装有本地兜底**。
- 超时 30s；使用 RestClient（Spring 6.1+ 现代 HTTP 客户端）。
- 开启虚拟线程（spring.threads.virtual.enabled=true）。

## 7. 前端设计

- 页面：登录/注册页；任务列表页（过滤、分页、状态下拉切换、删除）；新建/编辑弹窗；**一句话建任务面板（输入 → 展示解析出的字段 → 落库进列表，重点演示）**；统计卡片。
- 结构：`frontend/` 目录，Vite 项目；Axios 拦截器统一挂 JWT 与错误提示；Pinia 存登录态。
- 运行模式：
  1. 开发：IDEA 跑后端（8080）+ `npm run dev`（Vite 代理 /api → 8080）；
  2. 单进程演示：`npm run build` 产物拷入后端 static/，只开 8080（面试现场用）。
- v1 不写前端单元测试（诚实口径，简历不写）。

## 8. 工程结构

```
taskwhisper/
├── pom.xml                       # 单模块 Maven，Boot BOM 管版本
├── src/main/java/com/taskwhisper/
│   ├── TaskWhisperApplication.java
│   ├── common/                   # Result、PageResponse、全局异常处理、业务异常
│   ├── config/                   # SecurityConfig、MybatisPlusConfig、AiProperties、WebConfig(CORS)
│   ├── entity/                   # User、Category、Task
│   ├── mapper/                   # UserMapper、CategoryMapper、TaskMapper
│   ├── service/                  # AuthService、TaskService、CategoryService、StatsService
│   ├── security/                 # JwtService、JwtAuthFilter、UserDetails 实现
│   ├── ai/                       # DeepSeekClient、NlTaskParser、LLM 输出校验
│   └── controller/               # Auth、Task、Category、Stats、Nl
├── src/main/resources/
│   ├── application.yml / application-test.yml
│   ├── db/schema.sql             # 建库建表脚本
│   └── mapper/                   # TaskMapper.xml（统计等复杂 SQL）
├── src/test/java/...             # 见 §9
├── frontend/                     # Vue 3 项目
├── docs/superpowers/specs/       # 本文档
└── README.md                     # 运行说明 + 架构图 + 面试问答预案 + 诚实声明
```

## 9. 测试策略（IDEA 一键全跑，全绿 = 完成度 100%）

- **Service 层（H2，MySQL 模式）**：注册/登录/用户名冲突；任务 CRUD 与过滤分页；分类复用与新建；统计 SQL 正确性（手写 XML 的回归锚点）。
- **NL 解析（mock DeepSeekClient）**：正常解析落库；LLM 返回非法 JSON；字段超限/枚举非法 → 502；未配 Key → 503；分类自动复用/新建。
- **Controller 层（MockMvc）**：未认证 → 401；越权访问他人任务 → 403/404；参数校验 → 400；CRUD 全链路。
- **安全**：SecurityConfig 过滤器链单测（白名单/认证路径行为）。
- 不写：前端单测、真实 DeepSeek 调用测试（手动冒烟，Key 可选）。

## 10. 环境安装清单（本机，全部命令行完成）

- JDK：Temurin 21.0.12 → `C:\Users\spz97\Tools\jdk-21`，注册 JAVA_HOME/PATH
- Maven 3.9.16 → `C:\Users\spz97\Tools\maven`，注册 MAVEN_HOME/PATH + 阿里云镜像 settings.xml
- MySQL 8.0.46 便携版 → `C:\Users\spz97\Tools\mysql`，`--initialize-insecure` 初始化数据目录，注册 Windows 服务（权限不足则提供 start 脚本），建库 taskwhisper + 专用账号
- Node.js 已有（v24.18.0）；npm 源已切 npmmirror
- IDEA：用户确认本体安装完成后 Open 项目目录

## 11. 明确不做（YAGNI，面试应答口径同步写入 README）

- Redis（v1 不装：Windows 原生环境代价高；口径「当前规模不需要，了解穿透/击穿/雪崩，v2 引入」）
- 微服务 / 消息队列 / Docker / 多模块拆分
- OAuth 第三方登录、邮箱验证码、找回密码
- 前端单元测试、多端适配（小程序/H5）

## 12. 交付标准

1. `mvn test` 全绿；`mvn spring-boot:run` 起 8080；
2. 前端 `npm run build` 成功；dev 代理联调走通；
3. 端到端冒烟：注册 → 登录 → 建任务 → 过滤分页 → 统计 → NL 建任务（配 Key 时）→ 演示页全流程；
4. 推 GitHub main（SSH）；
5. 用户手测三入口：演示页 / Knife4j / IDEA 测试套件。

## 13. 后续工作（不在本次范围内）

- 简历更新：后端版与全栈版两份定向简历，替换 4Scar
- BOSS 在线简历项目经历栏回填
- 可选 v2：Redis 缓存/限流、NL 解析结果确认流
