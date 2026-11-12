# 商品交易存证系统 · 后端代码设计（草案 v0.1）

> 基础包：`edu.jxue.goodsevidence`
> 技术栈：Spring Boot 3 + JDK 22 + MyBatis-Plus 3.5.17 + MySQL 8 + Redis + MinIO + ZXing + JWT
> 区块链层（FISCO BCOS v3 SDK）在后续步骤接入。

## 一、目录结构（当前已实现）

```
backend/
├── pom.xml
└── src/main/
    ├── resources/
    │   └── application.yml          # 数据源/Redis/MinIO/JWT/FISCO 配置
    └── java/edu/jxue/goodsevidence/
        ├── GoodsEvidenceApplication.java   # 启动类
        ├── common/
        │   └── Result.java                 # 统一返回结构
        ├── config/
        │   ├── MybatisPlusConfig.java       # 分页插件
        │   └── MinioConfig.java             # MinIO 客户端
        ├── entity/                          # 10 张表实体（数据层）
        │   ├── SysUser / Enterprise / Product / ProductCode
        │   ├── TradeRecord / QualityRecord / RepairRecord
        │   └── ChainRecord / VerifyLog / OperationLog
        ├── mapper/                          # 10 个 Mapper（extends BaseMapper）
        ├── dto/
        │   └── ProductRegisterDTO.java       # 入参
        ├── service/
        │   ├── ProductService.java           # 业务接口
        │   ├── impl/ProductServiceImpl.java  # 业务实现
        │   ├── MinioService.java             # 文件存储
        │   └── QrCodeService.java            # 二维码生成
        └── controller/
            └── ProductController.java        # 模块二：商品登记与一物一码
```

## 二、分层职责

| 层 | 包 | 职责 |
|---|---|---|
| 表现/接口 | `controller` | 接收 HTTP 请求，调用 service，返回 `Result` |
| 业务 | `service` / `service.impl` | 业务逻辑编排（登记→生成码→上链→履历） |
| 数据访问 | `mapper` + `entity` | MyBatis-Plus  CRUD |
| 公共 | `common` / `config` / `dto` | 返回结构、配置、入参 |

## 三、已实现模块

- **模块二 · 商品登记与一物一码**：`POST /api/product/register`
  登记商品 → 生成唯一码 `P{productId}` → ZXing 生成二维码图片 → 上传 MinIO → 写入 `product` + `product_code` 表。

## 四、待实现（下一步）

1. 模块一 用户与角色权限（JWT + RBAC 拦截器）
2. 模块三 商品全生命周期履历（聚合各记录时间线）
3. 模块四 智能合约权属转移（调 FISCO 合约 `transfer`）
4. 模块六 哈希上链存证（ChainService + EvidenceContract）
5. 模块七 扫码验真与溯源（校验链上哈希 vs 链下原文）
6. 模块八 监管审计看板
7. 前端 Vue 3 骨架与页面

> 注：本骨架在 `code-design/backend`，确认后我会同步进你 D 盘的正式 Spring Boot 工程。
