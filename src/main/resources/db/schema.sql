-- TaskWhisper 数据模型（H2 测试库 MODE=MySQL 与 MySQL 8 通用语法）
-- 表名 t_ 前缀：国内后端惯例；user 是 H2 保留字，加前缀直接避雷

CREATE TABLE IF NOT EXISTS t_user (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME     NOT NULL,
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS t_category (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(32) NOT NULL,
    created_at DATETIME    NOT NULL,
    -- 组合唯一键已覆盖 user_id 前缀，无需再单独建 user_id 索引
    CONSTRAINT uk_category_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_category_user FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS t_task (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    category_id BIGINT       NULL,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    priority    VARCHAR(8)   NOT NULL DEFAULT '中',
    status      VARCHAR(16)  NOT NULL DEFAULT '待办',
    due_time    DATETIME     NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    INDEX idx_task_user (user_id),
    INDEX idx_task_status (status),
    INDEX idx_task_due (due_time),
    INDEX idx_task_category (category_id),
    CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_category FOREIGN KEY (category_id) REFERENCES t_category (id) ON DELETE SET NULL
);
