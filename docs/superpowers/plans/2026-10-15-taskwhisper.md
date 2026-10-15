# TaskWhisper 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `idea_project/taskwhisper/` 建成「一句话待办」全栈求职项目：Spring Boot 4.1 后端 + Vue 3 前端 + MySQL，测试全绿，推 GitHub。

**Architecture:** 单模块 Maven 后端（REST API + Spring Security JWT + MyBatis-Plus + DeepSeek NL 解析），`frontend/` 子目录 Vue 3 SPA（开发态 Vite 代理、演示态 build 产物进后端 static）。所有数据按 JWT 用户隔离。

**Tech Stack:** Java 21 (Temurin) · Spring Boot 4.1.x · MyBatis-Plus 3.5.17 (boot4 starter + jsqlparser) · Spring Security + JWT · MySQL 8.0.46 · H2 (test) · DeepSeek API · Vue 3 + Vite + Element Plus + Pinia + Vue Router + Axios · JUnit 5 + MockMvc。

## Global Constraints

- 包名 `com.taskwhisper`；后端单模块，前端在 `frontend/`；仓库根 = `idea_project/taskwhisper/`。
- 版本（pom 固定）：Spring Boot 4.1.1（BOM 管全部依赖版本）、mybatis-plus-spring-boot4-starter 3.5.17、mybatis-plus-jsqlparser 3.5.17（**必须显式引入，否则分页不可用**）、H2（test scope）。
- 响应统一 `{code, message, data}`；错误码：400 参数 / 401 未认证 / 403 越权 / 404 不存在 / 409 用户名冲突 / 502 AI 上游失败 / 503 未配 Key。
- 枚举值（全中文）：priority ∈ {高,中,低}（默认 中）；status ∈ {待办,进行中,已完成}（默认 待办）。
- 数据隔离铁律：一切 task/category 查询必须带当前用户 user_id（来自 JWT），不得横向越权。
- 密码 BCrypt 哈希存储；JWT 密钥/DeepSeek Key/数据库密码全部走环境变量，仓库不提交任何真实密钥。
- 无 DeepSeek Key 时 NL 接口返回 503，**禁止实现本地规则兜底**。
- LLM 输出必须校验后才落库；校验失败 → 502 + 原因。
- 不引入：Redis、MQ、Docker、微服务、多模块、OAuth、前端单测、邮件验证码。
- 测试命令：`mvn test`（后端，含 H2 全集成）；全绿是完成度标准。
- 提交纪律：每个 Task 完成后单独 commit，message 用 `feat:` / `test:` / `docs:` 前缀。

---

### Task 0: 本机环境安装

**Files:**
- 安装：`C:\Users\spz97\Tools\jdk-21\`、`C:\Users\spz97\Tools\maven\`、`C:\Users\spz97\Tools\mysql\`
- Create: `C:\Users\spz97\Tools\maven\conf\settings.xml`（阿里云镜像）

- [ ] **Step 1: 解压三件套**（zip 已在 `Tools\downloads\`：jdk21.zip / maven.zip / mysql.zip）

```bash
cd /c/Users/spz97/Tools
unzip -q downloads/jdk21.zip && mv jdk-21.0.12.1+1 jdk-21   # 实际解压出的目录名以 unzip 结果为准
unzip -q downloads/maven.zip                                # apache-maven-3.9.16 → 移动到 maven/
unzip -q downloads/mysql.zip                                # mysql-8.0.46-winx64 → 移动到 mysql/
```

- [ ] **Step 2: 注册环境变量**（JAVA_HOME、MAVEN_HOME、PATH 追加两处 bin）

```bash
setx JAVA_HOME "C:\Users\spz97\Tools\jdk-21"
setx MAVEN_HOME "C:\Users\spz97\Tools\maven"
# PATH 追加（用 powershell 读现有 PATH 后追加，避免 setx 截断）：
#   C:\Users\spz97\Tools\jdk-21\bin; C:\Users\spz97\Tools\maven\bin
```

- [ ] **Step 3: settings.xml 阿里云镜像**（maven/conf/，mirrors 节点配 `https://maven.aliyun.com/repository/public` + central）

- [ ] **Step 4: 初始化 MySQL 数据目录 + 启动**

```bash
/c/Users/spz97/Tools/mysql/bin/mysqld --initialize-insecure --basedir=C:/Users/spz97/Tools/mysql --datadir=C:/Users/spz97/Tools/mysql/data
# 启动：优先尝试注册服务 mysqld --install MySQL80（需管理员）；失败则用前台进程 + start-mysql.bat
```

- [ ] **Step 5: 建库建账号**

```sql
CREATE DATABASE taskwhisper DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'taskwhisper'@'localhost' IDENTIFIED BY '<本地开发密码>';
GRANT ALL PRIVILEGES ON taskwhisper.* TO 'taskwhisper'@'localhost';
```

- [ ] **Step 6: 验证**

Expected: `java -version` 21.x、`mvn -version` 3.9.16、`mysql -utaskwhisper -p... taskwhisper -e "SELECT 1"` 返回 1。

### Task 1: 项目骨架 + 版本解析解雷

**Files:**
- Create: `pom.xml`、`.gitignore`、`src/main/java/com/taskwhisper/TaskWhisperApplication.java`
- Create: `src/main/resources/application.yml`、`src/main/resources/application-test.yml`、`src/main/resources/db/schema.sql`

**Interfaces:**
- Produces: `TaskWhisperApplication`（@SpringBootApplication）；schema.sql 三表（见 spec §4 原文）；application.yml 含 `spring.threads.virtual.enabled: true`。

- [ ] **Step 1: 写 pom.xml**（parent boot 4.1.1；依赖：starter-web、starter-validation、starter-security、mybatis-plus-spring-boot4-starter 3.5.17、mybatis-plus-jsqlparser 3.5.17、mysql-connector-j、H2 test、starter-test、knife4j-openapi3-jakarta-spring-boot-starter（版本构建时验证，Boot 4 不兼容则删掉换 springdoc）、spring-boot-maven-plugin）

- [ ] **Step 2: `mvn -q compile` 验证依赖全部可解析**

Expected: BUILD SUCCESS（此步是 Boot 4.1.1 + MP boot4 starter + Jackson 3 生态的版本解雷点，失败就按报错降级/换版本，**在进入业务代码前解决**）。

- [ ] **Step 3: git init + .gitignore + 首 commit**

```bash
cd /c/Users/spz97/Desktop/VScode/idea_project/taskwhisper
git init && git add -A && git commit -m "feat: 项目骨架（Spring Boot 4.1 + MyBatis-Plus + Vue 占位）"
```

### Task 2: 公共层（Result / 异常体系）

**Files:**
- Create: `src/main/java/com/taskwhisper/common/Result.java`、`common/PageResponse.java`、`common/BizException.java`、`common/ErrorCode.java`、`common/GlobalExceptionHandler.java`
- Test: `src/test/java/com/taskwhisper/common/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces:
  - `Result<T>`：静态工厂 `Result.ok(T data)` / `Result.error(int code, String message)`；序列化 `{code, message, data}`
  - `ErrorCode` 枚举：`BAD_REQUEST(400)`、`UNAUTHORIZED(401)`、`FORBIDDEN(403)`、`NOT_FOUND(404)`、`CONFLICT(409)`、`AI_UPSTREAM_FAILED(502)`、`AI_NOT_CONFIGURED(503)`，含 message 文案
  - `BizException extends RuntimeException`：携带 ErrorCode
  - `GlobalExceptionHandler`（@RestControllerAdvice）：BizException → 对应码；MethodArgumentNotValidException → 400（拼校验消息）；兜底 → 400
  - `PageResponse<T>`：`{total, records}`

- [ ] **Step 1: 写失败测试**（BizException 映射 409、校验异常映射 400、兜底 400）
- [ ] **Step 2: `mvn test -Dtest=GlobalExceptionHandlerTest` 失败**
- [ ] **Step 3: 实现上述类**  **Step 4: 测试转绿**  **Step 5: commit `feat: 统一响应与异常体系`**

### Task 3: 实体 + Mapper + 配置 + 建表加载

**Files:**
- Create: `entity/User.java`、`entity/Category.java`、`entity/Task.java`（MyBatis-Plus 注解：@TableName、@TableId(type=AUTO)、@TableField(fill=INSERT/INSERT_UPDATE)）
- Create: `mapper/UserMapper.java`、`mapper/CategoryMapper.java`、`mapper/TaskMapper.java`（extends BaseMapper）
- Create: `config/MybatisPlusConfig.java`（分页拦截器 PaginationInnerInterceptor(DbType.MYSQL)）、`config/MetaObjectHandlerConfig.java`（createdAt/updatedAt 自动填充）
- Test: `src/test/java/com/taskwhisper/mapper/MapperSmokeTest.java`（H2 + schema.sql 加载）

**Interfaces:**
- Produces: 实体字段与 spec §4 完全一致；`TaskMapper` 额外方法 `List<TaskStatsRow> statsByStatus(Long userId)`、`List<CategoryStatsRow> statsByCategory(Long userId)`（SQL 在 Task 8 的 XML 中定义）。

- [ ] **Step 1: 写 MapperSmokeTest**（@SpringBootTest + application-test.yml：H2 MODE=MySQL + schema.sql 自动执行；插入 user/category/task → 断言回读字段、自动填充的 created_at 非空、分页拦截器注入成功）
- [ ] **Step 2: 测试失败（类不存在）**  **Step 3: 实现**  **Step 4: 转绿**  **Step 5: commit `feat: 实体与 Mapper 层 + 分页配置`**

### Task 4: Auth 服务（注册 / 登录 / JWT 签发）

**Files:**
- Create: `dto/RegisterRequest.java`、`dto/LoginRequest.java`、`dto/AuthResponse.java`
- Create: `service/AuthService.java`、`security/JwtService.java`
- Test: `src/test/java/com/taskwhisper/service/AuthServiceTest.java`、`security/JwtServiceTest.java`

**Interfaces:**
- Produces:
  - `AuthService.register(RegisterRequest)`：用户名已存在 → BizException(CONFLICT)；BCrypt 哈希入库；返回 `AuthResponse(token, userId, username)`
  - `AuthService.login(LoginRequest)`：用户不存在或密码错 → BizException(UNAUTHORIZED)（**同一文案，防用户枚举**）；成功签发 JWT
  - `JwtService.generateToken(userId, username)` / `JwtService.parse(token)` → userId（签名错/过期 → 抛 JwtException）
- JWT：HS256，密钥 `TASKWHISPER_JWT_SECRET`（≥32 字节，测试用固定值），有效期 24h，claims 含 userId/username。

- [ ] **Step 1: 失败测试**（注册成功可登录；重复注册 409；错密码 401 且文案与「用户不存在」一致；token 解析回 userId；篡改 token 抛异常）
- [ ] **Step 2-5: TDD 循环** → commit `feat: 注册登录与 JWT 签发`

### Task 5: Spring Security 过滤器链

**Files:**
- Create: `security/JwtAuthFilter.java`（OncePerRequestFilter：读 Authorization: Bearer，解析成功 → 装载 UsernamePasswordAuthenticationToken 进 SecurityContextHolder）
- Create: `config/SecurityConfig.java`（无状态、CSRF 关闭、白名单 `/api/auth/**` + 文档路径、其余 authenticated、BCryptPasswordEncoder bean、异常处理返回 401 JSON 而非 302）
- Test: `src/test/java/com/taskwhisper/security/SecurityConfigTest.java`（MockMvc：无 token 访问 /api/tasks → 401 JSON；白名单放行；坏 token → 401）

- [ ] **Step 1: 失败测试**  **Step 2-5: TDD 循环** → commit `feat: JWT 认证过滤器链`

### Task 6: 任务 CRUD + 过滤分页

**Files:**
- Create: `dto/TaskCreateRequest.java`、`dto/TaskUpdateRequest.java`、`dto/TaskResponse.java`、`dto/TaskQueryRequest.java`
- Create: `service/TaskService.java`
- Test: `src/test/java/com/taskwhisper/service/TaskServiceTest.java`

**Interfaces:**
- Produces:
  - `TaskService.create(Long userId, TaskCreateRequest)` → TaskResponse
  - `TaskService.update(Long userId, Long taskId, TaskUpdateRequest)`（task 不存在或不属于该用户 → NOT_FOUND；部分字段可空=不改）
  - `TaskService.delete(Long userId, Long taskId)`、`TaskService.get(Long userId, Long taskId)`
  - `TaskService.page(Long userId, TaskQueryRequest)`：status/categoryId/priority 过滤 + 分页 → `PageResponse<TaskResponse>`（分类过滤查 category 属于该用户，否则结果为空而非越权）
- 校验：title 1-100；description ≤500；priority/status 枚举合法；due_time 未来可选。

- [ ] **Step 1: 失败测试**（CRUD 全链路；越权 get/update/delete → NOT_FOUND；过滤组合；分页边界；非法枚举 → 校验异常）
- [ ] **Step 2-5: TDD 循环** → commit `feat: 任务 CRUD 与过滤分页`

### Task 7: 分类服务

**Files:**
- Create: `dto/CategoryCreateRequest.java`、`dto/CategoryResponse.java`
- Create: `service/CategoryService.java`
- Test: `src/test/java/com/taskwhisper/service/CategoryServiceTest.java`

**Interfaces:**
- Produces: `CategoryService.list(Long userId)`、`CategoryService.create(Long userId, String name)`（同名 → 返回已有，幂等）；`findOrCreate(Long userId, String name)`（供 NL 解析复用，Task 9 依赖）。

- [ ] **Step 1-5: TDD 循环** → commit `feat: 分类服务（含幂等 findOrCreate）`

### Task 8: 统计（手写 XML 复杂 SQL）

**Files:**
- Create: `src/main/resources/mapper/TaskMapper.xml`（namespace=TaskMapper）
- Create: `dto/StatsResponse.java`（statusCounts: Map<String,Long> + categoryCounts: List<{categoryId,name,count}>，含「未分类」桶）
- Create: `service/StatsService.java`
- Test: `src/test/java/com/taskwhisper/service/StatsServiceTest.java`

**Interfaces:**
- Produces: `StatsService.stats(Long userId)`
- SQL 要点（面试展示）：`SELECT status, COUNT(*) FROM task WHERE user_id = #{userId} GROUP BY status`；分类分布用 `LEFT JOIN category ON ... GROUP BY c.id, c.name`（**LEFT JOIN 保留未分类任务**，这是可讲的 SQL 点）。

- [ ] **Step 1: 失败测试**（造 3 状态 2 分类 + 1 未分类数据 → 断言各桶数字精确；他人任务不计入）
- [ ] **Step 2-5: TDD 循环** → commit `feat: 统计接口（手写 XML SQL）`

### Task 9: AI 模块（DeepSeek 解析）

**Files:**
- Create: `config/AiProperties.java`（@ConfigurationProperties("taskwhisper.ai")：api-key/base-url/model，key 来自 `DEEPSEEK_API_KEY`）
- Create: `ai/DeepSeekClient.java`（RestClient，POST /chat/completions，response_format json_object，temperature 0，超时 30s，返回原始 JSON 字符串）
- Create: `ai/NlTaskParser.java`（系统提示词定义严格 JSON 契约 → 解析 → 校验 title 非空≤100、priority∈{高中低}、due_time 可解析；非法 → BizException(AI_UPSTREAM_FAILED) 附原因）
- Create: `service/NlTaskService.java`（无 Key → BizException(AI_NOT_CONFIGURED)；解析 → CategoryService.findOrCreate → TaskService.create）
- Test: `src/test/java/com/taskwhisper/ai/NlTaskParserTest.java`、`service/NlTaskServiceTest.java`（mock DeepSeekClient）

**Interfaces:**
- Produces: `NlTaskService.createFromText(Long userId, String text)` → TaskResponse
- 系统提示词 JSON 契约：`{"title": string, "description": string|null, "priority": "高|中|低", "due_time": "yyyy-MM-dd HH:mm:ss"|null, "category": string|null}`

- [ ] **Step 1: 失败测试**（正常解析落库；分类复用与新建；非法 JSON → 502；title 超长 → 502；未配 Key → 503；text 空 → 400）
- [ ] **Step 2-5: TDD 循环** → commit `feat: DeepSeek 自然语言建任务`

### Task 10: Controller 层全链路

**Files:**
- Create: `controller/AuthController.java`、`controller/TaskController.java`、`controller/CategoryController.java`、`controller/StatsController.java`、`controller/NlController.java`
- Test: `src/test/java/com/taskwhisper/controller/`（每控制器一个 MockMvc 测试）

**Interfaces:** 严格按 spec §5 表；当前用户 userId 从 Authentication 取（JwtAuthFilter 装载的 principal）。

- [ ] **Step 1: 失败测试**（注册→登录→带 token CRUD 全链路；401/403/404/409 各路径；NL 503 路径用 @MockBean）
- [ ] **Step 2-5: TDD 循环** → commit `feat: REST API 全链路`

### Task 11: 接口文档

- [ ] 接入 Knife4j（若 Boot 4/Jackson 3 不兼容 → 降级 springdoc-openapi 最新版）；验证 `/doc.html` 可访问、接口分组正常 → commit `feat: 接口文档`

### Task 12: 前端骨架（可与 Task 4-10 并行，由前端 agent 执行）

**Files:**
- Create: `frontend/package.json`、`vite.config.js`（proxy `/api` → `http://localhost:8080`）、`index.html`
- Create: `frontend/src/main.js`、`App.vue`、`router/index.js`（/login、/tasks，路由守卫查 Pinia token）、`stores/auth.js`、`api/http.js`（Axios 实例：baseURL /api、请求拦截器挂 JWT、响应拦截器解 Result、401 跳登录）、`api/*.js`（auth/tasks/categories/stats/nl 五个模块，函数签名对照 spec §5）

- [ ] **Step 1: 脚手架 + 依赖安装**（npm create vite@latest → vue 模板；npm i element-plus pinia vue-router axios；npmmirror 已配）
- [ ] **Step 2: `npm run build` 成功**  **Step 3: commit `feat: 前端骨架与 API 层`**

### Task 13: 前端页面

**Files:**
- Create: `frontend/src/views/LoginView.vue`（登录/注册切换）
- Create: `frontend/src/views/TasksView.vue`（Element Plus 表格：过滤条（状态/分类/优先级下拉）、分页器、状态切换、删除；顶部统计卡片；新建/编辑 Dialog）
- Create: `frontend/src/views/` NL 面板组件：输入一句话 → 调 /api/tasks/nl → 展示解析出的字段 → 确认/直接落库进列表（**面试重点演示**）

- [ ] **Step 1: 页面实现**  **Step 2: `npm run build` 成功**  **Step 3: commit `feat: 前端页面（登录/任务/NL 建任务/统计）`**

### Task 14: 前后端联调冒烟

- [ ] 后端起 8080 + MySQL 跑通：注册 → 登录 → CRUD → 过滤分页 → 统计 → NL（配 Key 时）；`npm run dev` 下浏览器全流程
- [ ] 单进程演示模式：`npm run build` → dist 拷入 `src/main/resources/static/` → 只开 8080 全流程复验
- [ ] `mvn test` 全绿终验 → commit `chore: 联调与演示模式`

### Task 15: README + 发布

**Files:**
- Create: `README.md`（运行说明两模式；mermaid 架构图；接口文档入口；**面试问答预案**：Boot 4 vs 3.5 / JDK 21 / MyBatis-Plus 许可与 jsqlparser 坑 / Spring Security 深挖（token 刷新、双 token、注入）/ 手写 SQL / NL 功能 prompt 与校验 / AI 工具使用；**诚实声明**：无 Redis/MQ/前端单测、单机单体、未配 Key 时 NL 不可用）
- [ ] git init 已在 Task 1；远程 `git@github.com:LuckyDogHwk/taskwhisper.git`（用户先建空仓）→ push main → commit/tag

## 自检记录

- Spec 覆盖：§2 技术栈→Task 0/1/11；§3 功能→Task 6/7/8/9/13；§4 数据模型→Task 3 schema.sql；§5 API→Task 10；§6 AI→Task 9；§7 前端→Task 12/13/14；§9 测试→各 Task 测试文件；§10 环境→Task 0；§12 交付标准→Task 14/15。无缺口。
- 类型一致性：Result/PageResponse/ErrorCode/BizException 在 Task 2 定义，Task 4+ 引用；CategoryService.findOrCreate 在 Task 7 定义、Task 9 引用；JwtService.parse 返回 userId，Task 10 从 Authentication principal 取 userId（principal 为 userId 字符串，Task 5 装载时约定）。
- 无占位符：所有枚举值、错误码、路径、版本号均为具体值。

---

## 执行记录（2026-10-15 完成）

- Task 0-3：环境（JDK 21.0.12.1/Maven 3.9.16/MySQL 8.0.46 免安装版，Tools\ 目录，start-mysql.bat）+ 骨架 + 公共层，主会话执行，6 测试全绿
- Task 4-9 + 12-13：5 路并行 workflow（鉴权/任务+分类/统计/AI/前端），契约锁定，agent 不跑 mvn 防 target 冲突；集成后修 3 处：yyyy→uuuu（STRICT 纪元年坑）、@AutoConfigureMockMvc 包迁移（Boot 4 → spring-boot-webmvc-test）、/error 白名单 + NoResourceFound→404 + HttpMessageNotReadable→400 + 兜底异常打日志
- Task 10：Controller + MockMvc 端到端测试（主会话）
- Task 11：Knife4j 4.5.0 停在 Boot 3 → 按预案降级 springdoc-openapi 3.1.1
- Task 14：真实 MySQL 全链路 curl 冒烟通过（中文落库/统计未分类桶/503 降级/GBK 400/数据隔离）；双模式验证（dev 5173 代理 + build:demo 单进程 8080）
- Task 15：README（含 6 个真实踩坑 + 面试预案）✅；GitHub 推送 ⏳ 等用户建空仓 taskwhisper
- 最终：**74 测试全绿**
