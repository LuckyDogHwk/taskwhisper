package com.taskwhisper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TaskWhisper · 一句话待办。
 * 任务管理系统：注册登录、任务 CRUD、分类/优先级/状态、统计，
 * 核心卖点是用 DeepSeek 把一句自然语言解析成结构化任务（POST /api/tasks/nl）。
 */
@SpringBootApplication
public class TaskWhisperApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskWhisperApplication.class, args);
    }
}
