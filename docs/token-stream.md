# TokenStream 流式处理机制

本文档整理 `processTokenStream` 与 `JsonMessageStreamHandler` 的配合方式，并通过时间线示例演示一次完整的流式响应是如何被转换、分发和持久化的。

## 1. 总览

整体流程：

1. `AiCodeGeneratorService` 在生成 VUE 项目时代码返回 `TokenStream`。
2. `processTokenStream` 充当“适配器”，将 `TokenStream` 的各种事件包装成统一的 JSON 字符串并输出为 `Flux<String>`。
3. `StreamHandlerExecutor` 根据 `CodeGenTypeEnum` 选择合适的流处理器：
   - `HTML`、`MULTI_FILE` → `SimpleTextStreamHandler`
   - `VUE_PROJECT` → `JsonMessageStreamHandler`
4. `JsonMessageStreamHandler` 解析 JSON 消息，构造前端展示文本、写入聊天历史，并在流结束后触发项目构建。

## 2. processTokenStream 逻辑

`AiCodeGeneratorFacade.processTokenStream(TokenStream)` 把 LangChain4j 的回调注册到一个新的 Reactor `Flux` 上：

- `Flux.create` 获取 `sink`，负责向下游推送数据。
- 监听 `TokenStream` 各类事件，并在事件触发时把自定义的消息对象序列化为 JSON：
  - `onPartialResponse` → `AiResponseMessage`
  - `onPartialToolExecutionRequest` → `ToolRequestMessage`
  - `onToolExecuted` → `ToolExecutedMessage`
- `onCompleteResponse` → `sink.complete()`，让 Flux 知道流已结束。
- `onError` → `sink.error(error)`，确保异常可被下游感知。

**好处：**

- 统一输出格式：所有事件都转换为 JSON 字符串，前端与业务层只处理一种流。
- 适配 TokenStream：避免为新模型重写 SSE、聊天历史、部署逻辑。
- 保留工具信息：实时携带工具调用的元数据与结果。
- 规范生命周期：完成与异常状态都能传递到 Flux 管道，便于后续收尾。

## 3. JsonMessageStreamHandler 的职责

`JsonMessageStreamHandler.handle(...)` 用于消费 `processTokenStream` 输出的 JSON 流，并做进一步的业务处理：

- `map`：逐条解析 JSON，识别消息类型（`AI_RESPONSE`、`TOOL_REQUEST`、`TOOL_EXECUTED`）。
- `filter`：过滤空字符串，避免无意义输出。
- `doOnComplete`：
  - 将拼接好的 AI 响应写入 `chat_history`。
  - 调用 `VueProjectBuilder.buildProjectAsync(...)` 异步打包项目。
- `doOnError`：记录错误信息到对话历史。

**关键点：**

- 对工具请求去重：利用 `seenToolIds` 只在首次出现某个工具 ID 时输出“选择工具”的提示。
- 对工具执行结果进行 Markdown 格式化，既便于前端展示，也便于持久化。
- 统一在流结束后落库并触发构建，防止中途失败导致状态不一致。

## 4. 回调触发时间线示例

以下假设用户请求“生成 VUE 项目”，AI 需要写多个文件：

| 时间 | LangChain4j 事件 | processTokenStream 输出 | JsonMessageStreamHandler 行为 |
|------|------------------|-------------------------|--------------------------------|
| T0 | `start()` | — | 开始监听，无输出 |
| T1 | `onPartialResponse("项目介绍部分...")` | `{"type":"ai_response","data":"项目介绍部分..."}` | 原样返回给前端，追加到聊天记录 |
| T2 | `onPartialToolExecutionRequest(writeFile#1)` | `{"type":"tool_request","id":"tool-1",...}` | 首次看到该工具 → 输出“\[选择工具] 写入文件” |
| T3 | `onPartialResponse("请查看生成的文件...")` | `{"type":"ai_response","data":"请查看生成的文件..."}` | 继续返回 AI 文本 |
| T4 | `onToolExecuted(result)` | `{"type":"tool_executed","arguments":{"relativeFilePath":"src/App.vue","content":"..."}}` | 生成 Markdown：<br>`[工具调用] 写入文件 src/App.vue` + 代码块，返回给前端并累计 |
| T5 | `onCompleteResponse` | — | `sink.complete()` → `doOnComplete` 保存对话 & 异步构建 |
| T6 | *若有错误* `onError(Throwable)` | — | `sink.error` → `doOnError` 保存失败信息 |

通过这条时间线可以看到：

- `processTokenStream` 负责捕获原始事件并转换为 JSON。
- `JsonMessageStreamHandler` 负责解析 JSON、控制提示频率、落库、触发构建。
- 前端只需订阅统一的 SSE 流，就能同时看到 AI 文本和工具操作详情。

## 5. 与 SimpleTextStreamHandler 的对比

| 场景 | 处理器 | 说明 |
|------|--------|------|
| HTML / MULTI_FILE | `SimpleTextStreamHandler` | 流中只有字符串片段，直接拼接并落库 |
| VUE_PROJECT | `JsonMessageStreamHandler` | 流中包含工具事件、文件内容，需要解析 JSON |

两者都在 `StreamHandlerExecutor` 中根据 `CodeGenTypeEnum` 选择，保持控制层逻辑一致。

## 6. 小结

- `processTokenStream` 是 TokenStream → Flux 的适配层，把多种事件统一成 JSON 消息。
- `JsonMessageStreamHandler` 是业务编排层，解析 JSON、管理聊天历史、触发 Vue 项目构建。
- 二者配合，让复杂的工具流式调用也能复用既有的 SSE、对话记忆、部署机制。

借助该机制，前端体验到的是一条连贯的 JSON SSE 流，而后端可以灵活扩展更多消息类型或工具，而无需改动控制层或基础设施代码。

## 7. 常见问答与延伸说明

### Q1. `onPartialToolExecutionRequest` / `onToolExecuted` 的触发次数？

- `onPartialToolExecutionRequest`：**可能被多次调用**。LangChain4j 会把工具参数（尤其是 `arguments` 中的 `content`）分片输出，拼到一起才是完整参数。
- `onToolExecuted`：**只会触发一次**，在工具执行完成后返回一个 `ToolExecution` 对象，其中包含完整参数与结果（例如 `relativeFilePath`、`content`）。这不是流式的，而是“一次性快照”。
- 因此我们才在 `ToolRequest` 阶段只提示“选择工具”，而在 `ToolExecuted` 阶段输出 Markdown 代码块。

### Q2. 能否流式展示“写入文件内容”？

- 默认做法是在 `onToolExecuted` 拿到完整 `content` 后一次性输出，看起来略具有“阻塞感”。
- 如果想实时看到写入内容，需要在 `onPartialToolExecutionRequest` 中解析每个参数片段（例如 JSON 中的 `content` 字段）并逐段推给前端，再在最终 `onToolExecuted` 阶段进行校验/补齐。
- 目前项目仅输出“选择工具”提示和最终结果，没有对工具参数做流式展示；如需优化可在该阶段追加处理逻辑（无需变更控制层）。

### Q3. 为什么必须返回 `TokenStream` 才能监听工具调用？

- 若方法直接返回 `Flux<String>`，LangChain4j 只会把 AI 文本片段塞进 Flux，工具调用对我们是黑盒。
- 只有返回 `TokenStream` 时，框架才会暴露 `onPartialResponse`、`onPartialToolExecutionRequest`、`onToolExecuted` 等回调，我们才能捕获工具的参数与执行结果并转成 JSON。
- 因此，想要实时展示工具调用细节、写入内容或执行结果，API 的返回值必须是 `TokenStream`，然后经 `processTokenStream` 适配为下游可消费的 `Flux<String>`。

### Q4. 为什么要把每个流块先包装成不同的 Message？

目的并非直接把 JSON 推给前端，而是为了在 **后端内部** 做结构化解析与分派：

- `processTokenStream` 把底层回调统一成 `StreamMessage`（带 `type` 字段的 JSON），便于 `JsonMessageStreamHandler` 精准区分事件类型，附带额外字段（例如工具 ID、文件路径、内容等），方便业务逻辑处理和落库。
- `JsonMessageStreamHandler.handle()` 再依据 `type` 构造最终返回给前端的字符串：  
  - `AI_RESPONSE` → 原样文本  
  - `TOOL_REQUEST` → 只在首次调用时输出提示  
  - `TOOL_EXECUTED` → 格式化 Markdown 代码块  
 这样前端无需理解 JSON 协议，消费的是统一的字符串流；而后端保持了结构化信息，易于扩展和调试。

如果未来需要直接把 JSON 推给前端，只需调整 `handle()` 的 `map()` 返回值即可；当前方案是“内部结构化，外部简化”，兼顾可维护性与前端易用性。


