package edu.jxue.goodsevidence.config;

import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * FISCO BCOS v3 SDK 配置
 * <p>SDK 通过 TOML 配置文件构建 BcosSDK，该文件含群组/节点/证书路径。
 * 运行时由 application.yml 的 fisco.config-path 指向 <b>jar 外</b>的 backend/fisco/config.toml；
 * classpath 下这份只是参考模板，改它不会生效。
 * 证书文件必须使用 SDK 默认名 ca.crt / sdk.crt / sdk.key，与 config.toml 同目录，
 * 从节点机拷贝；改名成 ssl.crt / ssl.key 会导致 SDK 证书配置为空、发交易报 -4010。</p>
 * <p>生产部署务必将 fisco.config-path 指向 jar 外的真实文件路径，而非 classpath 资源
 * （fat jar 内证书读不到）。</p>
 */
@Configuration
@ConditionalOnProperty(name = "fisco.enabled", havingValue = "true")
public class FiscoConfig {

    /** config.toml 路径；默认值虽是 classpath 资源，但 application.yml 已覆盖为 ./fisco/config.toml（jar 外） */
    @Value("${fisco.config-path:classpath:fisco/config.toml}")
    private String configPath;

    /** 群组ID：FISCO BCOS 3.x 是字符串形式的 group0/group1，不是数字 1/2 */
    @Value("${fisco.group-id}")
    private String groupId;

    @Bean
    public BcosSDK bcosSDK() throws Exception {
        String path = resolvePath(configPath);
        return BcosSDK.build(path);
    }

    @Bean
    public Client fiscoClient(BcosSDK sdk) {
        return sdk.getClient(groupId);
    }

    /**
     * 将 classpath: 资源解析为文件系统路径。
     * ⚠️ 打 fat jar 后 resource.getFile() 会抛 FileNotFoundException（资源在 jar 里不是文件），
     * 所以这里兜一层：取不到就复制到临时文件再返回。
     * 但证书同样在 jar 内读不到，因此**正式接链请把配置与证书放在 jar 外**，
     * 并把 fisco.config-path 指向外部绝对路径（如 ./fisco/config.toml）。
     */
    private String resolvePath(String path) throws Exception {
        if (!path.startsWith("classpath:")) {
            return path;
        }
        ClassPathResource resource = new ClassPathResource(path.substring("classpath:".length()));
        try {
            File file = resource.getFile();
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        } catch (Exception ignored) {
            // jar 内运行，走下面的临时文件分支
        }
        Path tmp = Files.createTempFile("fisco-config", ".toml");
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp.toAbsolutePath().toString();
    }
}
