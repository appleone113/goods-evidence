package edu.jxue.goodsevidence.service.impl;

import edu.jxue.goodsevidence.service.FileStorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * MinIO 对象存储实现（storage.type=minio）
 * 商品二维码、质检报告、维修凭证统一存桶，访问地址由 endpoint + bucket + 对象名拼出
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class MinioStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;
    @Value("${minio.endpoint}")
    private String endpoint;

    public MinioStorageServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /** 启动时确保桶存在 */
    @PostConstruct
    public void init() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    @Override
    public String upload(String objectName, InputStream stream, long size, String contentType) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(stream, size, -1)
                .contentType(contentType)
                .build());
        return objectName;
    }

    @Override
    public String uploadFile(MultipartFile file) throws Exception {
        String name = UUID.randomUUID() + "_" + file.getOriginalFilename();
        upload(name, file.getInputStream(), file.getSize(), file.getContentType());
        return name;
    }

    @Override
    public String getUrl(String objectName) {
        return endpoint + "/" + bucket + "/" + objectName;
    }
}
