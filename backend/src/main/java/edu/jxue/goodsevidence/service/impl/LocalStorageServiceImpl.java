package edu.jxue.goodsevidence.service.impl;

import edu.jxue.goodsevidence.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 本地磁盘存储实现（默认，storage.type=local）
 *
 * <p>把文件写到 storage.local-dir 指定目录，访问地址统一走 /api/file/{对象名}，
 * 由 FileController 回读。这样在没有 MinIO 的机器（答辩笔记本、教室电脑）上
 * 也能完整跑通「二维码生成 → 存储 → 前端展示」链路。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements FileStorageService {

    @Value("${storage.local-dir:./uploads}")
    private String localDir;

    private Path root;

    @PostConstruct
    public void init() {
        try {
            root = Paths.get(localDir).toAbsolutePath().normalize();
            Files.createDirectories(root);
            log.info("[本地存储] 目录已就绪：{}", root);
        } catch (Exception e) {
            log.warn("[本地存储] 目录创建失败，上传将不可用：{}", e.getMessage());
        }
    }

    @Override
    public String upload(String objectName, InputStream stream, long size, String contentType) throws Exception {
        Path target = resolve(objectName);
        Files.createDirectories(target.getParent());
        Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        return objectName;
    }

    @Override
    public String uploadFile(MultipartFile file) throws Exception {
        String name = UUID.randomUUID() + "_" + safeName(file.getOriginalFilename());
        upload(name, file.getInputStream(), file.getSize(), file.getContentType());
        return name;
    }

    @Override
    public String getUrl(String objectName) {
        return "/api/file/" + objectName;
    }

    /** 解析对象名到磁盘路径，并做路径穿越防护 */
    public Path resolve(String objectName) {
        String normalized = objectName.replace('\\', '/');
        while (normalized.contains("..") || normalized.contains("//")) {
            normalized = normalized.replace("..", "").replace("//", "/");
        }
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法文件名：" + objectName);
        }
        return target;
    }

    /** 存储根目录，供 FileController 回读 */
    public Path getRoot() {
        return root;
    }

    private String safeName(String original) {
        if (original == null || original.isBlank()) {
            return "unnamed";
        }
        return original.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
