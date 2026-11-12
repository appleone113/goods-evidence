package edu.jxue.goodsevidence.config;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.jxue.goodsevidence.common.JwtUtil;
import edu.jxue.goodsevidence.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 拦截器：校验令牌，注入 userId / role 到 request
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return writeFail(response, "未登录或令牌缺失");
        }
        try {
            DecodedJWT jwt = jwtUtil.verify(auth.substring(7));
            request.setAttribute("userId", jwt.getClaim("userId").asLong());
            request.setAttribute("role", jwt.getClaim("role").asString());
            return true;
        } catch (Exception e) {
            return writeFail(response, "令牌无效或已过期");
        }
    }

    private boolean writeFail(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(200);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(new ObjectMapper().writeValueAsString(Result.fail(401, msg)));
        return false;
    }
}
