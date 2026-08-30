# 构建与发布

## 环境要求

- JDK 17 及以上
- Maven 3.6 及以上

## 常用命令

```bash
mvn test                 # 跑全量测试（103 例）
mvn package              # 构建 jar
mvn install              # 安装到本地仓库
```

## 发布流程

正式版本发布到 Maven Central，坐标 `io.github.13liyunfei`：

```bash
mvn -Prelease clean verify    # 签名并检查产物
mvn -Prelease deploy          # 发布
```

`release` profile 会追加 sources jar、javadoc jar、GPG 签名与 Central Portal 发布插件。日常构建不激活它，所以 `mvn test` 从不需要签名密钥。

发布需要在仓库之外配置三项：

1. `~/.m2/settings.xml` 中 server id 为 `central` 的 Central Portal user token
2. GPG 密钥，其 id 与密码短语写在 `release` profile 属性里
3. `io.github.13liyunfei` 命名空间已在 central.sonatype.com 认领并通过验证

## 版本约定

遵循语义化版本。已发布产物不可变——版本一旦发布既不能替换也不能删除，因此 deploy 前务必先 verify。
