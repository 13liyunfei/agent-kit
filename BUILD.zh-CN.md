# 构建与发布流程

## 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+ | 编译目标 Java 17 |
| Maven | 3.9+ | 构建 / 测试 / 发布 |
| 网络 | 可选 | 首次构建需拉取 jackson / slf4j / junit 依赖（离线可用 `-o`） |

## 构建命令

```bash
# 单元测试（38 例，无需联网依赖，可 -o 离线）
mvn test

# 打 jar 包（产出 target/agent-kit-0.1.0.jar）
mvn package

# 安装到本地 Maven 仓库（~/.m2/repository/com/codereview/agent-kit/0.1.0/）
mvn install

# 跳过测试快速打包
mvn package -DskipTests
```

> 使用方项目在 pom.xml 引入 `com.codereview:agent-kit:0.1.0` 前，需先执行 `mvn install` 把构件装入本地仓库（或配置私有仓库地址）。

## 版本约定

- 当前版本：`0.1.0`（首个可复用里程碑）
- 版本号在 `pom.xml` 的 `<version>` 声明，发布时同步更新：
  - `README.md` / `README.zh-CN.md` 的引入示例
  - 组件清单如有增减，同步两份 README 的组件表
- 语义化版本：`MAJOR.MINOR.PATCH`
  - MAJOR：破坏性 API 变更（如 ChatModel 签名变化）
  - MINOR：向后兼容的新组件 / 新扩展点
  - PATCH：缺陷修复

## 发布流程

```bash
# 1. 全量测试通过
mvn test

# 2. 打包验证
mvn package

# 3. 安装到本地仓库（供本机其他项目依赖）
mvn install

# 4.（可选）发布到私有 Nexus / 中央仓库
mvn deploy   # 需在 settings.xml 配置仓库认证
```

## 变更检查清单

提交前确认：

- [ ] `mvn test` 全绿（38 例）
- [ ] 新增组件在 `README.md`（英文）+ `README.zh-CN.md`（中文）组件表中登记
- [ ] 新增扩展点 SPI 在两张 README 的扩展点表中登记
- [ ] 组件包符合 `com.codereview.kit.*` 命名，仅依赖 JDK / jackson / slf4j
- [ ] 对外 API 带 Javadoc，说明设计意图与使用方式
