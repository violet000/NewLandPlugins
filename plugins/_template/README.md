# 插件模板

1. 复制本目录为 `plugins/your-id/`（不要保留下划线前缀，否则不会被 Gradle 收录）。
2. 修改 `gradle.properties`、`AndroidManifest.xml` 中的 id / 类名。
3. 把 `TemplatePlugin` 改成你的实现，并补上对外 API。

详见仓库根目录 README「添加插件」。
