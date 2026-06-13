# Java 21 与后端模块分层学习笔记

本文记录当前 Java 后端使用的 JDK 21 写法、多模块 Maven 分层，以及与 JDK 8 常见写法的区别。

## 当前 JDK 版本

当前后端 Maven 父工程在 `backend-java/pom.xml` 中配置：

```xml
<java.version>21</java.version>
```

因此本项目按 Java 21 编译运行，并在 Spring Boot 3.3.5 上启用部分 Java 21 友好的写法。

## Maven 模块分层

当前 Java 后端拆成 5 个 Maven 子模块：

| 模块 | 职责 | 可依赖 |
| --- | --- | --- |
| `agent-domain` | 领域对象、MySQL 持久化实体 | 只依赖 MyBatis Plus 注解 |
| `agent-infrastructure` | MyBatis Plus Mapper、基础设施实现 | `agent-domain` |
| `agent-application` | 业务服务、事务边界、DTO、实体到 DTO 转换 | `agent-domain`、`agent-infrastructure` |
| `agent-api` | Controller、HTTP 路由、入参校验 | `agent-application` |
| `agent-boot` | 启动类、配置属性、可执行 Jar 打包 | `agent-api`、`agent-infrastructure` |

依赖方向：

```text
agent-boot
  -> agent-api
       -> agent-application
            -> agent-infrastructure
                 -> agent-domain
```

这样做的好处：

- API 层不直接操作数据库，避免 Controller 变胖。
- 业务层集中表达用例和事务，便于面试时讲清楚业务流。
- 基础设施层集中管理 MyBatis Plus，未来替换存储或补 mapper.xml 时不会影响 Controller。
- 启动层只做装配和打包，避免每个模块都被打成 Spring Boot 可执行 Jar。
- Maven 依赖方向单向，没有循环依赖。

## JDK 21 特性对照 JDK 8

### 1. record：不可变 DTO

当前使用位置：

- `IdResponse`
- `HealthResponse`
- `KnowledgeBaseResponse`
- `KnowledgeDocumentResponse`
- `Citation`
- `IncidentRootCause`
- `IncidentDiagnoseResponse`
- `AgentTraceNode`

JDK 21 写法：

```java
public record IdResponse(Long id) {
}
```

JDK 8 常见写法：

```java
public class IdResponse {
    private Long id;

    public IdResponse(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
```

为什么使用：

- 响应 DTO 是数据载体，天然适合不可变。
- 少写大量 getter/setter/构造器，代码更短。
- 字段即构造参数，接口契约更清楚。
- Jackson 2.17 支持 record，可以直接序列化和反序列化。

不使用会怎样：

- 功能不受影响，但 DTO 代码会更冗长。
- 可变 DTO 更容易被业务层中途改值，长期维护时不如 record 清晰。

为什么实体不用 record：

- MyBatis Plus 实体需要无参构造、setter、表映射注解等 JavaBean 习惯。
- 持久化实体是数据库映射对象，不适合强行不可变。

### 2. Stream.toList()

当前使用位置：

- `KnowledgeBaseService.listKnowledgeBases`
- `KnowledgeBaseService.listDocuments`
- `KnowledgeBaseService.deleteDocumentMetadata`
- `TraceService.listByTraceId`

JDK 21 写法：

```java
return list.stream()
        .map(KnowledgeBaseResponse::from)
        .toList();
```

JDK 8 写法：

```java
return list.stream()
        .map(KnowledgeBaseResponse::from)
        .collect(Collectors.toList());
```

为什么使用：

- 表达更简洁。
- 当前场景只返回结果，不需要继续修改 List。

注意：

- `Stream.toList()` 返回的 List 不建议再 `add/remove`。
- 如果后续必须修改，继续使用 `collect(Collectors.toCollection(ArrayList::new))`。

### 3. Set.of()

当前使用位置：

- `KnowledgeBaseService` 中的可见性、文档状态白名单。

JDK 21 写法：

```java
private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "TEAM", "PUBLIC");
```

JDK 8 写法：

```java
private static final Set<String> VISIBILITIES =
        new HashSet<>(Arrays.asList("PRIVATE", "TEAM", "PUBLIC"));
```

为什么使用：

- 固定枚举值集合不可变，避免运行时被误改。
- 代码更短，业务意图更直接。

不使用会怎样：

- 功能不受影响，但 JDK 8 写法更啰嗦。
- 如果使用可变集合，后续代码可能误改白名单。

### 4. Virtual Threads 虚拟线程

当前使用位置：

`agent-boot/src/main/resources/application.yml`

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

JDK 8 情况：

- JDK 8 没有虚拟线程，只能使用传统平台线程。
- Spring MVC 每个阻塞请求会占用一个系统线程。

为什么使用：

- 当前后端是 Spring MVC + RestClient + MySQL，这些都是典型阻塞 IO。
- 虚拟线程能降低高并发阻塞场景下的平台线程占用。
- 对现有同步代码侵入小，不需要改成 WebFlux 全链路响应式。

不使用会怎样：

- 功能不受影响。
- 高并发阻塞调用时，需要更多平台线程和线程池调优。

注意：

- 虚拟线程不是性能万能药，CPU 密集型任务不会因此更快。
- 依赖库如果使用大量 synchronized 长时间阻塞，虚拟线程收益会下降。

### 5. Text Blocks 文本块

当前 Java 后端没有继续保留手写 SQL，因此没有使用 Java 文本块。

适合使用的场景：

```java
String sql = """
        select id, title
        from kb_document
        where tenant_id = ?
        """;
```

为什么现在不用：

- Java MySQL 操作已经统一 MyBatis Plus。
- 当前没有复杂 mapper.xml 或原生 SQL 字符串。

如果以后出现复杂报表 SQL：

- 优先放到 `mapper.xml` 或视图实体对应 Mapper。
- 不建议在 Service 中拼长 SQL 字符串。

### 6. Switch 表达式

当前没有使用。

JDK 21 写法：

```java
String label = switch (status) {
    case "PENDING" -> "待处理";
    case "INDEXED" -> "已索引";
    case "FAILED" -> "失败";
    default -> "未知";
};
```

JDK 8 写法：

```java
String label;
switch (status) {
    case "PENDING":
        label = "待处理";
        break;
    case "INDEXED":
        label = "已索引";
        break;
    case "FAILED":
        label = "失败";
        break;
    default:
        label = "未知";
}
```

为什么现在不用：

- 当前状态校验只需要 `Set.of(...).contains(...)`，比 switch 更短。
- 没有复杂分支转换逻辑。

### 7. Pattern Matching for instanceof

当前没有使用。

JDK 21 写法：

```java
if (value instanceof String text) {
    return text.trim();
}
```

JDK 8 写法：

```java
if (value instanceof String) {
    String text = (String) value;
    return text.trim();
}
```

为什么现在不用：

- 当前 Java 后端已尽量使用强类型 DTO。
- 除了 `IncidentEvidence` 的扩展字段，业务代码不需要大量处理 `Object`。

### 8. var 局部变量类型推断

当前没有使用。

为什么不使用：

- 项目偏面试和交接展示，显式类型更容易读。
- 后端 Service 中类型本身就是业务文档的一部分。

适合使用的场景：

- 局部临时变量类型很长，且右侧已经非常明确。

不建议使用的场景：

- DTO、实体、集合转换处会降低可读性。

## 实体到 DTO 转换：为什么暂时不用 MapStruct

当前使用静态工厂方法：

```java
public static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
    return new KnowledgeBaseResponse(
            knowledgeBase.getId(),
            knowledgeBase.getTenantId(),
            knowledgeBase.getName(),
            knowledgeBase.getDescription(),
            knowledgeBase.getVisibility(),
            knowledgeBase.getCreatedAt()
    );
}
```

为什么当前这样做：

- 当前只有少量实体转响应 DTO。
- 字段映射简单、没有嵌套对象、没有复杂类型转换。
- 不额外引入 annotation processor，降低多模块 Maven 配置复杂度。

什么时候推荐引入 MapStruct：

- DTO 数量明显增加。
- 同一实体有多个视图 DTO。
- 出现嵌套对象、枚举转换、字段改名、批量列表转换。
- 希望编译期检查未映射字段。

不推荐 `BeanUtils.copyProperties`：

- 运行时反射，字段错误不容易在编译期发现。
- 类型转换不透明。
- 面试讲项目时不如 MapStruct 或显式转换清楚。

## Python / MCP 模块是否需要拆分

### ai-service

当前已经有基本分层：

- `app/main.py`：FastAPI API 入口
- `app/schemas`：Pydantic 请求/响应模型
- `app/agents`：RAG 和故障诊断 Agent 编排
- `app/retrieval`：混合检索
- `app/core`：模型、MySQL、Qdrant、Neo4j、配置等基础能力

结论：暂时不需要继续拆。它已经符合小型 Python AI 服务的主流结构。

### mcp-server

当前规模很小，主要是工具 API 和少量工具函数。

结论：暂时不强拆。等工具数量增加后，可以再拆成：

- `app/api`
- `app/tools`
- `app/core`
- `app/schemas`

现在强拆会增加目录和导入成本，但收益不大。

## 当前打包方式

父工程下执行：

```bash
cd backend-java
mvn -pl agent-boot -am -DskipTests package
```

最终可执行 Jar：

```text
backend-java/agent-boot/target/agent-boot-1.1.0.jar
```

本地脚本 `scripts/service.sh` 和 Dockerfile 都按这个启动层 Jar 运行。
