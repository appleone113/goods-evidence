package edu.jxue.goodsevidence.controller;

import edu.jxue.goodsevidence.common.Result;
import edu.jxue.goodsevidence.service.VerifyService;
import edu.jxue.goodsevidence.vo.LifecycleEvent;
import edu.jxue.goodsevidence.vo.LifecycleVO;
import edu.jxue.goodsevidence.vo.VerifyResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 扫码验真与溯源查询（模块七，公开接口）
 *
 * <p>两个入口分工：
 * <ul>
 *   <li>{@code /scan} 返回 JSON，供前端 axios、第三方系统调用；</li>
 *   <li>{@code /view} 返回自包含 HTML 验真页，是「一物一码」二维码里写的地址，
 *       消费者用手机直接扫码即可看到结果，无需登录、无需前端 dev server。</li>
 * </ul>
 * 两者都在 WebConfig 的 /api/verify/** 白名单内。</p>
 */
@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    @Autowired
    private VerifyService verifyService;

    @GetMapping("/scan")
    public Result<VerifyResultVO> scan(@RequestParam String code) {
        try {
            return Result.ok(verifyService.verify(code));
        } catch (Exception e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 手机扫码落地点：返回移动端友好的验真结果页 */
    @GetMapping("/view")
    public ResponseEntity<String> view(@RequestParam String code) {
        String html;
        try {
            html = PAGE_TPL.replace("{{PAGE}}", buildPage(verifyService.verify(code), code));
        } catch (Exception e) {
            html = PAGE_TPL.replace("{{PAGE}}", buildError(e.getMessage()));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/html;charset=UTF-8"));
        headers.setCacheControl("no-store");   // 验真结果不应被浏览器缓存
        return new ResponseEntity<>(html, headers, HttpStatus.OK);
    }

    /** 页面骨架（内联样式，手机浏览器无需任何外部资源） */
    private static final String PAGE_TPL = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>商品验真结果 · 商品交易存证系统</title>
            <style>
              *{box-sizing:border-box}
              body{margin:0;padding:18px;background:#f4f6fa;color:#1f2937;
                font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif}
              .wrap{max-width:520px;margin:0 auto}
              .brand{text-align:center;color:#64748b;font-size:12px;letter-spacing:2px;margin-bottom:14px}
              .card{background:#fff;border-radius:16px;box-shadow:0 4px 20px rgba(15,23,42,.07);
                padding:24px;margin-bottom:14px}
              .banner{text-align:center;padding:6px 0 20px}
              .badge{width:74px;height:74px;line-height:74px;border-radius:50%;font-size:38px;
                color:#fff;margin:0 auto 14px}
              .ok{background:#16a34a}.no{background:#dc2626}
              h1{font-size:21px;margin:0 0 8px}
              .msg{color:#64748b;font-size:13px;margin:0;line-height:1.6}
              table{width:100%;border-collapse:collapse;font-size:14px}
              td{padding:11px 0;border-bottom:1px solid #f1f3f7;vertical-align:top}
              tr:last-child td{border-bottom:none}
              td.k{color:#94a3b8;width:88px;white-space:nowrap}
              td.v{font-weight:500;word-break:break-all}
              .mono{font-family:ui-monospace,Consolas,monospace;font-size:13px}
              h2{font-size:15px;margin:0 0 16px;color:#334155}
              .tl{list-style:none;margin:0;padding:0 0 0 20px;border-left:2px solid #e2e8f0}
              .tl li{position:relative;padding:0 0 18px 14px}
              .tl li:last-child{padding-bottom:0}
              .dot{position:absolute;left:-27px;top:3px;width:12px;height:12px;border-radius:50%;
                background:#3b82f6;border:2px solid #fff;box-shadow:0 0 0 2px #e2e8f0}
              .t{font-size:14px;font-weight:600}
              .d{font-size:12px;color:#94a3b8;margin-top:4px;line-height:1.5}
              .foot{text-align:center;color:#94a3b8;font-size:11px;line-height:1.8;padding:6px 0 20px}
              .tag{display:inline-block;background:#eff6ff;color:#2563eb;border-radius:6px;
                padding:2px 8px;font-size:12px;margin-right:6px}
            </style>
            </head>
            <body><div class="wrap">
              <div class="brand">商品交易存证系统 · 区块链验真</div>
              {{PAGE}}
              <div class="foot">验真结果由链上存证与权属记录实时校验生成<br>本页面为公开查询，请勿扫描来源不明的二维码</div>
            </div></body></html>
            """;

    private String buildPage(VerifyResultVO vo, String code) {
        if (vo == null) {
            return buildError("未查询到验真结果");
        }
        boolean ok = vo.isAuthentic();
        LifecycleVO life = vo.getLifecycle();

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"card\">");
        if (vo.getImageUrl() != null && !vo.getImageUrl().isEmpty()) {
            sb.append("<div style=\"text-align:center;margin-bottom:16px\">")
              .append("<img src=\"").append(esc(vo.getImageUrl()))
              .append("\" alt=\"商品实物图\" style=\"max-width:100%;max-height:220px;border-radius:10px;border:1px solid #e2e8f0\">")
              .append("</div>");
        }
        sb.append("<div class=\"banner\">");
        sb.append("<div class=\"badge ").append(ok ? "ok" : "no").append("\">")
          .append(ok ? "✓" : "!").append("</div>");
        sb.append("<h1>").append(ok ? "验真通过" : "验真未通过").append("</h1>");
        sb.append("<p class=\"msg\">").append(esc(vo.getMessage())).append("</p>");
        sb.append("</div><table>");
        row(sb, "商品名称", esc(vo.getProductName()));
        row(sb, "一物一码", "<span class=\"mono\">" + esc(code) + "</span>");
        row(sb, "商品编号", "<span class=\"mono\">" + esc(String.valueOf(vo.getProductId())) + "</span>");
        if (life != null) {
            row(sb, "当前状态", "<span class=\"tag\">" + statusName(life.getStatus()) + "</span>");
        }
        sb.append("</table></div>");

        List<LifecycleEvent> events = life == null ? null : life.getEvents();
        if (events != null && !events.isEmpty()) {
            sb.append("<div class=\"card\"><h2>全生命周期履历</h2><ul class=\"tl\">");
            for (LifecycleEvent e : events) {
                sb.append("<li><span class=\"dot\"></span>");
                sb.append("<div class=\"t\">").append(esc(typeName(e.getType()))).append("</div>");
                sb.append("<div class=\"d\">").append(esc(e.getDescription()));
                if (e.getTime() != null) {
                    sb.append(" · ").append(esc(e.getTime().toString().replace('T', ' ')));
                }
                sb.append("</div></li>");
            }
            sb.append("</ul></div>");
        }
        return sb.toString();
    }

    private String buildError(String message) {
        return "<div class=\"card\"><div class=\"banner\">"
                + "<div class=\"badge no\">!</div><h1>无法完成验真</h1>"
                + "<p class=\"msg\">" + esc(message) + "</p></div>"
                + "<table><tr><td class=\"k\">建议</td><td class=\"v\">请确认二维码来源，或联系商家核实商品编码</td></tr></table></div>";
    }

    private void row(StringBuilder sb, String key, String value) {
        sb.append("<tr><td class=\"k\">").append(key)
          .append("</td><td class=\"v\">").append(value).append("</td></tr>");
    }

    private String statusName(Integer s) {
        if (s == null) return "未知";
        return switch (s) {
            case 0 -> "已登记";
            case 1 -> "在售";
            case 2 -> "交易中";
            case 3 -> "已转移";
            default -> "状态 " + s;
        };
    }

    private String typeName(String t) {
        if (t == null) return "存证记录";
        return switch (t) {
            case "REGISTER" -> "商品登记";
            case "TRADE" -> "权属转移";
            case "QUALITY" -> "质检记录";
            case "REPAIR" -> "维修记录";
            default -> t;
        };
    }

    /** 防 XSS：商品名/描述来自用户输入，必须转义后再写进 HTML */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
