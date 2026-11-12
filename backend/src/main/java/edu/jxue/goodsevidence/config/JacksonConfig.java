package edu.jxue.goodsevidence.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局把 Long 序列化为字符串（模块六/七 联调必配）
 *
 * <p>系统主键是 MyBatis-Plus 雪花 ID（19 位，约 2.1e18），远超 JS 的
 * Number.MAX_SAFE_INTEGER（2^53-1 ≈ 9.007e15）。前端 JSON.parse 后精度丢失，
 * 末尾几位会被抹成 0，例如：</p>
 * <pre>
 *   后端真实值  2115355817291591682
 *   前端拿到    2115355817291591700   ← 末两位被截断
 * </pre>
 *
 * <p>后果很隐蔽：页面能正常渲染（显示的错误 ID 不报错），但一旦拿这个 ID 去请求后端
 * ——查履历、选商品做质检/维修/权属转移——就会查不到商品或操作到错误记录。
 *
 * <p>序列化成字符串后前端原样传递，精度完整；后端用 Long 接收时 Jackson 会自动转回，
 * 业务代码零改动。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
