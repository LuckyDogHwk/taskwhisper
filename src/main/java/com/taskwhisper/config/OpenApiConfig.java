package com.taskwhisper.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档元信息 + JWT 认证方案（Swagger UI 右上角 Authorize 可直接贴 token 调试）。
 * 文档地址：/swagger-ui/index.html，OpenAPI JSON：/v3/api-docs（已在安全白名单内）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI taskwhisperOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TaskWhisper 一句话待办 API")
                        .description("任务管理 + DeepSeek 自然语言建任务。认证方式：登录后把 token 填入 Authorize（Bearer）。")
                        .version("0.1.0"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
