package com.smarttrade.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域以及拦截器全局配置 (允许 Vue3 前端调用)
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Autowired
    private AdminInterceptor adminInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
      registry.addMapping("/**") // 所有接口
              .allowCredentials(true) // 是否发送 Cookie
              .allowedOriginPatterns("*") // 支持域
              .allowedMethods(new String[]{"GET", "POST", "PUT", "DELETE", "OPTIONS"}) // 支持方法, 必须加OPTIONS
              .allowedHeaders("*")
              .exposedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 放行 登录/注册 和 接口文档 Knife4j 等相关路径
        registry.addInterceptor(jwtInterceptor)
                .order(1)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/user/login",
                    "/user/register",
                    "/doc.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**"
                );

        // 管理后台权限拦截器：仅 /admin/** 触发，依赖 JwtInterceptor 已写入 UserContext
        registry.addInterceptor(adminInterceptor)
                .order(2)
                .addPathPatterns("/admin/**");
    }
}