package edu.jxue.goodsevidence.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 两级缓存封装（模块九·性能优化）。
 * <p>L1：进程内 ConcurrentHashMap（带 TTL），无外部依赖，永远可用；
 * L2：Redis 分布式缓存（带 TTL），多实例共享。
 * <p>采用 cache-aside 模式：读时 L1 → L2 → DB/链，并逐级回填；写时两级同时失效。
 * <p>降级策略：Redis 不可用（未部署 / 连接失败）时自动只用 L1，
 * 不阻断主流程，保证"缓存是加速项而非依赖项"。
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    /** 验真结果缓存键前缀（与 VerifyServiceImpl 共用，避免两处硬编码漂移） */
    public static final String VERIFY_PREFIX = "verify:";

    /** L1 本地缓存条目上限，防止长期运行内存无限增长 */
    private static final int L1_MAX_ENTRIES = 10_000;

    /** L1：key -> [JSON 值, 过期时间戳(ms)] */
    private final Map<String, Object[]> l1 = new ConcurrentHashMap<>();

    /** Redis 熔断：连接失败后 60 秒内不再尝试，避免每次请求都付出连接超时代价 */
    private volatile long redisDownUntil = 0L;

    /**
     * 缓存总开关（默认开启）。
     * <p>关闭时读写一律旁路，等价于"未做缓存优化"的直查实现，
     * 供性能对照实验作为基准组使用：--app.cache.enabled=false
     */
    @Value("${app.cache.enabled:true}")
    private boolean cacheEnabled;

    private boolean redisUsable() {
        return cacheEnabled && System.currentTimeMillis() >= redisDownUntil;
    }

    private void markRedisDown(Exception e) {
        redisDownUntil = System.currentTimeMillis() + 60_000L;
        log.info("Redis 不可用，60 秒内只用本地缓存（原因：{}）", e.toString());
    }

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /** 读缓存原始字符串：先 L1（本地），再 L2（Redis） */
    public String getRaw(String key) {
        if (!cacheEnabled) {
            return null;   // 对照实验基准组：缓存关闭
        }
        Object[] hit = l1.get(key);
        if (hit != null) {
            if ((Long) hit[1] >= System.currentTimeMillis()) {
                return (String) hit[0];
            }
            l1.remove(key);   // 已过期，顺手清理
        }
        try {
            if (!redisUsable()) {
                return null;
            }
            String raw = redisTemplate.opsForValue().get(key);
            if (raw != null) {
                putL1(key, raw, 60);   // L2 命中也回填 L1，缩短后续访问路径
            }
            return raw;
        } catch (Exception e) {
            markRedisDown(e);
            return null;
        }
    }

    /** 写 L1（带 TTL 秒），超过上限时先做一次整体淘汰，避免无界增长 */
    private void putL1(String key, String value, long ttlSeconds) {
        if (l1.size() >= L1_MAX_ENTRIES) {
            long now = System.currentTimeMillis();
            l1.entrySet().removeIf(en -> (Long) en.getValue()[1] < now);
            if (l1.size() >= L1_MAX_ENTRIES) {
                l1.clear();
            }
        }
        l1.put(key, new Object[]{value, System.currentTimeMillis() + ttlSeconds * 1000L});
    }

    /** 写缓存（带 TTL 秒）：L1 必写，L2 尽力写 */
    public void putRaw(String key, String value, long ttlSeconds) {
        if (!cacheEnabled) {
            return;   // 对照实验基准组：缓存关闭
        }
        putL1(key, value, ttlSeconds);
        try {
            if (redisUsable()) {
                redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            markRedisDown(e);
        }
    }

    /** 删除缓存（写操作后失效）：两级同时失效 */
    public void delete(String key) {
        l1.remove(key);
        try {
            if (redisUsable()) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            markRedisDown(e);
        }
    }

    /**
     * 商品业务数据发生变更（登记/质检/维修/权属转移）后，失效该商品的验真缓存。
     * <p>验真缓存按「码值」建键，而码值有 "P{id}" 与纯 "{id}" 两种调用形态，
     * 因此两种键都要清，避免写后仍读到 60 秒内的旧验真结果（脏读）。
     */
    public void evictVerify(Long productId) {
        if (productId == null) {
            return;
        }
        delete(VERIFY_PREFIX + "P" + productId);
        delete(VERIFY_PREFIX + productId);
    }

    /** 读缓存并反序列化 JSON 对象 */
    public <T> T getJson(String key, Class<T> type) {
        String raw = getRaw(key);
        if (raw == null) return null;
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("缓存反序列化失败: {}", key, e);
            return null;
        }
    }

    /** 序列化对象并写入缓存 */
    public void putJson(String key, Object value, long ttlSeconds) {
        try {
            putRaw(key, objectMapper.writeValueAsString(value), ttlSeconds);
        } catch (Exception e) {
            log.warn("缓存序列化失败，跳过: {}", key, e);
        }
    }
}
