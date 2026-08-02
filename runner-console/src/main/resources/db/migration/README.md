# Flyway 数据库迁移脚本目录

本目录为 Flyway 增量迁移脚本目录（classpath:db/migration），随 `runner-console` 打包进 JAR，应用启动时自动执行。

## 约定

- 首次初始化由仓库根目录 `sql/db_init.sql` 完成（Flyway 基线版本为 V1，不在此目录维护）。
- 增量脚本命名：`V{版本}__{描述}.sql`，版本号从 `V2` 开始递增，如 `V2__workflow_add_column.sql`。
- 版本号必须全局唯一且不重复使用；已发布的脚本禁止修改（Flyway 会做 checksum 校验）。
- 每个脚本内禁止使用 `DROP TABLE` 等破坏性语句，需基于当前结构编写 `ALTER` / `CREATE` / `UPDATE`。
- 存量库首次升级：`runner-console` 启动时 Flyway 检测到非空库且无历史表，自动执行 baseline（标记 V1 已应用），随后按序执行本目录全部增量脚本。
- 新库：先执行 `sql/db_init.sql` 完成首次初始化，再启动应用即可。
