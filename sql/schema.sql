-- =============================================================
-- 商品交易存证系统 —— 数据库建表脚本
-- 对应论文库名：second_hand_chain（10 张表）
-- MySQL 8.0 / utf8mb4
--
-- 【导入方式】（务必带 utf8mb4，否则中文注释/数据会报 ERROR 1366）
--   mysql -uroot -p --default-character-set=utf8mb4 < schema.sql
--   或 Navicat / DataGrip 中执行本文件
-- =============================================================

CREATE DATABASE IF NOT EXISTS second_hand_chain
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE second_hand_chain;

SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------
-- 1. 企业表：商家 / 质检机构 / 维修方等链上参与主体
-- -------------------------------------------------------------
DROP TABLE IF EXISTS enterprise;
CREATE TABLE enterprise (
  enterprise_id BIGINT       NOT NULL COMMENT '企业ID（雪花）',
  name          VARCHAR(100) NOT NULL COMMENT '企业名称',
  credit_code   VARCHAR(32)           COMMENT '统一社会信用代码',
  legal_person  VARCHAR(50)           COMMENT '法人代表',
  contact       VARCHAR(50)           COMMENT '联系方式',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (enterprise_id),
  UNIQUE KEY uk_credit_code (credit_code)
) ENGINE = InnoDB COMMENT = '企业';

-- -------------------------------------------------------------
-- 2. 系统用户表：三角色 USER / ENTERPRISE / ADMIN
-- -------------------------------------------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  user_id       BIGINT       NOT NULL COMMENT '用户ID（雪花）',
  username      VARCHAR(50)  NOT NULL COMMENT '登录账号',
  password      VARCHAR(100) NOT NULL COMMENT '密码（BCrypt）',
  role          VARCHAR(20)  NOT NULL COMMENT '角色 USER/ENTERPRISE/ADMIN',
  enterprise_id BIGINT                COMMENT '关联企业ID',
  status        TINYINT     DEFAULT 1 COMMENT '状态 0-禁用 1-正常',
  create_time   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_username (username),
  KEY idx_enterprise (enterprise_id)
) ENGINE = InnoDB COMMENT = '系统用户';

-- -------------------------------------------------------------
-- 3. 商品表：登记即上链主体的基本信息
-- -------------------------------------------------------------
DROP TABLE IF EXISTS product;
CREATE TABLE product (
  product_id    BIGINT       NOT NULL COMMENT '商品ID（雪花）',
  name          VARCHAR(100) NOT NULL COMMENT '商品名称',
  category      VARCHAR(50)           COMMENT '商品类别',
  description   VARCHAR(500)          COMMENT '商品描述',
  image_url     VARCHAR(255)          COMMENT '商品实物图（/api/file/... 回读）',
  current_owner VARCHAR(100)          COMMENT '当前权属人（企业ID或链上地址）',
  status        TINYINT     DEFAULT 0 COMMENT '状态 0-登记 1-在售 2-交易中 3-已转移',
  register_by   BIGINT                COMMENT '登记人',
  register_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
  PRIMARY KEY (product_id),
  KEY idx_owner (current_owner),
  KEY idx_register_time (register_time)
) ENGINE = InnoDB COMMENT = '商品';

-- -------------------------------------------------------------
-- 4. 商品码表：一物一码，登记时生成二维码
-- -------------------------------------------------------------
DROP TABLE IF EXISTS product_code;
CREATE TABLE product_code (
  code_id       BIGINT      NOT NULL COMMENT '码ID（雪花）',
  product_id    BIGINT      NOT NULL COMMENT '商品ID',
  code          VARCHAR(64) NOT NULL COMMENT '一物一码唯一编码，如 P123456',
  qr_content    VARCHAR(255)         COMMENT '二维码内容（验真URL）',
  generate_time DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  status        TINYINT    DEFAULT 0 COMMENT '状态 0-有效 1-作废',
  PRIMARY KEY (code_id),
  UNIQUE KEY uk_code (code),
  KEY idx_product (product_id)
) ENGINE = InnoDB COMMENT = '商品码（一物一码）';

-- -------------------------------------------------------------
-- 5. 交易记录表：权属转移，落链后回填区块高度与交易哈希
-- -------------------------------------------------------------
DROP TABLE IF EXISTS trade_record;
CREATE TABLE trade_record (
  trade_id     BIGINT      NOT NULL COMMENT '交易ID（雪花）',
  product_id   BIGINT      NOT NULL COMMENT '商品ID',
  seller       VARCHAR(100)         COMMENT '出让方',
  buyer        VARCHAR(100)         COMMENT '受让方',
  trade_time   DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '交易时间',
  block_height BIGINT               COMMENT '链上区块高度',
  tx_hash      VARCHAR(128)         COMMENT '链上交易哈希',
  PRIMARY KEY (trade_id),
  KEY idx_product (product_id),
  KEY idx_tx_hash (tx_hash)
) ENGINE = InnoDB COMMENT = '交易记录（权属转移）';

-- -------------------------------------------------------------
-- 6. 质检记录表（模块五）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS quality_record;
CREATE TABLE quality_record (
  quality_id  BIGINT      NOT NULL COMMENT '质检ID（雪花）',
  product_id  BIGINT      NOT NULL COMMENT '商品ID',
  org         VARCHAR(100)         COMMENT '质检机构',
  result      VARCHAR(20)          COMMENT '质检结果 合格/不合格',
  report_file VARCHAR(255)         COMMENT '质检报告文件（MinIO对象名）',
  `time`      DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '质检时间',
  PRIMARY KEY (quality_id),
  KEY idx_product (product_id)
) ENGINE = InnoDB COMMENT = '质检记录';

-- -------------------------------------------------------------
-- 7. 维修记录表（模块五）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS repair_record;
CREATE TABLE repair_record (
  repair_id BIGINT      NOT NULL COMMENT '维修ID（雪花）',
  product_id BIGINT     NOT NULL COMMENT '商品ID',
  repairer  VARCHAR(100)         COMMENT '维修方',
  content   VARCHAR(500)         COMMENT '维修内容',
  voucher   VARCHAR(255)         COMMENT '维修凭证（MinIO对象名）',
  `time`    DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '维修时间',
  PRIMARY KEY (repair_id),
  KEY idx_product (product_id)
) ENGINE = InnoDB COMMENT = '维修记录';

-- -------------------------------------------------------------
-- 8. 上链存证记录表：业务数据哈希上链后的回执
-- -------------------------------------------------------------
DROP TABLE IF EXISTS chain_record;
CREATE TABLE chain_record (
  evidence_id  BIGINT      NOT NULL COMMENT '存证ID（雪花）',
  biz_type     VARCHAR(32)          COMMENT '业务类型 trade/quality/repair',
  biz_id       BIGINT               COMMENT '关联业务ID（统一存商品ID，便于按商品反查存证）',
  origin_hash  VARCHAR(128)         COMMENT '业务数据原文哈希',
  block_height BIGINT               COMMENT '区块高度',
  tx_hash      VARCHAR(128)         COMMENT '交易哈希（存证这笔交易自己的哈希）',
  chain_time   DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '上链时间',
  PRIMARY KEY (evidence_id),
  KEY idx_tx_hash (tx_hash),
  KEY idx_biz_type (biz_type),
  KEY idx_biz_id (biz_id)
) ENGINE = InnoDB COMMENT = '上链存证记录';

-- -------------------------------------------------------------
-- 9. 验真日志表（模块七）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS verify_log;
CREATE TABLE verify_log (
  verify_id  BIGINT      NOT NULL COMMENT '验真ID（雪花）',
  product_id BIGINT               COMMENT '商品ID',
  code_id    BIGINT               COMMENT '一物一码ID',
  verifier   VARCHAR(100)         COMMENT '验真方',
  result     TINYINT              COMMENT '验真结果 0-失败 1-成功',
  ip         VARCHAR(64)          COMMENT '验真IP',
  `time`     DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '验真时间',
  PRIMARY KEY (verify_id),
  KEY idx_product (product_id)
) ENGINE = InnoDB COMMENT = '验真日志';

-- -------------------------------------------------------------
-- 10. 操作日志表：监管审计留痕（模块八）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS operation_log;
CREATE TABLE operation_log (
  log_id   BIGINT      NOT NULL COMMENT '日志ID（雪花）',
  operator VARCHAR(100)         COMMENT '操作人',
  action   VARCHAR(64)          COMMENT '操作类型',
  detail   VARCHAR(500)         COMMENT '操作详情',
  `time`   DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (log_id),
  KEY idx_time (`time`)
) ENGINE = InnoDB COMMENT = '操作日志（审计留痕）';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 演示数据（可选执行）
-- 账号密码统一在注册时用 BCrypt 加密；此处只预置企业主体，
-- 登录账号请用接口 POST /api/auth/register 创建，避免明文入库。
-- 注：以下企业名、信用代码、联系人、电话均为虚构演示数据，与真实主体无关。
-- =============================================================
INSERT INTO enterprise (enterprise_id, name, credit_code, legal_person, contact) VALUES
  (1, '新余智链科技有限公司',    '91360500MA35EXAMPLE01', '陈明',   '0790-6660001'),
  (2, '江西省质检中心',          '12360500MB0EXAMPLE2X',  '李强',   '0790-6660002'),
  (3, '赣西数码维修服务部',      '91360502MA3EXAMPLE03',  '王芳',   '0790-6660003');
