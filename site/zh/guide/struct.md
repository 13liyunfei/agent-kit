# 结构化输出

让模型"返回 JSON"在它不老实之前都好用——多一句解释、多个尾逗号、大小写变了，都会让解析崩掉。结构化输出绑定 Schema，并在校验失败时自动重试。

## 问题

```java
String raw = model.chat("返回含 action 和 level 字段的 JSON");
// "好的！JSON 如下：\n```json\n{...}\n```"   <- 解析失败
```

## 解法

```java
public record Decision(String action, int level) {}

StructuredChatModel structured = new StructuredChatModel(model);
Decision d = structured.chat("给这个变更定级", Decision.class, 3);

d.action();  // "reject"
d.level();   // 3
```

Schema 由目标类型推导，追加进 prompt，响应回来后做校验。失败则重试，次数用完后抛异常。

## 为什么重试是它的一部分

一次重试就能把大部分畸形响应救回来。没有它，你会在每个调用点重复写同样的循环——而且通常会忘记带上那条能让重试成功的错误信息。

## 行为表

| 情况 | 结果 |
|------|------|
| 首次即合法 | 直接解析返回 |
| 非法 JSON | 重试，直到设定的次数上限 |
| 达上限仍非法 | 抛异常，并附带最后一次响应 |
| 模型调用失败 | 直接抛出——这不是格式问题 |

结构化输出是 `ChatModel` 之上的增强，不是替代品。同一个模型实例在其他地方照常使用。
