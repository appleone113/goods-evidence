# 商品交易存证系统 · 运行说明

> 毕业设计《商品交易存证系统设计与实现》配套工程（区块链方向）
> Spring Boot 3 + JDK 22 + MyBatis-Plus + MySQL 8 + Redis + MinIO + ZXing + FISCO BCOS + Vue 3

---

## 快速开始：从 GitHub 下载后（别人看这一节）

> 这一节给「刚 clone 下来、想在自己电脑上跑起来」的人。作者在答辩机上演示请看下一节。

### 1. 先装好这些（一次性）

| 组件 | 版本 | 验证命令 |
|---|---|---|
| JDK | **22**（至少 17） | `java -version` |
| Node.js | 18+ | `node -v` |
| Maven | 3.9+ | `mvn -v`（只有首次构建 jar 需要） |
| MySQL | 8.0 | 启动后 3306 可连 |

Redis、MinIO、FISCO BCOS **都不用装**：缓存自动降级、文件落本机 `uploads/`、链默认走 Mock 模拟实现。

### 2. 建库（只做一次）

```bash
mysql -uroot -p --default-character-set=utf8mb4 < sql/schema.sql
```

> 必须带 `--default-character-set=utf8mb4`，否则中文注释会报 `ERROR 1366`。
> MySQL 密码不是 `root` 的话，启动时设环境变量 `DB_PASSWORD=你的密码`。

### 3. 一键启动

| 系统 | 命令 |
|---|---|
| Windows | 双击 `start.bat` |
| Linux / macOS | `bash start.sh` |

脚本会自动：检查 java/node → 没有 jar 就 `mvn package` → 没有 `node_modules` 就 `npm install` →
以 `--fisco.enabled=false` 启动后端 → 启动 Vite → 打开浏览器。
**首次构建约 5-15 分钟**（`npm install` 受网络影响最大，实测本机约 6 分钟），之后跳过；
这一段黑窗口长时间没输出是正常的，别当成卡死，等它自己跑完。

浏览器打开 `http://localhost:5173`。

### 4. 第一次用要先注册账号 ⚠️

`sql/schema.sql` 只建表、不含数据，所以**新库是空的**。`ent / user / admin` 加 `123456`
是作者本机已有的演示账号，别人的库里没有。请到 `http://localhost:5173/register`
注册三个角色（企业端填一个企业ID即可），或按第四节用三条 curl 创建。

### 5. 环境变量（不改源码适配自己机器）

| 变量 | 默认值 | 什么时候改 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/second_hand_chain?...` | 库名 / 端口不同 |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` | MySQL 账号不同 |
| `APP_PUBLIC_BASE_URL` | `http://localhost:8080` | 手机扫码 / 局域网演示 |
| `JWT_SECRET` | 内置开发用串 | 公开部署前务必换掉 |
| `FISCO_ENABLED` | `false` | 接真链时设 `true` |

### 6. 关于「真链」

**默认是 Mock 模拟链，这是有意为之**：别人机器上没有作者的 VMware 虚机、4 个 FISCO 节点和 SDK 证书，
硬开真链后端会直接启动失败。Mock 模式下九个模块、页面、验真、看板全部可用，
只是 `tx_hash` 是模拟值。要接自己的链，见第六节与 `backend/fisco/config.toml.example`。

⚠️ `backend/fisco/` 下的证书和 `sdk.key` 是私钥，已在 `.gitignore` 排除；
仓库里只有 `config.toml.example` 模板，不要把自己的私钥提交上来。

### 7. 首次 clone 实测记录（2026-11-12）

不是静态检查，是把仓库 `git clone` 到**全新空目录**后，按上面流程真跑了一遍：

| # | 验证项 | 结果 |
|---|---|---|
| 1 | clone 出来的副本是否干净 | ✅ 无 `backend/target`、无 `frontend/node_modules`；`backend/fisco/` 只有 `config.toml.example`；全仓库无 `*.key` / `*.crt`，git 历史里也没有证书对象 |
| 2 | 后端首次构建 `mvn clean package -DskipTests` | ✅ `BUILD SUCCESS`，产出 105 MB 可执行 jar |
| 3 | 前端首次依赖安装 `npm install` | ✅ 声明的 8 个依赖全部装好（Vite 5.4.21 / Vue 3.5.44 / Element Plus 2.14.7 等），`package-lock.json` 生成 |
| 4 | 默认 Mock 启动（**刻意不传** `--fisco.enabled`，用 8081 隔离端口） | ✅ `Started GoodsEvidenceApplication`；登录接口 200、验真 `authentic=true`、履历 4 事件；日志 ERROR / Exception **0 行**，且没有误加载 `FiscoChainServiceImpl` |
| 5 | 前端首次启动 Vite（5174 隔离端口） | ✅ 首页 / `/src/main.js` / `/src/App.vue` 均 HTTP 200，`/api` 代理转发正常，编译日志无报错 |

> 第 4 项连的是作者本机已有账号和演练商品的 MySQL 库，证明的是「**默认配置 + 全新 jar 能启动**」；
> 别人全新空库依然要先按第 4 步注册账号，别直接拿 `ent/123456` 去登录。
> 两个隔离端口的测试进程跑完都已停掉，不影响本机 8080 / 5173。

---

## 零、日常怎么打开系统（答辩演示看这一节就够）

> 完整图文版见桌面 **`商品交易存证系统-速查卡.html`**（工程内副本 `系统速查卡.html`）。

| 场景 | 做法 | 难度 |
|------|------|------|
| 系统已经开着 | 浏览器直接打开 `http://localhost:5173` | ⭐ |
| 开机后打不开 / 服务没起 | **双击桌面 `打开商品交易存证系统.bat`**，自动检查并拉起 MySQL / 后端 / 前端，然后自动弹浏览器 | ⭐ |
| 想彻底关掉 | 双击桌面 `停止商品交易存证系统.bat` | ⭐ |
| 只看状态、不改动 | `python scripts/launch.py --check` | ⭐⭐ |

启动器 `scripts/launch.py` 的行为：

- **幂等**：8080 / 5173 已在跑就直接复用，不会重复启动导致端口冲突。
- **自动选链**：从 `backend/fisco/config.toml` 读 `peers` 探测节点，可达走真链；不可达自动加 `--fisco.enabled=false` 降级 Mock，功能不中断。
- **后台常驻**：`DETACHED_PROCESS` 拉起，关掉黑窗口服务继续跑。
- **自动用局域网 IP 作二维码基址**：`--app.public-base-url=http://<当前局域网IP>:8080`，手机扫码可直接打开验真页。
- 参数：`--check` 只打印 / `--no-browser` 不弹浏览器 / `--mock` 强制模拟链 / `--stop` 停止服务。

访问地址与账号：本机 `http://localhost:5173`；同一个 WiFi 下手机 / 别的电脑 `http://<本机局域网IP>:5173`；
账号 `ent` / `user` / `admin`，密码统一 `123456`。

> ⚠️ **换 WiFi 后 IP 会变**：二维码里固化了旧 IP，需按 `局域网演示操作卡.html` 用新 IP 重启后端并调
> `POST /api/product/refresh-qr` 批量重刷；真链还要同步改 `backend/fisco/config.toml` 的 `peers`。

---

## 一、目录结构

```
code-design/
├─ backend/                     # Spring Boot 3 后端
│  ├─ src/main/java/edu/jxue/goodsevidence/
│  │  ├─ common/    统一返回 Result、JWT、哈希工具、审计注解
│  │  ├─ config/    MyBatis-Plus / JWT 拦截器 / MinIO / FISCO / AOP 切面
│  │  ├─ controller/8 个控制器
│  │  ├─ service/   业务接口 + impl（含 Mock 链实现）
│  │  ├─ mapper/    10 张表 Mapper
│  │  ├─ entity/    10 个实体
│  │  ├─ vo/ dto/   返回体与请求体
│  └─ src/main/resources/application.yml
├─ frontend/                    # Vue 3 + Element Plus 前端
│  └─ src/views/  enterprise/  user/  regulator/  三端页面
├─ contracts/                   # Solidity 合约（部署到 FISCO BCOS）
│  ├─ Ownership.sol             # 权属合约：require(owner == msg.sender)
│  └─ Evidence.sol              # 存证合约：save / verify
├─ sql/schema.sql               # 建库建表脚本（10 张表）
├─ start.bat / start.sh         # 从 GitHub 下载后的一键启动（别人用）
├─ scripts/launch.py            # 作者本机桌面入口调用的一键启动器
└─ scripts/check-consistency.py # 前后端一致性静态检查
```

---

## 二、环境要求

| 组件 | 版本 | 是否必须 |
|------|------|---------|
| JDK | 22 | 必须 |
| Maven | 3.9+ | 必须（或用 IDEA 内置） |
| MySQL | 8.0 | 必须 |
| Redis | 6+ | **否**（不可用时自动降级为「无缓存直查」，不阻断业务） |
| MinIO | 新版 | **否**（默认 `storage.type=local` 落本机 `./uploads` 目录） |
| Node.js | 18+ | 前端必须 |
| FISCO BCOS | v3.17.0 | **否**（`fisco.enabled` 默认就是 `false` Mock 模式） |

> **零中间件也能跑**：`fisco.enabled` 默认 `false`（Mock 内存账本），配合 `storage.type=local`，
> 不装区块链、不装 MinIO、不装 Redis 也能完整跑通九个模块。
> 要接真链：设 `FISCO_ENABLED=true` 或启动参数 `--fisco.enabled=true`，切换方式见第六节；
> 作者本机的桌面一键启动器会自动探测链节点，可达就显式传 `--fisco.enabled=true`。

---

## 三、三步跑起来

### 1. 建库

```bash
mysql -uroot -p --default-character-set=utf8mb4 < sql/schema.sql
```

> 注意必须带 `--default-character-set=utf8mb4`，否则中文注释会报 `ERROR 1366`。

### 2. 启动后端

```bash
cd backend
mvn clean install -DskipTests
mvn spring-boot:run
# 或直接在 IDEA 里运行 GoodsEvidenceApplication
```

确认 `application.yml` 中 MySQL / Redis / MinIO 连接信息与你本机一致。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev      # 访问 http://localhost:5173
```

`vite.config.js` 已配置 `/api` 代理到后端 8080，无需额外处理跨域。

---

## 四、初始化账号（三端演示）

**推荐方式**：启动前端后访问 `http://localhost:5173/register`，在注册页选角色直接注册
（企业端记得填企业ID，脚本已预置 1/2/3 三家）。

也可以用接口创建：

```bash
# 企业端（登记商品 / 质检 / 维修 / 权属转移）
curl -X POST "http://localhost:8080/api/auth/register?username=ent&password=123456&role=ENTERPRISE&enterpriseId=1"

# 用户端（扫码验真 / 溯源履历）
curl -X POST "http://localhost:8080/api/auth/register?username=user&password=123456&role=USER"

# 监管端（审计看板 / 操作日志）
curl -X POST "http://localhost:8080/api/auth/register?username=admin&password=123456&role=ADMIN"
```

角色取值只能是 `USER` / `ENTERPRISE` / `ADMIN`（大小写均可，入库统一大写）。

---

## 五、完整演示流程

1. **登录企业端** → 商品登记 → 生成一物一码 + 二维码（同时完成链上权属初始化）
2. **商品列表** → 查看二维码 → 点「履历」查看全生命周期时间线
3. **质检录入** / **维修录入** → 上传报告/凭证，摘要上链存证
4. **权属转移** → 合约前置校验 `require(from == record.owner)` → 更新链上 owner + 交易摘要上链
5. **退出，登录用户端** → 扫码验真，输入二维码里的 code（如 `P1900000000000000001`）
   → 重算哈希与链上存证比对，返回真伪 + 完整履历
6. **退出，登录监管端** → 监管看板查看四项统计 + 最近操作日志（AOP 自动留痕）

---

## 六、真链 / Mock 链切换

`application.yml` 里 `fisco.enabled` 用的是 `${FISCO_ENABLED:false}`，**默认 Mock**，
由 `@ConditionalOnProperty` 自动装配 `MockChainServiceImpl`（内存账本模拟权属状态机 + 存证哈希，
业务代码零改动）。这样别人下载后不用装区块链也能跑通九个模块。

切真链只要启动参数或环境变量显式打开：

```bash
# Mock 链（默认，零中间件）
java -jar target/goods-evidence-1.0.0.jar --server.port=8080 --fisco.enabled=false

# 真链（需先按 backend/fisco/config.toml.example 配好证书与 peers）
java -jar target/goods-evidence-1.0.0.jar --server.port=8080 --fisco.enabled=true
# 等价写法：FISCO_ENABLED=true java -jar target/goods-evidence-1.0.0.jar --server.port=8080
```

> Windows 上作者本机 PATH 里的 java 是 1.8，跑不了 Spring Boot 3，
> 要用 `.jdks\openjdk-22\bin\java.exe` 的完整路径，或把 JDK 22 的 `bin` 放进 `JAVA_HOME`。
> 作者桌面一键启动器 `scripts/launch.py` 已自动处理，并在链节点可达时显式传 `--fisco.enabled=true`。

真链配置要点（分别在 `application.yml` 与 **`backend/fisco/config.toml`**：后者才是运行时读取的文件，
`src/main/resources/fisco/` 下的是源码副本，改它不生效）：

| 项 | 值 / 说明 |
|---|---|
| `fisco.group-id` | `group0`（FISCO 3.x 是字符串，不是数字 `1`） |
| `fisco.config-path` | `./fisco/config.toml`，必须指向 **jar 外**的真实文件 |
| SDK 证书 | `ca.crt / sdk.crt / sdk.key` 必须用 SDK 默认文件名放在 `backend/fisco/`，不要改名成 `ssl.*` 并显式指定，否则启动日志 `certConfig=CertConfig{}` 为空，发交易报 `-4010 no connection available` |
| `config.toml` 的 `peers` | 节点 RPC 地址；虚机换网段后必须同步改，否则同样报 -4010。可用 `bash scripts/check-real-chain.sh` 复检（脚本自动从该文件读 peers） |
| 合约地址 | `ownership-address` / `evidence-address`，部署后填入 |

> 复验记录见 `真链验证报告.html` 第六节；一键验证用 `python scripts/validate-real-chain.py`。

---

## 七、切换 MinIO 文件存储（可选）

默认 `storage.type=local`：二维码、质检报告、维修凭证落在本机 `./uploads/`，
通过 `/api/file/{对象名}` 回读（该路径已放行 JWT 校验，便于扫码页直接展示图片）。

改用 MinIO 时：

```yaml
storage:
  type: minio
```

并保证 `minio.endpoint` 指向运行中的 MinIO；业务代码通过 `FileStorageService`
接口调用，两个实现由 `@ConditionalOnProperty` 互斥装配，切换无需改代码。

## 八、九大功能模块对应位置

| 模块 | 主要代码 |
|------|---------|
| 1 用户与角色权限管理 | `AuthService/Impl`、`JwtInterceptor`、**`RoleAuthAspect` + `@RequireRole`** |
| 2 商品登记与一物一码 | `ProductService/Impl`、`QrCodeService`、`FileStorageService`（本地/MinIO） |
| 3 商品全生命周期履历 | `ProductLifecycleService/Impl`、前端 `views/user/Trace.vue` |
| 4 智能合约权属转移 | `TradeService/Impl`、`ChainService`、`contracts/Ownership.sol` |
| 5 质检与维修管理 | `QualityService/Impl`、`RepairService/Impl` |
| 6 哈希上链存证 | `ChainService`、各业务中的 `HashUtil.sha256` + `saveEvidence` |
| 7 扫码验真与溯源查询 | `VerifyService/Impl`（含 Redis 缓存）、前端 `ScanVerify.vue` |
| 8 监管审计看板 | `DashboardService/Impl`、**`AuditLogAspect`**、前端 `regulator/` |
| 9 系统测试与性能优化 | `ChainThroughputBenchmark`、`src/test/` |

---

## 九、一致性自检（不用启动服务）

```bash
python scripts/check-consistency.py
```

会检查四类问题：

1. 前端调用了后端不存在的接口
2. 路由引用了不存在的页面文件
3. 实体类字段与建表列名不一致
4. 前端 multipart 表单提交的字段名后端 DTO 里没有（如把 `org` 写成 `item`）

---

## 十、命令行编译打包（已验证）

本机 `mvn` 命令没有全局安装，也没有 JDK 22 的 `JAVA_HOME`。已搭好一套**不污染系统环境**的便携工具链，位置固定在：

| 组件 | 路径 |
|------|------|
| JDK | `C:\Users\Lenovo\.jdks\openjdk-22`（IDEA 已下好的，**不要再另外下载**） |
| Maven | `C:\Users\Lenovo\.workbuddy\binaries\java\maven\apache-maven-3.9.9` |
| 本地仓库 | `C:\Users\Lenovo\.workbuddy\binaries\java\m2repo` |
| settings | `C:\Users\Lenovo\.workbuddy\binaries\java\gec-settings.xml`（已配华为云镜像） |
| 启动脚本 | `C:\Users\Lenovo\.workbuddy\binaries\java\gmvn.sh` |

用法（Git Bash，先把脚本路径换成实际位置）：

```bash
cd backend
bash /c/Users/Lenovo/.workbuddy/binaries/java/gmvn.sh compile -DskipTests   # 编译
bash /c/Users/Lenovo/.workbuddy/binaries/java/gmvn.sh package -DskipTests   # 打包成 jar
```

> **为什么要用 `gmvn.sh` 而不是 `mvn`**：Git Bash 直接跑 Maven 自带的 `bin/mvn` 会报
> `找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher`，
> 因为脚本内部的 `cygpath` 把 `MAVEN_HOME` 转成了 Windows 路径，导致 classpath 算错。
> `gmvn.sh` 绕开这层转换，直接用 `java -cp boot/plexus-classworlds-*.jar ... Launcher` 启动。
> 另一种绕法是用 PowerShell 调 `bin\mvn.cmd`，两条路都能走。

**当前验证结果（2026-10-28 00:28）**：`BUILD SUCCESS`，80 个源文件全部通过，
产出 `backend/target/goods-evidence-1.0.0.jar`（Spring Boot 可执行 FAT jar）。

### 排查第三方 SDK 编译报错的正确姿势

改 FISCO SDK 调用这类代码时不要凭记忆猜 API，直接用 `javap` 反查 jar 里的真实签名：

```bash
JV=/c/Users/Lenovo/.jdks/openjdk-22/bin/javap
JAR=/c/Users/Lenovo/.workbuddy/binaries/java/m2repo/org/fisco-bcos/java-sdk/fisco-bcos-java-sdk-v3/3.6.0/fisco-bcos-java-sdk-v3-3.6.0.jar
$JV -classpath "$JAR" org.fisco.bcos.sdk.v3.codec.abi.FunctionEncoder
```

本轮靠这个纠正了三处记忆型错误（详见代码注释）：
`FunctionEncoder.encode()` 是实例方法且返回 `byte[]`、`Function` 的第三个参数必须是
`List<TypeReference<?>>`、`FunctionReturnDecoder.decode()` 也是实例方法。

---

## 十一、启动与端到端自检（2026-10-28 已跑通）

### 启动

```bash
cd backend
java -jar target/goods-evidence-1.0.0.jar --server.port=8080
```

> ⚠️ **`--server.port=8080` 不能省**。当前终端环境里有变量 `SERVER__PORT=49731`
> （WorkBuddy 自身注入的，它自己正占着这个端口），Spring Boot 的宽松绑定会把它吃进去，
> 于是 Tomcat 起在 49731 而不是 yml 里的 8080，然后报「端口被占用」启动失败。
> 命令行参数优先级最高，能盖掉它。这个坑只在命令行启动时出现，IDEA 里 Run 不受影响。

MySQL 用 `root/root`（本机 MySQL80 实测可连）；**Redis 没起也能正常跑**，
`CacheService` 会自动降级为直查，只有二维码/报告走本地 `./uploads` 目录。

### 九大模块自检结果

| # | 模块 | 结果 |
|---|------|------|
| 1 | 用户与角色权限管理 | ✅ USER 调企业接口返回 `403 需要角色：[ENTERPRISE, ADMIN]`；调看板返回 `403 需要角色：[ADMIN]` |
| 2 | 商品登记与一物一码 | ✅ 返回 `code=P<雪花ID>`、`qrUrl=/api/file/qr/*.png` |
| 3 | 商品全生命周期履历 | ✅ 4 个事件 `REGISTER → QUALITY → TRADE → REPAIR` |
| 4 | 智能合约权属转移 | ✅ 成功，`require(owner == msg.sender)` 由 MockChain 等价校验 |
| 5 | 质检与维修管理 | ✅ 均落库并上链存证 |
| 6 | 哈希上链存证 | ✅ `chain_record` 有 9 条记录 |
| 7 | 扫码验真与溯源 | ✅ `authentic=true`（修完下面两个 bug 后） |
| 8 | 监管审计看板 | ✅ 四项统计有数 + AOP 操作日志留痕 |
| 9 | 系统测试与性能优化 | ✅ `GET /api/perf/benchmark?rounds=N` 实测见下 |

演示账号：`ent/123456`（企业）、`user/123456`（用户）、`admin/123456`（监管）。

### 前端启动（已联调）

```bash
cd frontend
npm run dev        # http://localhost:5173
```

`vite.config.js` 已把 `/api` 代理到 8080。验证方式：经 5173 调登录、商品列表都能拿到数据，
说明代理与后端均正常（后端需先启动）。

> `vite.config.js` 已设 `host: '0.0.0.0'`，本机用 `localhost`、同一 WiFi 下的设备用
> `http://<本机局域网IP>:5173` 都能访问（换 WiFi 后按第零节的说明重刷二维码）。

### 模块九性能实测（2026-10-28）

```bash
curl "http://localhost:8080/api/perf/benchmark?rounds=1000" -H "Authorization: Bearer <admin token>"
```

| 笔数 | 耗时 | 吞吐 | 单笔均耗 |
|------|------|------|---------|
| 1,000 | 419 ms | 2,386 笔/秒 | 0.419 ms |
| 10,000 | 1,962 ms | 5,096 笔/秒 | 0.196 ms |

> ⚠️ 这是 **MockChainServiceImpl（内存账本）** 下的**链下模拟吞吐**，量级远高于真实链。
> 论文里要写清它是「对照组/链下基准」，等接上 FISCO BCOS 真链后用同一接口再测一组，
> 两组对比才有说服力。优化策略（批量上链 / 异步化 / 缓存）见 `ChainThroughputBenchmark` 注释。

### 本轮修掉的两个运行时 bug（验真一直 false 的真凶）

这两个都是**编译期发现不了、只有真跑才会暴露**的问题：

1. **存证 ID 被雪花 ID 覆盖**：`chain_record` 的主键 `evidence_id` 是拿链上返回的存证 ID
   当 key 去链上取哈希的，但原代码没 `setEvidenceId()`，让 MyBatis-Plus 生成了雪花 ID，
   与链上账本 key 完全对不上 → 验真必失败。现在三个 Service 都改成
   `chainRecord.setEvidenceId(ev.getEvidenceId())`。
2. **验真用 txHash 关联，而两边根本不是同一笔交易**：`trade_record.txHash` 是「权属转移」
   那笔的哈希，`chain_record.txHash` 是「存证」那笔的哈希，按它对必然查不到。
   为此给 `chain_record` 新增 `biz_id` 列（统一存商品 ID，已同步 `sql/schema.sql`），
   验真改为按 `biz_type + biz_id` 反查。

> 已在本机库执行过 ALTER，若换机器重建库直接用新的 `schema.sql` 即可。

### 已知小瑕疵

- 操作日志里登录类请求的 `operator` 记为 `anonymous`（此时还没身份），业务操作正常记录。
  `AuditLogAspect` 后续可从 token 里取用户名回填。
- Element Plus 全量打包约 1.1MB，`npm run build` 有 chunk 体积告警，暂未做按需引入。
