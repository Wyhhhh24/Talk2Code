# Talk2Code - 零代码应用生成平台

<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=timeGradient&height=300&&section=header&text=Talk2Code&fontSize=90&fontAlign=50&fontAlignY=30&desc=Talk2Code&descAlign=50&descSize=30&descAlignY=60&animation=twinkling" />
</p>

## 项目简介

**Talk2Code** 专为开发者和团队设计的智能代码平台，可以通过自然语言对话智能匹配三种代码生成模式，快速生成前端页面。平台赋予AI文件操作与组件级精准修改能力，支持用户从对话迭代开发、一键部署预览到代码下载的全流程

## 主要功能

- **智能代码生成**  
  - HTML 单文件：生成包含内联 CSS 和 JS 的独立 HTML 页面。
  - 多文件分离：生成结构化的 HTML、CSS、JavaScript 分离文件。
  - Vue3 完整项目：生成包含路由、组件的完整 Vue3 工程化项目。

- **AI 工具调用能力**  
  - 文件操作：支持文件读写、修改、删除、目录读取等工具调用。
  - 组件级修改：前端选择页面组件后，通过提示词驱动 AI 精准修改对应代码。
  - 多应用隔离：基于 memoryId 实现不同应用间的代码生成隔离。

- **对话式迭代开发**  
  - 智能路由：AI 自动分析需求，智能选择最适合的代码生成模式。
  - 多轮对话：支持通过对话历史持续优化和迭代生成的代码。
  - 实时反馈：基于 SSE 流式输出，实时展示代码生成过程和工具调用步骤。

- **应用管理与部署**  
  - 应用创建：通过初始化提示词快速创建应用，自动生成应用名称。
  - 一键部署：支持 HTML、多文件、Vue 项目的自动化部署与预览。
  - 代码下载：支持将生成的代码打包为 ZIP 文件下载。
  - 应用管理：支持应用更新、删除、精选等管理功能。

- **对话历史追溯**  
  - 历史查询：基于时间游标实现高效的分页对话历史查询。
  - 会话记忆：通过 Redis+MySQL 多级存储，实现跨会话的对话连续性。
  - 上下文管理：支持最多 20 条消息的上下文窗口，保持对话连贯性。

- **自动化辅助功能**  
  - 封面生成：基于 Selenium 自动生成应用截图作为封面。
  - 云端存储：应用封面自动上传至腾讯云 COS 对象存储。
  - 构建优化：Vue 项目自动执行 npm 构建，生成生产版本。

## 技术栈
<img align="center" src="https://skillicons.dev/icons?i=java,spring,redis,mysql,vue,js,html,css,maven&theme=light" />
本项目主要使用 Java 开发，结合现代技术栈，确保性能与扩展性。

## 安装与使用

1. 克隆项目：`git clone https://github.com/Wyhhhh24/Talk2Code.git`
2. 进入项目目录：`cd Talk2Code`
3. 安装依赖并运行（具体步骤请根据项目配置调整）。

## 贡献

欢迎提交 issue 或 pull request！让我们一起打造更强大的 Talk2Code。

## 联系我们

如有疑问或建议，请通过 GitHub Issues 与我们联系。

---

**Talk2Code - 你的零代码应用生成平台！**
