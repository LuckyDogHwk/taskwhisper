package com.taskwhisper.controller;

import com.taskwhisper.ai.DeepSeekClient;
import com.taskwhisper.dto.AuthResponse;
import com.taskwhisper.dto.RegisterRequest;
import com.taskwhisper.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NL 建任务接口。
 * 默认配置无 Key（application.yml 的 ${DEEPSEEK_API_KEY:} 为空）→ 503 诚实降级。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NlControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;

    private String token;

    @BeforeEach
    void registerAndLogin() {
        AuthResponse resp = authService.register(
                new RegisterRequest("ctrl_nl_" + System.nanoTime() % 100000, "pass123456"));
        token = resp.token();
    }

    @Test
    void withoutApiKeyReturns503WithClearMessage() throws Exception {
        mockMvc.perform(post("/api/tasks/nl")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"明天下午三点开会\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value(
                        "未配置 DeepSeek API Key，自然语言建任务不可用"));
    }

    @Test
    void blankTextReturns400() throws Exception {
        mockMvc.perform(post("/api/tasks/nl")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/tasks/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"明天开会\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 配了 Key 的正常链路：mock DeepSeek 返回合法 JSON → 解析 → 落库 → 分类自动复用。
     */
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = "taskwhisper.ai.api-key=test-key")
    static class WithKey {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private AuthService authService;
        @MockitoBean
        private DeepSeekClient deepSeekClient;

        @Test
        void parsesAndPersistsWithCategoryReuse() throws Exception {
            AuthResponse resp = authService.register(
                    new RegisterRequest("ctrl_nlkey_" + System.nanoTime() % 100000, "pass123456"));
            String token = resp.token();

            when(deepSeekClient.chatJson(anyString(), anyString())).thenReturn(
                    "{\"title\":\"项目周会\",\"description\":\"讨论迭代计划\","
                            + "\"priority\":\"高\",\"due_time\":\"2026-10-20 15:00:00\",\"category\":\"工作\"}");

            mockMvc.perform(post("/api/tasks/nl")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"明天下午三点开项目周会，高优先级，工作分类\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.title").value("项目周会"))
                    .andExpect(jsonPath("$.data.priority").value("高"))
                    .andExpect(jsonPath("$.data.categoryName").value("工作"));

            // 第二次同分类名 → 复用，不新建
            when(deepSeekClient.chatJson(anyString(), anyString())).thenReturn(
                    "{\"title\":\"第二次会议\",\"category\":\"工作\"}");
            mockMvc.perform(post("/api/tasks/nl")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"再来一个会\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        void invalidLlmOutputReturns502AndPersistsNothing() throws Exception {
            AuthResponse resp = authService.register(
                    new RegisterRequest("ctrl_nlbad_" + System.nanoTime() % 100000, "pass123456"));
            String token = resp.token();

            when(deepSeekClient.chatJson(anyString(), anyString())).thenReturn("这不是JSON");

            mockMvc.perform(post("/api/tasks/nl")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"随便说点什么\"}"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value(502));

            // 校验失败不落库
            mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }
}
