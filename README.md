# Access Control Prototype

简要说明：这是一个基于 Swing 的原型应用，实现了简化版的 Badge/Reader/Resource 模拟、数据库连接、事件模拟与实时可视化。项目不使用 Maven 或 Gradle，直接通过 `javac` / `java` 运行。

目录结构（关键）:
- src/com/bigcomp/accesscontrol/... : Java 源码
- resources/img/ : 放置 `1.png` 与 `2.png`（地图等）
- config.properties : 数据库连接配置
- compile.bat / run.bat : Windows 下编译与运行脚本

快速上手:
1. 先在数据库里运行 `insertDatabase.txt` 来建立并填充测试数据（推荐 MySQL）。
2. 编辑 `config.properties`，填写正确的 `db.url`, `db.user`, `db.password`。
3. 在 Windows 终端运行 `compile.bat`，然后 `run.bat` 启动程序。

注意:
- 程序在启动时会尝试创建 `AccessLogs` 表（如果不存在），并从已有表加载 Badge、Readers、Resources、Groups、Profiles 等基本信息。
- 如果找不到图片，程序会以占位方式继续运行。

如需我继续：我会接着实现数据库访问与模型类，然后实现访问控制（ARP）、模拟器与 UI 的详细功能。