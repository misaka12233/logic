# 逻辑表达式可视化与编辑器（模块化版）

## 项目简介
本项目是一个基于 Java Swing 的逻辑约束表达式可视化与编辑工具，支持自定义 XML 结构的逻辑树编辑、校验、导入导出、可视化操作。现已模块化拆分，便于维护和扩展。

## 主要功能
- **XML 解析与保存**：支持自定义逻辑表达式 XML 文件的加载，可以保存为图片或xml文件格式。
- **语法与结构校验**：严格按照约定规则校验节点类型、参数、父子关系等，检验将要加载或保存的xml文件是否符合规则。
- **树状结构可视化**：以 JTree 形式展示逻辑结构，支持节点编号、类型、参数等信息。
- **节点操作**：支持节点的添加、修改、删除、移动、交换功能。
    - 添加：选定某个节点，可以在其子树末尾添加一个新节点，用户可根据指引依次选择填写节点的类型、参数
    - 修改：选定某个节点，可以重新填写其参数内容，子树内容不变（不能修改类型，若有修改类型的需求可组合添加、删除、移动等操作实现）
    - 删除：选定某个节点，将该节点及其子节点全部删除
    - 移动：选定某个节点，可以将该节点及其所有子树移动到另一个节点的子树末尾
    - 交换：选定某个节点，可以将该节点及其子树与另一节点及其子树进行整体位置交换
- **节点展开状态保持**：每次操作后自动保存并恢复各节点的展开/收起状态，提升用户体验。

## 代码结构
- `logic/LogicNode.java`：逻辑节点数据结构，包含类型、参数、子节点、展开状态等。
- `logic/LogicXmlUtil.java`：XML 与 LogicNode 的互转工具，支持递归解析与序列化。
- `logic/LogicValidator.java`：逻辑树结构与参数的校验工具，支持递归校验。
- `logic/SwingTreeUtil.java`：Swing 相关的树构建、展开状态保存/恢复等辅助方法。
- `logic/TreeHelper.java`：主界面树结构构建时使用到的辅助方法
- `ConstraintVisualizer.java`：主界面与交互逻辑，负责 UI、事件响应、文件操作等。

## 主要数据结构
- **LogicNode**：
    - `nodeId`：唯一编号
    - `type`：节点类型（FORALL、EXISTS、FILTER、AND、OR、IMPLIES、NOT、BFUNC、FORMULA、UNKNOWN）
    - `params`：节点参数（如 var, in, name 等）
    - `paramList`：函数（即bfunc与fliter类型）的参数列表
    - `children`：子节点列表
    - `expanded`：可视化展开状态

## 运行方式
- 直接用 javac 编译所有 .java 文件，再用 java 启动 ConstraintVisualizer。
- 无需外部依赖，推荐 JDK 11+。

## XML 格式介绍
用于存储和作为程序输入时，约束描述语言使用 XML 格式序列化。最外层为`<formula>`标签，内部为具体的约束表达式。约束描述语言和 XML 格式的对应关系如下：

- $\forall~v~\in~P~(f)$:
    ```xml
    <forall var="v" in="P">
        <f/>
    </forall>
    ```

    其中 `<f/>` 应该为子公式 `f` 的 XML 形式。

- $\exists~v~\in~P~(f)$:
    ```xml
    <exists var="v" in="P">
        <f/>
    </exists>
    ```

    其中 `<f/>` 应该为子公式 `f` 的 XML 形式。

- $\forall~v~\in~P~\text{with}~ffunc(v, v_i,...,v_j)~(f)$:
    ```xml
    <forall var="v" in="P">
        <filter name="ffunc">
            <param pos="1" name="v"/>
            <param pos="2" name="v_i"/>
            <param pos="j" name="v_j"/>
        </filter>
        <f/>
    </forall>
    ```

    其中 `<param/>` 的数量需要和 `ffunc` 的参数数量保持一致。 `<f/>` 应该为子公式 `f` 的 XML 形式。

- $\exists~v~\in~P~\text{with}~ffunc(v, v_i,...,v_j)~(f)$:
    ```xml
    <exists var="v" in="P">
        <filter name="ffunc">
            <param pos="1" name="v"/>
            <param pos="2" name="v_i"/>
            <param pos="j" name="v_j"/>
        </filter>
        <f/>
    </exists>
    ```

    其中 `<f/>` 应该为子公式 `f` 的 XML 形式。

- $(f_1)~\text{and}~(f_2)$:
    ```xml
    <and>
        <f1/>
        <f2/>
    </and>
    ```

    `<and>` 标签内必须包含且仅包含两个子标签，其中 `<f1/>` 和 `<f2/>` 应该分别为子公式 `f_1` 和 `f_2` 的 XML 形式。

- $(f_1)~\text{or}~(f_2)$:
    ```xml
    <or>
        <f1/>
        <f2/>
    </or>
    ```

    `<or>` 标签内必须包含且仅包含两个子标签，其中 `<f1/>` 和 `<f2/>` 应该分别为子公式 `f_1` 和 `f_2` 的 XML 形式。

- $(f_1)~\text{implies}~(f_2)$:
    ```xml
    <implies>
        <f1/>
        <f2/>
    </implies>
    ```

    `<implies>` 标签内必须包含且仅包含两个子标签，其中 `<f1/>` 和 `<f2/>` 应该分别为子公式 `f_1` 和 `f_2` 的 XML 形式。

- $\text{not}(f)$:
    ```xml
    <not>
        <f/>
    </not>
    ```

    `<implies>` 标签内必须包含且仅包含一个子标签，其中 `<f/>` 应该为子公式 `f` 的 XML 形式。

- $bfunc\_1(v_1,...,v_n)$:
    ```xml
    <bfunc name="bfunc_1">
        <param pos="1" name="v_1"/>
        <param pos="2" name="v_2"/>
        <param pos="n" name="v_n"/>
    </bfunc>
    ```

下面是一个约束写成 XML 的例子：

```xml
<formula>
    <forall var="v1" in="functionDocException">
        <forall var="v2" in="functionDefWithFunctionCall">
            <filter name="signature_is_same">
                <param pos="1" var="v2"/>
                <param pos="2" var="v1"/>
            </filter>
            <or>
                <bfunc name="code_throws_exception">
                    <param pos="1" var="v2"/>
                    <param pos="2" var="v1"/>
                </bfunc>
                <exists var="v3" in="functionCallWithSubtreeId">
                    <filter name="is_in_subtree">
                        <param pos="descendant" var="v3"/>
                        <param pos="ancestor" var="v2"/>
                    </filter>
                    <exists var="v4" in="functionDocException">
                        <filter name="function_call_matches_doc_exception">
                            <param pos="1" var="v3"/>
                            <param pos="2" var="v4"/>
                        </filter>
                        <exists var="vEx2" in="functionDocException">
                            <bfunc name="exception_is_same">
                                <param pos="1" var="v1"/>
                                <param pos="2" var="v4"/>
                            </bfunc>
                        </exists>
                    </exists>
                </exists>
            </or>
        </forall>
    </forall>
</formula>
```
