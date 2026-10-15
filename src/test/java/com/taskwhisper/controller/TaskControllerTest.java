package com.taskwhisper.controller;

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
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务/统计/分类接口端到端：真实安全链 + 真实 JWT + 真实 H2。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;

    private String token;
    private Long userId;

    @BeforeEach
    void registerAndLogin() {
        AuthResponse resp = authService.register(
                new RegisterRequest("ctrl_task_" + System.nanoTime() % 100000, "pass123456"));
        token = resp.token();
        userId = resp.userId();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Test
    void createThenListThenStatsRoundtrip() throws Exception {
        mockMvc.perform(authed(post("/api/tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"写周报\",\"priority\":\"高\",\"dueTime\":\"2026-12-31T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("写周报"))
                .andExpect(jsonPath("$.data.priority").value("高"))
                .andExpect(jsonPath("$.data.status").value("待办"));

        mockMvc.perform(authed(get("/api/tasks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("写周报"));

        mockMvc.perform(authed(get("/api/tasks/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusCounts.待办").value(1));
    }

    @Test
    void filtersByStatusAndPriority() throws Exception {
        mockMvc.perform(authed(post("/api/tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"已完成任务\",\"status\":\"已完成\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(authed(post("/api/tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"低优先任务\",\"priority\":\"低\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/tasks").param("status", "已完成")))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("已完成任务"));

        mockMvc.perform(authed(get("/api/tasks").param("priority", "低")))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("低优先任务"));

        mockMvc.perform(authed(get("/api/tasks").param("status", "待办").param("priority", "高")))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void partialUpdateWithStatusOnlyWorks() throws Exception {
        mockMvc.perform(authed(post("/api/tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"内联改状态\"}"))
                .andExpect(jsonPath("$.data.id").isNumber());

        // PUT 只带 status：回归锚点——TaskUpdateRequest.title 不允许拦下部分更新
        String id = mockMvc.perform(authed(get("/api/tasks")))
                .andReturn().getResponse().getContentAsString();
        Long taskId = extractId(id);

        mockMvc.perform(authed(put("/api/tasks/" + taskId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"已完成\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已完成"))
                .andExpect(jsonPath("$.data.title").value("内联改状态"));
    }

    @Test
    void invalidEnumReturns400() throws Exception {
        mockMvc.perform(authed(post("/api/tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"非法枚举\",\"priority\":\"紧急\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void zeroPageReturns400() throws Exception {
        mockMvc.perform(authed(get("/api/tasks").param("page", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void crossUserAccessReturns404() throws Exception {
        // 用户 A 建任务
        mockMvc.perform(authed(post("/api/tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"A 的任务\"}"))
                .andExpect(status().isOk());
        Long taskId = extractId(mockMvc.perform(authed(get("/api/tasks")))
                .andReturn().getResponse().getContentAsString());

        // 用户 B 越权访问 → 404（存在性与归属同判，不泄露资源存在信息）
        AuthResponse other = authService.register(new RegisterRequest("ctrl_other_" + System.nanoTime() % 100000, "pass123456"));
        mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void deleteOwnTaskThenGone() throws Exception {
        mockMvc.perform(authed(post("/api/tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"待删除\"}"))
                .andExpect(status().isOk());
        Long taskId = extractId(mockMvc.perform(authed(get("/api/tasks")))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(authed(delete("/api/tasks/" + taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(authed(get("/api/tasks/" + taskId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryCreateIsIdempotentAndListed() throws Exception {
        mockMvc.perform(authed(post("/api/categories"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学习\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("学习"));

        String first = mockMvc.perform(authed(post("/api/categories"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学习\"}"))
                .andReturn().getResponse().getContentAsString();
        assertThat(first).contains("\"name\":\"学习\"");

        mockMvc.perform(authed(get("/api/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void categoryScopedPerUser() throws Exception {
        mockMvc.perform(authed(post("/api/categories"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"私密分类\"}"))
                .andExpect(status().isOk());

        AuthResponse other = authService.register(new RegisterRequest("ctrl_scope_" + System.nanoTime() % 100000, "pass123456"));
        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /** 从列表响应 JSON 提取第一条记录的 id（响应为 {"code":0,...,"data":{"total":...,"records":[{"id":N,...}]}}） */
    private Long extractId(String json) {
        int idx = json.indexOf("\"id\":");
        if (idx < 0) {
            throw new IllegalStateException("响应中未找到 id 字段: " + json);
        }
        String rest = json.substring(idx + 5);
        int end = 0;
        while (end < rest.length() && (Character.isDigit(rest.charAt(end)) || rest.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(rest.substring(0, end));
    }
}
