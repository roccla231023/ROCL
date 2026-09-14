# Changelog

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
