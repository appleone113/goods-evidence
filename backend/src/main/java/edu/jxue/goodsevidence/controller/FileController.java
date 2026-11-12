package edu.jxue.goodsevidence.controller;

import edu.jxue.goodsevidence.service.impl.LocalStorageServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地存储文件回读接口（storage.type=local 时启用）
 *
 * <p>LocalStorageServiceImpl.getUrl 返回 /api/file/{对象名}，
 * 由本控制器把磁盘文件写出来，供前端展示二维码、质检修凭证。
 * 该路径已在 WebConfig 中从 JWT 拦截里放行，保证消费者扫码页能直接加载图片。</p>
 */
@RestController
@RequestMapping("/api/file")
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class FileController {

    @Autowired
    private LocalStorageServiceImpl localStorageService;

    /** 通配匹配：/api/file/qr/P123.png、/api/file/uuid_报告.pdf 均落到此处 */
    @GetMapping("/**")
    public void download(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String prefix = "/api/file/";
        if (path == null || path.length() <= prefix.length()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String objectName = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8);
        Path file = localStorageService.resolve(objectName);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String contentType = guessContentType(objectName);
        response.setContentType(contentType);
        response.setContentLengthLong(Files.size(file));
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file, out);
        }
    }

    private String guessContentType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF_VALUE;
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
