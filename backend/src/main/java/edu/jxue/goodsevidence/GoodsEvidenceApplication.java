package edu.jxue.goodsevidence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商品交易存证系统 - 后端启动类
 */
@SpringBootApplication
@MapperScan("edu.jxue.goodsevidence.mapper")
public class GoodsEvidenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodsEvidenceApplication.class, args);
    }
}
