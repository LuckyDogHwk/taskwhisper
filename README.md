# TaskWhisper · 一句话待办

任务管理系统，核心卖点：**输入一句自然语言，AI 帮你建好任务**——「明天下午三点提醒我开会，高优先级，属于工作」→ 一条结构化任务落库。

- **后端**：Spring Boot 4.1 + MyBatis-Plus + Spring Security(JWT) + MySQL
- **前端**：Vue 3 + Vite + Element Plus + Pinia
- **AI**：DeepSeek（json 模式解析，输出先校验后落库）

## 架构

```
┌─────────────┐   REST /api    ┌──────────────────────────────────────┐
│  Vue 3 SPA  │ ──────────────▶│ Spring Boot 4.1（Java 21，虚拟线程） │
│ (Element+)  │  Bearer JWT    │  ├─ Security 过滤器链（JWT + BCrypt） │
└─────────────┘                │  ├─ Controller → Service → Mapper    │
      ▲                        │  │     (MyBatis-Plus + 手写 XML SQL) │
      │ npm run build 产物     │  └─ ai/ DeepSeekClient + NlTaskParser │
      │ 拷入 static/ 单进程演示 │                  │                   │
      └────────────────────────┘                  ▼                   │
                               ┌────────────┐  ┌────────────────────┐  │
                               │   MySQL 8  │  │  DeepSeek API      │  │
                               │ 3 表+索引  │  │ (json_object 模式) │  │
                               └────────────┘  └────────────────────┘  │
                                 └──────────────────────────────────────┘
```

## 技术栈

| 层 | 选择 | 备注 |
|---|---|---|
| JDK | Temurin 21 LTS | Boot 4 基线 17；虚拟线程是 21 独有亮点 |
| 框架 | Spring Boot 4.1.1 | 3.5.x 免费维护已于 2026-06-30 停止 |
| ORM | MyBatis-Plus 3.5.17（boot4 starter） | 分页需显式引入 `mybatis-plus-jsqlparser` |
| 鉴权 | Spring Security + JWT（Nimbus）+ BCrypt | 自定义 OncePerRequestFilter 挂入过滤器链 |
| 数据库 | MySQL 8.0（测试用 H2 MODE=MySQL） | schema.sql 一份脚本双库通用 |
| 接口文档 | springdoc-openapi 3.1.1 | Boot 4 路线；Knife4j 停在面向 Boot 3 的 4.5.0 故未采用 |
| AI | DeepSeek chat/completions | `response_format=json_object`、temperature 0、30s 超时 |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Vue Router + Axios | 无前端测试（诚实声明） |

## 快速开始

前置：JDK 21、Maven、MySQL 8（本机免安装版见 `C:\Users\spz97\Tools\`，启动用 `Tools\mysql\start-mysql.bat`）。

```bash
# 1. 初始化数据库（仅首次）
mysql -utaskwhisper -ptaskwhisper_dev taskwhisper < src/main/resources/db/schema.sql

# 2. 启动后端（8080）
mvn spring-boot:run

# 3a. 开发模式：另开终端跑前端（5173，自动代理 /api → 8080）
cd frontend && npm install && npm run dev

# 3b. 单进程演示模式（面试现场推荐）：构建产物进后端 static，只开 8080
cd frontend && npm run build:demo
# 之后重启后端，浏览器打开 http://localhost:8080 即完整应用
```

环境变量（默认值均可本地跑通）：

| 变量 | 默认 | 说明 |
|---|---|---|
| `DB_USER` / `DB_PASSWORD` | taskwhisper / taskwhisper_dev | 本地开发库 |
| `TASKWHISPER_JWT_SECRET` | 内置开发值 | 演示/生产务必覆盖（≥32 字节） |
| `DEEPSEEK_API_KEY` | 空 | **不配置时 NL 接口返回 503**（诚实降级，无本地规则兜底） |
| `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` | api.deepseek.com / deepseek-chat | |

## 功能与接口

- 注册/登录：`POST /api/auth/register`、`POST /api/auth/login`
- 任务 CRUD + 过滤分页：`GET/POST /api/tasks`、`GET/PUT/DELETE /api/tasks/{id}`
- 统计（手写 XML SQL）：`GET /api/tasks/stats`（状态计数 + 分类分布，含「未分类」桶）
- 分类：`GET/POST /api/categories`（同名幂等）
- **一句话建任务**：`POST /api/tasks/nl`（无 Key → 503；LLM 输出校验失败 → 502 不落库）
- 接口文档：`/swagger-ui/index.html`（右上角 Authorize 贴 token 可在线调试全部接口）

## 数据模型

`t_user` / `t_category` / `t_task` 三表，真实索引设计：`idx_task_user/status/due/category`，组合唯一键 `uk_category_user_name(user_id, name)`；外键 `ON DELETE CASCADE / SET NULL`。所有查询强制当前用户作用域，横向越权一律 404。

## 测试

```bash
mvn test   # 74 个测试全绿：Service/Controller/Security/NL 解析/统计 SQL 全链路，真实 H2 造数
```

## 面试问答预案

**为什么 Spring Boot 4.1 而不是 3.5？**
3.5.x 的免费维护已于 2026-06-30 结束（官方博客原文：继续收补丁请升级 4.x），开新项目选 3.5 等于第一天选停维版本。4.0 于 2025-11 GA，到本项目启动时已发布近一年、4.1.1 为最新 GA。4 与 3 的核心编程模型（注解/自动装配）一致，差异在 Jackson 3（包名 `tools.jackson`）与模块化拆分。我额外准备了 2.x→3.x→4.x 的差异话题（javax→jakarta、JDK 17 基线），应对用 3.x 的公司。

**为什么 JDK 21？**
Boot 4 基线是 17，21 向下覆盖 17 全部语法不损失任何岗位竞争力；虚拟线程是 21 独有，本项目通过 `spring.threads.virtual.enabled=true` 实际开启（官方文档明确这是显式开关，4.x 并非默认开启——这点面试容易被问错）。校招 JD 已出现「跟踪 Java 21+ 虚拟线程/Scoped Values」的原文。

**MyBatis-Plus 有没有许可风险？**
没有。全程 Apache-2.0（2024 年零变更），网上传的「2024 许可争议」实为 CVE-2024-35548 的漏洞声明之争；项目史上唯一插曲是 2020 年短暂附加反996协议、数日内删除。实操坑：3.5.9 起分页拦截器拆包到 `mybatis-plus-jsqlparser`，不显式引入分页插件直接不可用——本项目的 pom 注释里就记着这个坑。

**登录鉴权怎么做深挖准备？**
Spring Security 过滤器链（无状态 + JWT + BCrypt），自定义 OncePerRequestFilter 解析 Bearer token 装载 SecurityContext；401/403 返回统一 JSON（手写响应，不走默认重定向）。面经实证「负责登录鉴权」会被追问：token 过期策略（本项目 24h）、token 刷新/双 token（诚实回答：当前单 token + 过期重登，双 token 方案我在 v2 计划里）、注入防护（登录防用户枚举：用户不存在与密码错误同一文案；XSS/SQL 注入：MyBatis-Plus 参数化 + DTO 校验）。

**为什么留了手写 XML SQL？**
统计接口（状态分组计数 + LEFT JOIN 分类分布）写在 `TaskMapper.xml`：LEFT JOIN 的 ON 条件带 user_id 防跨用户泄漏、GROUP BY 双列保正确性、保留「未分类」桶。单表 CRUD 用 MyBatis-Plus 提速，复杂聚合回归 XML——两种方式都写得出。

**NL 功能凭什么说「LLM 输出不可信」？**
DeepSeek 以 json 模式返回后，先过 NlTaskParser 校验（title 必填/长度、枚举合法、日期 STRICT 解析——这里踩过 `yyyy` 纪元年 STRICT 拒绝解析的坑，改 `uuuu`），校验失败返回 502 且**不落库**；无 Key 时接口明确 503，不假装有本地兜底。

**这个项目你真实踩过哪些坑？**（每个都能讲出排查过程）
1. Boot 4 中 `@AutoConfigureMockMvc` 从 `boot.test.autoconfigure` 迁到 `boot.webmvc.test.autoconfigure`（新构件 spring-boot-webmvc-test）
2. H2 的 `USER` 是保留字 → 表名加 `t_` 前缀，一份 schema.sql 双库通用
3. 错误分发端点 `/error` 未放行 → 404 被安全链拦成 500
4. 请求体非 UTF-8 → Jackson 抛 HttpMessageNotReadable → 需映射 400 而非兜底 500
5. STRICT 解析器 `yyyy`（纪元年）缺纪元拒绝解析 → `uuuu`
6. 全局兜底异常处理器必须打日志——线上排障第一现场

## 诚实声明

- 未引入 Redis/消息队列/微服务/Docker：当前单机单体规模不需要；缓存穿透/击穿/雪崩等概念已在 v2 计划（列表缓存 + NL 限流），简历与面试口径与此一致
- 前端无单元测试；后端 74 测试全绿是完成度标准
- NL 功能依赖 DeepSeek Key；未配置时该接口 503，其余功能不受影响
- 单 token 方案（24h 过期重登），无 refresh token——v2 候选
