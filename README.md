# 约束描述语言（Logic）可视化编辑器

本仓库是一个 Java/Swing 项目，用于编辑、验证与可视化一种约束表达式树结构，该约束的描述规则见 约束描述语言.md 。该工具支持 XML 读写、注释保留、节点级别的编辑和撤销等功能。

> 语言：Java 11（兼容 JDK 11）

## 运行环境要求

- 操作系统：Windows / macOS / Linux（在 Windows 上开发与测试）
- Java：JDK 11（例如 Liberica/OpenJDK 11 或相近版本）。确保 `java` 与 `javac` 可在终端中使用。

## 仓库结构（关键文件）

- `logic/ConstraintVisualizer.java` — 主入口（包含 UI 布局，菜单与事件绑定）。
- `logic/LogicNode.java` — 数据模型（节点类型、参数、子节点、注释等）。
- `logic/LogicGraphPanel.java` — 右侧有向图渲染面板（节点布局、缩放、平移、节点高亮）。
- `logic/LogicXmlUtil.java` — XML 解析与序列化，保留 DOM 注释节点到 LogicNode.comments。
- `logic/LogicValidator.java` — 静态校验规则（结构与变量作用域校验）。
- `logic/SwingTreeUtil.java` — 辅助方法：构建 Swing 树、保存/恢复 UI 状态（基于节点 ID 的展开/选中恢复）。
- `logic/UndoManager.java` — 简易撤销：保存模型快照与 UI 状态。
- `action/` — 一组 action 类：添加/修改/删除/复制/移动/交换/重命名/保存/打开/撤销/编辑注释 等操作。

## 编译与运行（PowerShell 示例）

1. 编译：

```powershell
# 编译所有 java 源文件到 bin 目录
mkdir -Force bin
javac -d bin $(Get-ChildItem -Recurse -Filter "*.java" | ForEach-Object { $_.FullName })
```

2. 运行：

```powershell
# 运行主程序
java -cp bin logic.ConstraintVisualizer
```

> 开发期间我直接在 VS Code 中使用 Java 插件直接运行 `ConstraintVisualizer`，上面命令是通用的命令行方式。

## 运行时配置

- `config.xml`（可选）: 程序尝试加载当前工作目录下的 `config.xml`（包含 bfunc/ffunc/patterns 等配置），用于在添加/编辑节点时提供参数选择。若缺失，某些编辑功能会无法使用。

## UI 布局与主要区域

- 左侧：树状结构（JTree）
  - 显示每个逻辑节点的编号与简短标签。
  - 带注释的节点右侧有一个小角标（橙色三角），单击角标会在树内展开/折叠注释显示（不改变树的展开/折叠状态）。
  - 支持右键菜单（在节点上右键会选中该节点并弹出菜单）。

- 右侧：有向图可视化（LogicGraphPanel）
  - 根据树结构绘制节点和箭头，自动布局。
  - 支持缩放和平移（鼠标滚轮缩放，拖拽平移）。
  - 在树中选中节点时，会把图缩放到合适的固定大小并把选中节点置于视口中心（实现为同步绘制后计算偏移以避免布局时序问题）。

- 底部状态栏：显示当前状态和错误提示。

- 菜单栏：文件 / 编辑 / 视图
  - 文件：打开 XML、保存 XML、导出 PNG/SVG
  - 编辑：撤销、添加、修改、删除、移动、交换（全子树/单节点）、复制粘贴、变量重命名、编辑注释
  - 视图：展开、收起

- 右键弹出菜单：包含编辑菜单与视图菜单中（除撤销外）的项，行为与主菜单一致。

## 支持的节点类型与校验规则（简要）

- 节点类型（LogicNode.NodeType）:
  - RULES、RULE、FORALL、EXISTS、AND、OR、IMPLIES、NOT、BFUNC、FORMULA、UNKNOWN

- 主要结构约束（由 `LogicValidator.validateNodeSelf` 实现）:
  - 根节点必须为 `RULES`。
  - `RULE` 的父必须是 `RULES`。
  - `FORMULA` 的父必须是 `RULE`。
  - `FORALL`/`EXISTS` 必须有 `var` 与 `in` 参数，且只能有 1 个子公式。
  - `BFUNC` 必须有 `name` 参数且不可有子公式。
  - `IMPLIES`、`NOT`、`AND`、`OR` 等有各自子节点计数要求。
  - `UNKNOWN` 节点要求其父为 `RULE` 或 `RULES`（项目中新增规则）。

- 变量作用域校验：
  - `FORALL`/`EXISTS` 定义的变量不得与祖先重复定义。
  - 使用参数（paramList / filterParamList）中引用的变量必须在祖先中定义。
  - 若量词定义的变量在其子孙中未被使用，会报“定义的变量未被使用”。

校验结果会收集到 `LogicValidator.errorNodeMap` 并在 UI 中高亮显示（折叠节点的祖先会显示红色下划线以提示子孙存在错误）。

## 菜单与按键行为清单（交互要点）

- 文件 -> 打开XML：选择并加载 XML，解析成 LogicNode 树。解析时会把 DOM 注释节点附加到该注释之后的第一个逻辑节点（作为该节点的 comments 列表）。
- 文件 -> 保存XML：把当前逻辑树序列化为 XML，注释以独立 DOM 注释节点写入（每段注释写为独立 COMMENT_NODE）。
- 编辑 -> 添加：在当前选中节点下添加子节点（交互式选择类型与参数）。
- 编辑 -> 修改：编辑当前节点。对于常见类型提供两级交互：
  - 若节点为 `FORALL/EXISTS`：首选项提供“修改变量(var) / 修改集合(pattern) / 修改过滤器(ffunc) / 完全修改”，并当节点已有 filter 时提供逐个 filter 参数的修改入口。
  - 若节点为 `BFUNC`：提供“修改单个参数 / 完全修改”。
  - 完全修改会进入完整参数修改流程（选择类型、逐项填写）。
- 编辑 -> 删除：删除当前节点及其子树。
- 编辑 -> 移动：把节点移动到另一个父节点。
- 编辑 -> 交换（全子树 / 单节点）：两种交换模式：交换整个子树或只交换单节点（保留/交换注释与 unknown 信息）。
- 编辑 -> 复制-粘贴：复制一个子树并粘贴到其他父节点（保留注释、showComments 标志等）。
- 编辑 -> 变量重命名：在量词节点上重命名变量，包含冲突检测（祖先/子孙定义冲突提示）。
- 编辑 -> 编辑注释：对当前节点打开注释编辑器（多行），使用空行分段保存为多段注释。此操作在保存前会触发撤销快照，可通过 编辑 -> 撤销 恢复。
- 编辑 -> 撤销：恢复上一次快照（UndoManager 保存模型与 UI 状态）。
- 视图 -> 展开/收起：对当前选中节点展开或收起其子树。
- 右键菜单：在节点上右键会选中该节点并弹出包含上述编辑/视图项（除撤销）的上下文菜单。

## 注释行为说明

- 解析：读取 XML 时，把连续出现的 DOM 注释节点（COMMENT_NODE）归为紧随其后的第一个逻辑元素节点的注释列表（多条注释保留为多段）。
- 显示：树节点文本末尾显示一个小角标（橙色三角），点击角标可在树中展开注释段落；角标可翻转以指示展开/收起状态。
- 编辑：通过“编辑注释”菜单弹出文本编辑器，修改后保存会覆盖该节点的注释段。
- 导出：保存 XML 时会为每段注释生成独立的 DOM COMMENT_NODE，以保持可读性与 round-trip 注释结构。

## 撤销（Undo）策略

- 项目实现了一个简单的单步撤销管理器（`UndoManager`）：在每个会改变模型的 action 前会调用 `UndoManager.saveSnapshot(root, tree, swingRoot)`，它会把当前模型序列化到临时文件并保存 UI 的展开/选中状态。`UndoAction` 会恢复上一次快照并恢复 UI 状态。

## 导出/打印

- 可以导出 SVG（`导出SVG` 菜单）或 PNG（`导出PNG`）；SVG 导出使用项目内的导出器生成文本并写入指定文件。