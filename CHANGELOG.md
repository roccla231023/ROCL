# Changelog

## 1.27

更新内容:
- 新增子代理：在默认模型设置里单独选模型和思考深度才启用，不选就不出现这个工具
- 子代理能读文件、列目录、按内容检索，也能跑命令测试（工作区把「执行命令」设免确认后才有），但不能改文件
- 子代理卡片一张脸：跑着显示「进行中」，完成显示步数；时间线按实际步骤走，折叠路径只留最后两截
- 列目录显示请求的目录，搜索显示搜索词；「要的是」只在读文件请求和结果不一致时出现
- 列目录、按内容检索有自己的卡片：条数和是否被砍进人话，不再铺 JSON
- 点进去底下一个「报告」；引擎账本（几步、成败）在时间线前面
- 群聊成员可单独设置思考深度；未覆写时跟随该座位助手，不改助手本身

Updates:
- New sub-agent: pick its model and thinking depth in Settings; leave empty and the tool does not appear
- The sub-agent can read files, list directories, search contents, and run commands (only if Execute Command is set to no-approval in that workspace); it cannot change files
- One card: running is "in progress", done shows the step count; the timeline is the actual steps, collapsed paths keep the last two segments
- Listing a directory shows the requested directory, search shows the query; "asked for" only appears when a read requested A but the result was B
- Directory listing and content search have their own cards: counts and truncation in plain language, not JSON
- Opening the card shows a report at the bottom, with an engine ledger (steps / success) above the timeline
- Group chat seats can override thinking depth; otherwise they follow that seat's assistant and do not write back to it

## 1.267

更新内容:
- 子代理跑着的标题改成「进行中」，不再带循环轮次 /10，避免和工具步数打架
- 列目录显示请求的目录，搜索显示搜索词，不再把结果里的第一个文件当成标题
- 「要的是」只在读文件请求了 A、工具结果却是 B 时出现；列目录和搜索不再误报
- 折叠时间线的路径只留最后两截，点进去再看完整路径

Updates:
- The running sub-agent title is now "in progress" instead of a loop count like 4/10, so it no longer fights the tool-step ledger
- Listing a directory shows the requested directory, and search shows the query, instead of the first result file
- "asked for" only appears when a read requested A but the tool result was B; directory listing and search no longer false-alarm
- Collapsed timeline paths keep only the last two segments; the full path stays in the open sheet

## 1.266

更新内容:
- 按内容检索扫文件被上限砍断时会明确标成 truncated，并提示缩小路径，不再假装搜完了
- 检索默认跳过 .git、node_modules、build、.gradle、.idea、dist、__pycache__，避免这些目录把配额吃光；指定搜里面仍然可以
- 子代理卡片改成一张脸：跑着和跑完都是同一条时间线，点进去能看到全部步骤；报告沉到底下并标明是模型写的
- 没有调用任何工具就结束时，标题会写成「未调用工具」，不再看起来像干完了
- 列目录、按内容检索有自己的卡片了：条数、是否被砍断用人话写，不再铺 JSON
- 子代理摘要前面会带一行引擎记录（几步、成败），主模型不只看见那篇可能编的报告

Updates:
- Searching file contents now sets truncated when the file-scan cap is hit, with a hint to narrow the path, instead of pretending the search finished
- Content search skips .git, node_modules, build, .gradle, .idea, dist and __pycache__ by default so they do not consume the scan budget; pointing path inside one of them still searches it
- The sub-agent card is one face now: running and done share the same timeline, opening it shows every step, and the model's report sits at the bottom labelled as such
- Finishing without calling any tools is titled that way instead of looking finished
- Listing a directory and searching file contents have their own cards: counts and truncation in plain language, not raw JSON
- A one-line engine ledger now precedes the sub-agent report so the main model is not left with only the possibly-invented write-up

## 1.265

更新内容:
- 子代理能查文件了：新增「列目录」和「按内容检索」两个只读工具（主对话同样可用），读文件改成分段读，长文件不再只能看到开头
- 这两个工具不再依赖 shell，也不假设工作区里装了什么；查询范围限定在 /workspace、/skills、/tmp
- 卡片把「请求」和「结果」分开显示：请求是模型说要做什么，结果是从工具返回里记下来的、它真的碰到了什么
- 跑完的卡片现在能看到实际读了哪些文件、哪一步失败了，失败原因直接显示
- 失败 / 中止 / 没执行各有各的状态，不再都显示成「跑完了」
- 修正内层工具输出截断：以前是硬切字符，会把结果 JSON 切坏、还可能正好切掉「还能继续读」的提示
- 读文件传了超出文件末尾的 offset 时，如实回显请求值并说明，而不是悄悄改成文件末行

Updates:
- The sub-agent can search files now: two read-only tools that list directories and search contents (available to the main chat too), and reading a file is windowed so long files no longer collapse to their head
- Those two tools no longer depend on shell or on anything installed inside the workspace, and queries are limited to /workspace, /skills and /tmp
- The card separates "requested" from "result": requested is what the model said it wanted, result is what was recorded from the tool's actual return value
- A finished card shows which files were really touched and which step failed, with the failure reason inline
- Failed, stopped and never-run now have their own states instead of all looking finished
- Fixed truncation of inner tool output: it used to cut raw characters, which broke the result JSON and could cut away the "you can keep reading" hint
- Passing an offset past the end of a file now echoes the requested value and says so, instead of silently clamping to the last line


## 1.264

更新内容:
- 子代理现在能查文件：新增「列目录」和「按内容检索」两个只读工具，主对话和子代理都能用（它们会改变每次请求的工具集）
- 读文件改成分段读：默认前 250 行，返回总行数和是否还有内容，可按行号接着读；长文件不再只能看到开头
- 子代理能跑命令测试：在工作区里把「执行命令」设为免确认后子代理才拿得到，不设就没有
- 子代理工具白名单改名（里面确实有 shell 了，再叫「只读」是假话）
- 子代理报告改成固定四段：读了什么 / 跑了什么 / 结论 / 未知；要求写短，被截断处会标出来，不再无声截断
- 卡片：折叠态显示每一步实际看的路径 / 命令 / 搜索词；展开态按标签分开展示；摘要按 markdown 渲染，不再出现字面星号
- 配置了子代理模型却没有任何可用工具时，派发会返回一条说明原因的失败摘要，而不是静默什么都不发生

Updates:
- The sub-agent can search files now: two read-only tools that list directories and search file contents, available to both the main chat and the sub-agent (they change the tool set of every request)
- Reading a file is windowed: first 250 lines by default, with total line count and a next offset to continue, so long files no longer collapse to their head
- The sub-agent can run commands: it only gets shell once Execute Command is set to no-approval inside the workspace, and does not get it otherwise
- The sub-agent tool whitelist is no longer called read-only, because it now really does include shell
- Sub-agent reports use a fixed four-part shape (read / ran / findings / unknowns) and are asked to stay short; truncation is marked instead of silent
- The card shows what each step actually looked at when collapsed, labelled evidence when expanded, and renders the summary as markdown instead of literal asterisks
- When a sub-agent model is configured but no tools are available, dispatch returns a failed summary naming the reason instead of doing nothing


## 1.263

更新内容:
- 子代理改成「指定才启用」：默认模型设置页可分别选子代理模型和思考深度，清空即关闭
- 未选择子代理模型时，主模型完全看不到子代理工具（不是报错，是工具不存在）
- 删掉硬编码开关，子代理默认关闭这件事不再依赖「记得改代码」
- 子代理改用你指定的模型和思考深度（原先跟随聊天模型、思考写死关闭）；步数上限 5 -> 10
- 仍是预览：子代理暂时没有目录/内容检索工具，也还不能执行命令

Updates:
- Sub-agent is now opt-in: pick its model and thinking depth in Settings - Default model; clearing the field turns it off
- While no sub-agent model is selected the main model never sees the sub-agent tool at all, instead of seeing it and failing
- Removed the hardcoded switch, so "off by default" no longer depends on remembering to flip a flag
- The sub-agent now runs on the model and thinking depth you picked (it used to follow the chat model with thinking hardwired off); step cap raised from 5 to 10
- Still a preview: no directory/content search tools and no command execution yet


## 1.262

更新内容:
- 预览：主模型可派只读子代理（读文件 / 联网调研，最多 5 步）；摘要回主对话，轨迹留在卡片里。默认硬编码开启，仅用于试包，进正式版前会关掉

Updates:
- Preview: the main model can dispatch a read-only sub-agent (file reads / web research, max 5 steps). A short summary returns to the chat; the trail stays on the card. Hardcoded on for this preview build; it will be off before a stable release

## 1.261

更新内容:
- 群聊成员可单独设置思考深度；未覆写时跟随该座位助手，不改助手本身

Updates:
- Group chat seats can override thinking depth; otherwise they follow that seat's assistant and do not write back to it

## 1.26

更新内容:
- 群聊 MCP 按座位开关生效，不再误用当前/第一助手的 MCP
- 群聊页不再改任何助手（模型 / 思考 / 搜索 / 工作区 / MCP）；这些在成员设置里改
- 群聊顶栏模型与座位覆写一致
- 续写去掉与原文末尾重叠的重复段落
- 记忆检索不再提供「混合」选项（向量超时会退回关键词，以前就是这样）
- 高级外观：全局背景图、透明度/虚化；聊天页单独开关；进出聊天不丢草稿；玻璃主题下弹层和滑动删除卡不透底
- 输入框材质和气泡样式改成分段按钮；偏好图标对齐

Updates:
- Group chat MCP now follows seat toggles instead of the current or first assistant
- The group chat page no longer changes any assistant (model / reasoning / search / workspace / MCP); set those in member settings
- The group chat top bar uses the seat model override
- Continue strips overlapping restated prefixes
- Memory retrieval no longer offers a Hybrid option (vector still falls back to keyword on timeout)
- Advanced appearance: global wallpaper with opacity/blur, a separate chat-page switch, chat transitions keep drafts, and glass dialogs or swipe-dismiss cards stay opaque
- Composer and bubble materials use segmented buttons; preference icons match the rest of Settings

## 1.257

- 群聊 MCP 按座位开关生效，不再误用当前/第一助手的 MCP
- 群聊页不再改任何助手（模型 / 思考 / 搜索 / 工作区 / MCP）；这些在成员设置里改
- 群聊顶栏模型与座位覆写一致
- 续写去掉与原文末尾重叠的重复段落
- 记忆检索不再提供「混合」选项（向量超时会退回关键词，以前就是这样）

## 1.256

- 修复全局背景下弹层透底：对话框、下拉菜单、滑动删除卡片、全屏编辑层改为实体不透明背景，避免提供商列表等内容穿透重叠

## 1.255

- 修复进出聊天页会重建对话页：全局背景和透视主题保持同一棵组合树，避免草稿输入被清空、页面卡顿刷新

## 1.254

- 修复模型配置卡片滑动删除图标穿透重叠：为卡片显式设置实体不透明背景，未滑动时完全遮挡底层的删除与取消按钮
- 页面切换全局壁纸动效平滑化：全局背景与透视主题引入 260ms 平滑透明度渐变过渡（animateFloatAsState），消除页面进出聊天主界面时的闪烁与硬切感

## 1.253

- 修复「应用到聊天主界面」控制逻辑：根层全局背景与透视主题仅在非聊天页面激活，聊天主界面恢复助手独立背景控制与纯色底色；当开启「应用到聊天主界面」时精准覆盖并提供虚化源
- 偏好设置图标风格统一：RP 优化改用与系统工具系列严格统一的极简线条拾色吸管（ColorPicker），彻底消除视觉突兀感

## 1.252

- 修复高级外观设置中聊天输入框小标题的字符编码乱码
- 材质交互升级：输入框材质与气泡样式采用 Material 3 原生分段按钮替代下拉菜单，平铺展示且彻底消除透光穿透与展开重叠
- 偏好设置图标精细化：高级外观设置对齐移植规范使用魔法棒（MagicWand01），RP 优化使用独立引号图标（Quotes），消除图标重复

## 1.251

- 高级外观设置：新增全局背景图片、透明度与高斯虚化调节
- 全局背景透视：支持所有非聊天页面及聊天主界面的背景穿透
- 聊天输入框材质：支持半透明与磨砂玻璃切换，可调节不透明度与虚化半径
- 聊天气泡材质：支持默认、半透明细描边与磨砂玻璃风格切换

## 1.25

- 工作区已读 Markdown 内联渲染：正文和标题缩小一档，只影响开了内联阅读的助手卡片
- 记忆页打开记忆时有展开动效
- 群聊成员进群只带提示词和模型；世界书、Skills、模式注入、记忆、搜索、MCP 默认关，在成员选项里单独开，只在本群生效
- 群聊扩展只留快捷消息，名单挂这个群；聊天页不再误改第一个助手

## 1.21

- 记忆页打开记忆时有展开动效
- 群聊成员进群只带提示词和模型；世界书、Skills、模式注入、记忆、搜索、MCP 默认关，在成员选项里单独开，只在本群生效
- 群聊扩展只留快捷消息，名单挂这个群；聊天页不再误改第一个助手

## 1.2

- 助手记忆：关着只留记忆和会话记忆；会话记忆写在当前对话里，不用后台模型。升级后旧助手默认开会话记忆
- 群聊成员可覆写提示词；@ 两个成员按顺序各自一条气泡，结束后不粘最后开口的人
- 群聊绑工作区后，空 @ 先选成员或文件；Skills 全群共用
- 启动图标字标缩小，不再顶满圆角

## 1.1

- 关闭思考：OpenAI 兼容接口现在传 `reasoning_effort=none`，不再偷偷改成 `low`
- 更新检查改为对照 GitHub Latest 的版本号，不再误报旧 nightly
- 新的 ROCL 启动图标
- 偏好里「消息底部工具栏」改成工具图标，描述压成一行
- Google.Antigravity 使用专用图标

## 1.0

- 首个正式版，基于官方 RikkaHub 2.5.1
- 按模块移植 FLIT 的记忆、群聊、存储、工具栏 Continue、RP 样式、统计、工作区 Markdown 预览
- 品牌改为 ROCL；官方 App 可双开
- 群聊列表 / 成员卡 / 抽屉头像进入群设置
- 关于页指向本仓库；更新通道改为自己的 Releases
