package edu.jxue.goodsevidence.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储抽象层（隔离 MinIO 与本地磁盘两种实现）
 *
 * <p>业务代码只依赖本接口。通过 application.yml 的 storage.type 切换实现：
 *   local（默认）—— 落在本机 upload 目录，无外部中间件即可运行，适合答辩/演示；
 *   minio      —— 落在 MinIO 对象存储，用于生产或论文中的分布式存储方案描述。</p>
 */
public interface FileStorageService {

    /** 上传输入流，返回对象名 */
    String upload(String objectName, InputStream stream, long size, String contentType) throws Exception;

    /** 上传 MultipartFile（质检报告、维修凭证） */
    String uploadFile(MultipartFile file) throws Exception;

    /** 拼接可访问地址 */
    String getUrl(String objectName);
}
