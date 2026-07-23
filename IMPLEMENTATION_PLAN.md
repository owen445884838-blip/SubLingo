# SubLingo 实施计划

> 版本：v0.3 · 状态：已优化，可进入技术验证
> 最后更新：2026-07-15
>
> MVP 原则：优先打通“导入/下载视频 → 获取英文字幕 → 翻译 → 双语播放 → 生词卡 → 复习”的最短学习闭环。高级下载、播放和统计功能在核心链路稳定后再迭代。

SubLingo 是一款 Android 端"通过视频学英语"的开源应用：用户下载 YouTube / Bilibili / 小红书 等平台视频到本地，App 自动提取音频并通过云端 STT 转录为英文字幕、用 LLM 翻译成中英双语字幕，从中抽取生词生成生词卡（含音标、发音、释义、视频原句），再通过"复习"页面的左右滑动卡片进行记忆训练，同时提供双语逐字稿阅读。

---

## 一、技术栈选型（含开源复用调研）

| 能力 | 选型 | 复用的开源项目 / API | 协议 | 说明 |
|---|---|---|---|---|
| 视频下载（主力） | youtubedl-android 库 | [youtubedl-android](https://github.com/yausername/youtubedl-android) (1.3k★) 内核为 [yt-dlp](https://github.com/yt-dlp/yt-dlp) (178k★) + ffmpeg + aria2c | GPL-3.0 / Unlicense | Maven Central 已发布 `io.github.junkfood02.youtubedl-android:library`，Seal 验证过的成熟方案；yt-dlp 内置 YouTube/Bilibili/小红书 等上千站点 extractor |
| 视频下载（后续候选） | cobalt API 客户端 | [cobalt](https://github.com/imputnet/cobalt) (41.5k★) | AGPL-3.0 | 不进入 MVP；首个稳定版本后再评估用户自填实例 URL 的纯 HTTP 通道，不引入 cobalt 源码 |
| 视频播放 | AndroidX Media3 (ExoPlayer) | Google 官方 | Apache-2.0 | 负责本地媒体播放、倍速和进度控制；双语学习字幕由 Compose Overlay 渲染 |
| 音频转录（STT，云端） | 豆包语音识别大模型（火山引擎语音技术）独立客户端 | [豆包录音文件识别](https://www.volcengine.com/docs/6561/1631584)（极速版/标准版） | — | **非 OpenAI 兼容**，走自有端点 + `X-Api-Key` 鉴权；预设 1 家（Doubao）+ 自定义；返回 utterances 带词级时间戳，App 内合成 SRT |
| LLM 翻译 / 抽词 / 总结 | OpenAI 兼容 `/v1/chat/completions` 客户端 | DeepSeek、Xiaomi MiMo、Doubao（火山方舟）均兼容该端点 | — | 自研轻量 HTTP 客户端；3 套预设 + 自定义 |
| 词典数据（音标/释义/例句） | Free Dictionary API | [dictionaryapi.dev](https://dictionaryapi.dev) | 免费、无需 key | 返回 IPA 音标 + 发音 mp3 URL + 英文释义 + 例句；中文释义由 LLM 翻译英文释义得到 |
| 单词发音（TTS） | Android `TextToSpeech` + 词典 mp3 | 系统内置，离线 | — | 双重保障：系统 TTS 兜底 + 词典 mp3 高质量 |
| 间隔重复算法 | SM-2（自实现，约 50 行） | 参考 Anki / SuperMemo-2 | — | 决定复习排序与调度 |
| UI 框架 | Jetpack Compose + Material 3 | 单 Activity + Navigation Compose + 动态取色 | — | 参考 [Seal](https://github.com/JunkFood02/Seal) (27.6k★) 的 Compose 架构，但不直接 fork（Seal 禁止用其名做下载器衍生；SubLingo 为学习型 app，仅借鉴其下载 UI/逻辑模式） |
| 持久化 | Room（结构化）+ 应用专属目录（媒体文件） | AndroidX Room | Apache-2.0 | — |
| 后台任务 | WorkManager | AndroidX WorkManager | Apache-2.0 | 下载/转录/翻译流水线，支持进度通知与重试 |
| DI / 网络 / 序列化 / 图片 | Hilt + Retrofit/OkHttp + kotlinx.serialization + Coil | 业界标准 | — | — |

### 关于 GPL/AGPL 合规
本项目自身开源，不规避 GPL。但需注意：
- 依赖 youtubedl-android（GPL-3.0）→ SubLingo 须以 **GPL-3.0** 发布。
- 若集成 cobalt 客户端代码（AGPL-3.0）→ 须以 **AGPL-3.0** 发布（更严格，含网络交互条款）。
- **当前决策**：MVP 不集成 cobalt，以 GPL-3.0-or-later 作为暂定项目许可证；发布前根据实际打包产物完成 SBOM 和许可证审计。后续若仅调用独立部署的 cobalt HTTP 服务，仍需结合实现与分发方式重新核查合规义务，不在计划中作绝对法律结论。

---

## 二、系统架构

采用单模块分层 + 功能分包，架构为 MVVM + UseCase + Repository。首版不拆多 Gradle 模块，避免过早增加构建复杂度；业务边界稳定后再按需模块化。

```
app/
├─ ui/                    Compose 屏幕
│  ├─ library/            本地视频库 + 本地文件导入
│  ├─ download/           URL 下载 + 任务进度
│  ├─ player/             Media3 播放 + Compose 双语字幕 Overlay
│  ├─ review/             生词本 + 滑动学习卡片
│  ├─ transcript/         双语逐字稿
│  └─ settings/           LLM / STT / 下载与安全配置
├─ domain/                UseCase + 领域模型 + Provider 接口
├─ data/
│  ├─ db/                 Room 实体、DAO、Migration
│  ├─ remote/             LLM / STT / Dictionary 客户端
│  ├─ media/              yt-dlp、ffmpeg、字幕解析与导出
│  ├─ repository/         Repository 实现
│  └─ security/           Keystore + AES-GCM 密钥存储
└─ worker/                WorkManager 阶段任务
   ├─ DownloadWorker
   ├─ SubtitleDiscoveryWorker（优先获取平台已有英文字幕）
   ├─ ExtractAudioWorker
   ├─ TranscribeWorker
   ├─ TranslateWorker
   └─ VocabWorker
```

### 任务执行原则

- **Room 是任务状态的唯一事实来源**，WorkManager 只负责持久化调度、约束与重试。
- 各阶段必须幂等：执行前检查数据库状态和产物，避免重复字幕、重复卡片与重复计费。
- 中间文件写入 `.part`，成功校验后原子重命名；取消或失败后按清理策略处理。
- MVP 支持取消、自动重试和从已完成阶段继续；**不实现下载暂停按钮**，断点续传能力以后端引擎实测为准。
- 失败分为 `RETRYABLE`、`USER_ACTION_REQUIRED`、`FATAL`，UI 提供可执行的恢复提示。

### 核心数据流（按需执行）

```
粘贴 URL / 导入本地视频
  → 获取元数据并创建 ProcessingJob
  → 下载视频（URL 来源）或登记本地文件
  → 检测平台已有英文字幕
      ├─ 有可用字幕：下载并解析
      └─ 无可用字幕：抽取压缩音频 → 分片 → 云端 STT
  → 英文 SubtitleTrack 入库
  → 用户确认或按设置自动调用 LLM 翻译
  → 中文 SubtitleTrack 入库并与英文轨道关联
  → 用户确认或按设置生成生词
  → 本地预处理候选词 + LLM 排序/词形还原
  → Dictionary Provider 补全并缓存词典信息
  → 批量翻译释义 → 创建复习卡
  → 双语播放 / 逐字稿 / 复习
```

优先复用平台字幕可以显著减少 STT 成本和等待时间。转录、翻译、生词提取分别提供费用提示和独立开关，下载完成后不默认产生全部云端费用。

---

## 三、数据模型（Room 实体草图）

### 媒体与任务

- **Video**：id, originalUrl, canonicalUrl, source, remoteVideoId, title, thumbnail, filePath, durationMs, fileSize, language, lastPlayedPositionMs, createdAt, updatedAt
- **ProcessingJob**：id, videoId, currentStage, state, progress, attemptCount, lastErrorCode, lastErrorMessage, createdAt, updatedAt
- **AudioChunk**：id, jobId, chunkIndex, startOffsetMs, durationMs, filePath, remoteTaskId, state, attemptCount

`ProcessingJob.currentStage`：`METADATA / DOWNLOAD / SUBTITLE_DISCOVERY / AUDIO_EXTRACTION / TRANSCRIPTION / TRANSLATION / VOCABULARY`。

`ProcessingJob.state`：`PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED / WAITING_FOR_USER`。不再用单个 `Video.status` 表示整条流水线，避免翻译失败覆盖“视频已成功下载”等局部事实。

### 字幕

- **SubtitleTrack**：id, videoId, language, kind(PLATFORM/ASR/TRANSLATION/USER), sourceTrackId, providerId, model, promptVersion, createdAt
- **SubtitleCue**：id, trackId, sequence, startMs, endMs, text, isUserEdited, createdAt, updatedAt

英文与中文使用独立轨道，中文轨道通过 `sourceTrackId` 关联英文轨道。Room 是字幕事实来源，SRT/VTT 作为导入导出格式按需生成，避免数据库与字幕文件双向不一致。

### 词汇与复习

- **Lexeme**：id, lemma, normalizedLemma, language, phonetic, audioUrl, createdAt
- **LexemeSense**：id, lexemeId, pos, definitionEn, definitionZh, source
- **WordOccurrence**：id, lexemeId, videoId, cueId, surfaceForm, contextEn, contextZh
- **ReviewCard**：id, lexemeId, repetitions, intervalDays, easeFactor, dueAt, lastReviewedAt, createdAt
- **ReviewLog**：id, cardId, rating(AGAIN/GOOD), reviewedAt, previousIntervalDays, nextIntervalDays
- **DictionaryCache**：query, responseJson, state, expiresAt, updatedAt

同一词元只创建一张默认复习卡，但保留多个视频上下文。MVP 使用适配左右滑交互的二元简化 SM-2：左滑 `AGAIN`、右滑 `GOOD`；数据字段保留将来扩展 `HARD/EASY` 的能力。文案称“基于 SM-2 的间隔重复”，不宣称严格实现原始 SM-2。

### Provider 与密钥

- **ProviderProfile**：id, kind(LLM/STT/DICTIONARY), name, presetId, baseUrl, model, resourceId, optionsJson, secretAlias, enabled
- API Key 与 Cookie 不进入 Room 明文字段。Keystore 保存 AES 密钥，AES-GCM 密文存放于专用 SecretStore，数据库只引用 `secretAlias`。
- 业务层面向 `SpeechToTextProvider`、`TranslationProvider`、`VocabularyProvider`、`DictionaryProvider` 接口，不按供应商名称分支。

> 双语逐字稿无需单独实体，由关联的 `SubtitleTrack + SubtitleCue` 聚合生成。所有外键、级联删除与唯一索引在 M0 编码前形成正式 Room Schema；重点约束 `Video(source, remoteVideoId)`、`SubtitleCue(trackId, sequence)` 和 `Lexeme(language, normalizedLemma)`。

---

## 四、功能模块详细设计

### 模块 1：视频下载与播放

**下载页**
- URL 输入框 + 剪贴板粘贴识别 + 站点图标自动识别（依据 URL 域名匹配 yt-dlp extractor）。
- MVP 仅使用本地 youtubedl-android 通道；下载失败时提示重试、更新 Cookie 或改用本地文件导入，不自动切换到外部服务。
- MVP 提供自动推荐清晰度和“仅音频”快捷选项；完整 `--list-formats` 高级选择后置。
- 下载任务：Room 持久化状态 + WorkManager 前台执行，通知栏展示进度；支持取消和重试，暂停按钮后置。
- Cookie 支持：Bilibili 高清/小红书等可能需要 cookie，设置页可粘贴 cookie 字符串。

**本地播放页**
- Media3 负责媒体播放，Compose 自定义字幕 Overlay 负责学习型字幕渲染；不把核心交互绑定到内置 SRT renderer。
- 英文和中文使用独立字幕轨道，可分别开关、调整字号/颜色/背景。
- 点击字幕行跳转到对应时间；支持倍速（0.5x–2.0x）和 A-B 复读。
- 字幕以 Room 数据为事实来源，可按需导出 `.srt`；后续可扩展点击单词、当前词高亮等学习交互。
- 后台音频模式推迟到核心链路稳定后评估。

### 模块 2：字幕转录与翻译 + 生词卡

**转录（云端 STT，豆包语音识别大模型）**
- 默认配置：用户必填一个 STT Provider 才能使用转录功能。
- 预设 STT 供应商：**豆包录音文件识别（火山引擎语音技术）**。非 OpenAI 兼容接口，需写独立 HTTP 客户端。
  | 预设 | 端点 | 鉴权 | 资源 ID | 版本 |
  |---|---|---|---|---|
  | Doubao ASR 极速版 | `https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash` | Header `X-Api-Key`（新版控制台 App Key） | `volc.bigasr.auc_turbo` | 一次请求即返回，30 分钟音频约 10 秒 |
  | Doubao ASR 标准版 | `https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize`（submit/query 轮询） | 同上 | `volc.bigasr.auc` | 3 小时内返回，支持 ≤5h 音频 |
- 鉴权：HTTP Header 方式（`X-Api-Key` + `X-Api-Resource-Id` + `X-Api-Request-Id` + `X-Api-Sequence`），**不是** OpenAI 的 `Authorization: Bearer`。App 需写专用客户端适配。
- 请求体：`{ user:{uid}, audio:{url 或 data:base64}, request:{model_name:"bigmodel", enable_punc, enable_itn, enable_speaker_info} }`。`audio.url` 与 `audio.data` 二选一；本地文件用 base64 上传（建议 ≤20MB，大文件走 url）。
- 返回：`result.utterances[]`，每个 utterance 含 `text` + `start_time`/`end_time`（毫秒）+ 词级 `words[]` 时间戳。App 内按 utterance 的时间戳合成 SRT。
- 输出语言：ASR 只转录源语言文字（英文音频→英文字幕），**不做翻译**。中文译文由后续 LLM 翻译步骤产出。
- 版本选择策略：默认极速版（≤2h 音频、≤100MB）；超 2h 自动回退标准版（≤5h）。
- 音频格式优先使用服务端支持的压缩格式，避免 16-bit WAV 造成巨大上传体积；最终编码参数以 M-1 实测为准。
- 分片不只用于超 5 小时视频：当文件超过接口请求体限制、网络不稳定或需要阶段恢复时，按 10–20 分钟切片，并尽量靠近静音点。
- 每片保留 `startOffsetMs`，服务端时间戳回写时统一加偏移；边界可保留约 0.5–1 秒重叠并做重复文本消除。
- **大文件上传路径必须在 M-1 闭环验证**：核实二进制/Base64 上限及 `audio.url` 的公网可达要求。若 URL 模式需要对象存储，MVP 优先采用本地分片，不默认引入自建后端。
- 自定义 STT：保留自定义入口（name + endpoint + apiKey + resourceId + version），供用户接入其他火山引擎 ASR 资源或自建实例。
- 计费：按音频时长计费（元/小时），具体单价以火山引擎控制台为准；App 内在配置页提示"按音频时长计费，详见火山引擎控制台"。

**翻译（LLM）**
- 输入：英文 SRT 句段数组（含 index + 时间戳 + 英文文本）。
- Prompt 要求：逐句翻译、严格保持 index 对齐、不合并/拆分句段、输出 JSON 数组 `[{index, textZh}]`。
- 按 token 预算动态分批，不固定为 50 句；为输出和系统 Prompt 预留足够 token。
- 校验输出 index 集合与输入完全一致，拒绝缺失、重复、额外 index 和异常空文本。
- 只重试缺失或非法条目；重试时缩小批次，最终可降级为逐句请求，避免整批重复计费。
- 保存 provider、model、promptVersion、tokenUsage 和批次状态，便于追踪成本及选择性重新生成。

**生词提取（LLM）**
- 从英文字幕中抽取"值得学习的词汇"：词形还原（lemmatization）、去停用词、过滤常见词表（如 GSL/牛津 3000 反例）、按词频与难度排序。
- 输出 JSON：`[{surfaceForm, lemma, sourceCueId}]`；按 `language + normalizedLemma` 去重，同时保留多个来源上下文。

**生词卡补全**
- 对每个生词请求 Free Dictionary API → 取 IPA 音标、发音 mp3 URL、英文释义/例句。
- LLM 把英文释义翻成中文释义。
- 例句优先取该词出现的视频原句（含中文译文）；词典例句作为补充。

**模型配置（设置页）**
- **LLM 预设**（OpenAI 兼容 `/v1/chat/completions`），只需填 apiKey：
  | 预设 | baseUrl | 默认 model |
  |---|---|---|
  | DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
  | Xiaomi MiMo | `https://api.mimo.xiaomi.com/v1`（编码阶段核实端点） | 待核实 |
  | Doubao | `https://ark.cn-beijing.volces.com/api/v3` | 待核实 endpoint id |
- **自定义 LLM**：name + baseUrl + apiKey + model + 可选参数（temperature 等）。
- **STT 预设 / 自定义**：见上"转录"小节。
- Keystore 生成不可导出的 AES 密钥，使用 AES-GCM 加密 apiKey 与 Cookie；密文、IV 和版本写入 SecretStore，禁止明文落盘。
- Release 构建关闭 HTTP Body/Header 日志；崩溃日志清洗鉴权头、Cookie、签名 URL 和请求正文。
- 自定义服务默认只允许 HTTPS；如用户显式开启局域网 HTTP，显示安全警告。
- Android 自动备份排除密钥密文、Cookie 和临时媒体处理文件。
- 分别配置“翻译/抽词用 LLM”与“转录用 STT”两套 Provider；MVP 不单独实现视频总结。

### 模块 3：复习页面

**生词本**
- 列表/网格视图，可按"源视频分组"、"到期/未学/已掌握"筛选，支持搜索、编辑、删除、手动添加。
- 每张卡显示：单词、音标、词性、中文释义摘要、来源视频、到期日。

**学习模式（滑动卡片）**
- 全屏卡片栈，正面只显示**英文单词 + 音标 + 视频原例句（英文）**，**不显示中文**。
- 右滑 = 记住，左滑 = 没记住；滑动后卡片翻面显示：中文释义 + 词性 + 发音按钮 + 例句中文译文。
- 发音按钮：优先播放词典 mp3，失败回退 Android TTS。
- 结果写入 `ReviewLog`，并更新简化间隔调度（repetitions / intervalDays / easeFactor / dueAt）。
- 卡片支持"跳转到源视频该句"（ deeplink 到播放页指定时间点）。
- 一次学习会话默认取到期卡 + 新卡混合（可配置数量）。

**学习统计与热力图**
- 展示今日已学、待复习、已掌握、连续学习天数和累计学习天数。
- 在复习页提供 A1、A2、B1、B2、C1–C2 五档英语难度滑块。词汇/短语 occurrence 持久化 CEFR 难度，学习卡、生词本和逐字稿高亮按用户选择的最低难度本地过滤；手动卡片和未可靠分级的数据始终保留。切换档位不得删除卡片、修改复习记录或重复调用 LLM。
- 提供近 12 个月的单词学习热力图，按自然日聚合 `ReviewLog` 中的复习次数；每个方格代表一天，颜色深浅表示当日完成的复习次数。
- 默认强度分为 5 级：0、1–9、10–19、20–39、40+ 次；阈值集中定义，后续可按用户数据分布调整。
- 点击日期显示当日复习次数、记住次数、忘记次数、新学单词数和复习正确率；没有记录的日期显示为 0，不补写数据库。
- 日期分组使用设备当前时区和本地自然日；`ReviewLog.reviewedAt` 仍以 UTC 时间戳存储，时区变化后按当前时区重新聚合。
- 热力图使用 Compose Canvas/Lazy 布局自绘，不引入仅用于该图表的大型依赖；支持横向滚动、月份标签、星期标签和无障碍描述。
- 统计数据直接从 `ReviewLog` 聚合，MVP 不新增每日汇总表；若实测查询性能不足，再增加可重建的 `DailyReviewStats` 缓存表。

### 模块 4：双语逐字稿

- 由 `SubtitleCue` 按 `videoId + index` 聚合，逐段展示英文 + 中文。
- 显示模式切换：只看英文 / 只看中文 / 双语。
- 点击任一段 → 跳转播放页对应时间点。
- 支持复制、全文导出（txt / md）。

---

## 五、关键技术决策（已根据反馈确认）

| 项 | 决策 | 来源 |
|---|---|---|
| 转录方式 | **全部云端 STT**，不做本地 whisper.cpp | 用户反馈：本地资源消耗过大、过慢 |
| GPL 合规 | 不规避 GPL；MVP 以 GPL-3.0 发布（依赖 youtubedl-android） | 用户反馈：本项目开源 |
| 是否 fork Seal | 不直接 fork Seal（其禁止用 Seal 名做下载器衍生），改为依赖 youtubedl-android 库 + 借鉴其 UI 模式 | Seal README 限制 + 架构差异 |
| 站点优先级 | **YouTube + Bilibili + 小红书 全部进 MVP**（yt-dlp 均已内置 extractor） | 调研：yt-dlp/lux 均支持小红书，无需降级 |
| 最低系统版本 | Android 8.0 (API 26)+，arm64-v8a 优先 | 用户确认 |
| cobalt 集成方式 | MVP 暂不实现；后续仅作用户自填实例 URL 的纯 HTTP 调用，不复制或链接其源码 | 范围与合规考量 |
| 本地视频导入 | 纳入 MVP，作为下载器失效、站点限制和用户已有媒体的稳定入口 | 核心能力解耦 |
| 字幕来源 | 优先平台已有英文字幕，无可用字幕时才调用云端 STT | 降低费用与延迟 |
| 小红书支持级别 | MVP 标记为实验性，以 M-1 实测决定是否进入正式支持列表 | 站点风控与 Cookie 不确定性 |
| 画中画 | 取消，不进入当前路线图 | 用户确认 |
| 单词学习热力图 | 纳入 MVP，基于 `ReviewLog` 按本地自然日聚合近 12 个月学习活动 | 用户确认 |

---

## 六、MVP 范围

### MVP 必须完成

- 本地视频导入。
- youtubedl-android 单下载通道，支持 URL 元数据、下载、取消、重试和进度展示。
- YouTube、Bilibili 基础支持；小红书实验性支持。
- 平台英文字幕优先，Doubao ASR 极速版/标准版回退。
- DeepSeek 预设 + 自定义 OpenAI-compatible LLM；其他 LLM 预设在端点核实后再启用。
- Media3 播放 + Compose 双语字幕 Overlay。
- 双语逐字稿、生词提取、词典补全、基础复习和二元间隔重复。
- 今日统计、连续学习天数和近 12 个月单词学习热力图。
- 任务状态持久化、阶段恢复、密钥加密、错误提示和基础测试。

### MVP 明确不做

- cobalt 下载通道。
- 下载暂停按钮；仅提供取消和重试。
- 画中画、后台音频。
- 周/月趋势图、学习时长分析等高级统计；单词学习热力图保留。
- 高级格式选择、批量下载、说话人识别、视频总结。
- 超长视频的复杂 VAD 系统；只实现满足接口限制和恢复需求的基础切片。
- 自动处理超过 5 小时的视频。

### 后续增强候选

cobalt、下载暂停、A-B 复读增强、高级格式选择、后台音频、更多 Provider、Markdown 导出、批量处理和更丰富的复习评分可在首个稳定版本后单独排期。

---

## 七、分阶段实施路线图

| 里程碑 | 周期 | 主要交付 |
|---|---:|---|
| **M-1 技术验证** | 1–2 周 | 三站下载 Spike、本地视频导入、平台字幕获取、Doubao 音频上传/轮询、分片时间戳拼接、100 句翻译对齐、Media3 + Compose 字幕 Overlay、包体积与后台限制测试 |
| **M0 基础架构** | 1 周 | Gradle/Compose、Hilt、Room、Navigation、Material 3；Provider 接口、SecretStore、ProcessingJob 状态机、Fake API 与基础测试框架 |
| **M1 导入、下载与播放** | 2 周 | 本地文件导入、youtubedl-android、元数据、DownloadWorker、取消/重试、媒体库、Media3 播放器与双语字幕 Overlay |
| **M2 字幕流水线** | 2–3 周 | 平台字幕优先、ExtractAudioWorker、音频分片、Doubao 两种模式 + 自定义 STT、TranscribeWorker、动态批次翻译、字幕轨道入库与阶段恢复 |
| **M3 生词与逐字稿** | 1.5–2 周 | 本地候选词预处理、LLM 排序、Dictionary Provider 与缓存、批量释义翻译、生词上下文、双语逐字稿 |
| **M4 复习闭环** | 1.5–2 周 | 生词本、滑动卡片、二元简化 SM-2、TTS/词典发音、今日统计、连续学习天数、近 12 个月学习热力图及日期详情 |
| **M5 稳定与发布** | 2 周 | 中断恢复、Room Migration、错误分类、存储清理、性能与设备测试、许可证/SBOM、隐私说明、ABI 拆分、首个 Release |

单人全职的可靠 MVP 预计约 **11–14 周**，具体取决于 M-1 对下载站点、Doubao 上传限制和后台执行的验证结果。M-1 未通过的关键能力不得直接带风险进入正式开发。

---

## 八、风险与对策

| 风险 | 对策 |
|---|---|
| yt-dlp 原生打包体积大（含 Python + ffmpeg） | M-1 测量 Release 单 ABI 体积；使用 App Bundle ABI 拆分；不默认启用动态代码更新 |
| yt-dlp 应用内更新的供应链/商店政策风险 | 更新机制后置；如实现，必须校验来源与哈希、支持回滚，并在发布前核对应用商店政策 |
| 站点 extractor 失效（尤其小红书） | 本地导入兜底；Cookie 加密存储；标注支持等级；小红书保持实验性，不承诺稳定 |
| 本地音频无法直接提供公网 URL | M-1 核实二进制/Base64 限制；MVP 优先分片上传，不默认引入对象存储或自建后端 |
| 云端 STT 费用、限速与任务中断 | 调用前提示费用；平台字幕优先；分片持久化；仅重试失败片；支持从已完成片继续 |
| LLM 翻译句段对齐漂移 | 结构化 JSON、严格 index 集合校验、只重试非法项、缩小批次并保留 promptVersion |
| Worker 被系统停止或 App 被杀 | Room 状态机作为事实来源；阶段幂等；中间文件原子提交；重启后从最近成功阶段恢复 |
| Dictionary API 覆盖率与可用性不足 | Provider 抽象、成功/失败缓存、词典失败仍允许创建上下文卡片；发布前核实数据及音频许可 |
| API Key/Cookie 泄露 | Keystore + AES-GCM；备份排除；Release 禁止敏感日志；崩溃报告脱敏 |
| 平台合规（下载版权） | 仅个人学习用途声明；不绕过 DRM/付费保护；清楚标注实验性支持并遵守平台条款 |
| GPL/第三方许可证义务遗漏 | M0 建立 SBOM；M5 审计实际打包产物、源码提供义务和 NOTICE；许可证结论不只依据依赖名称 |
| DeepSeek/MiMo/Doubao 端点变化 | 预设可编辑；启动开发与发布前分别核实端点、模型 ID、鉴权和价格 |

---

## 九、测试与可观测性

### 自动化测试

- 单元测试：SRT/VTT 解析、时间戳偏移与去重、token 分批、LLM index 校验、词元去重、间隔算法、任务状态迁移、每日统计聚合、热力等级映射、连续学习天数和文件清理。
- 集成测试：Fake LLM/STT Server、Room Migration、WorkManager 中断恢复、限流/超时/非法 JSON、取消与重试、重复执行幂等性，以及跨月/跨年/时区变化下的 `ReviewLog` 聚合。
- UI 测试：本地导入、任务失败恢复、字幕显隐、点击字幕跳转、复习滑动、热力图滚动与日期详情、密钥配置。

### 设备与场景测试

- 至少覆盖 API 26、当前主流 Android 版本、低内存 arm64 设备。
- 覆盖切后台、杀进程、重启、网络切换、弱网、存储不足、Cookie 过期、Provider 额度不足。
- 使用 5 分钟、30 分钟、2 小时媒体样本验证资源占用与恢复能力；MVP 明确拒绝超过 5 小时的媒体并给出提示。

### 结构化事件

每个任务阶段记录耗时、重试次数、输入/输出大小、Provider、模型、错误码、可重试性、yt-dlp/ffmpeg 版本和估算费用。日志不得包含 API Key、Cookie、完整签名 URL、音频 Base64 或完整请求正文。

---

## 十、里程碑验收标准

- **M-1**：YouTube/Bilibili 各完成至少 3 个样本；小红书记录匿名与 Cookie 场景；30 分钟英文视频可完成字幕获取或 STT；100 句翻译 index 完整率 100%；双语字幕连续播放无明显漂移。
- **M0**：进程重启后可恢复模拟任务；敏感信息不明文落盘；Room Schema 与 Migration 测试通过。
- **M1**：本地导入和两站下载成功；取消后无不可清理残留；播放器恢复上次进度；字幕开关和跳转可用。
- **M2**：平台字幕优先逻辑生效；30 分钟音频可分片转录；单片失败不会重复成功片；翻译缺项可定向重试。
- **M3**：同一 lemma 多上下文不重复建默认卡；词典失败不阻塞流程；逐字稿与字幕轨道一致。
- **M4**：左右滑结果正确更新间隔和日志；到期排序稳定；词典音频失败可回退系统 TTS；热力图完整覆盖近 12 个月，跨月/跨年布局正确，日期详情与 `ReviewLog` 聚合结果一致，连续学习天数计算正确。
- **M5**：API 26 与主流版本关键链路通过；无敏感日志；许可证清单、隐私说明、Release 构建和数据清理策略完成。

---

## 十一、待确认/编码阶段核实的事项

1. **Doubao ASR 上传约束**：M-1 核实二进制/Base64 请求上限、支持的压缩音频编码、`audio.url` 要求、极速版/标准版时长与大小限制、轮询状态和任务有效期。
2. **MiMo 与 Doubao LLM 的端点/model id**：MVP 不阻塞于这两个预设；核实后再启用。DeepSeek 暂作默认翻译/抽词预设，同时保留自定义 OpenAI-compatible Provider。
3. **三站实际支持级别**：以 M-1 样本结果决定 Stable/Experimental 标签，尤其记录 Cookie、地区和登录要求。
4. **Free Dictionary API 许可与覆盖率**：验证缓存、音频播放/下载、再分发条件及常见词查询成功率。
5. **品牌与图标**：默认采用 Material 3 动态取色；品牌主色与图标在 M5 前确认，不阻塞核心开发。
6. **cobalt**：已从 MVP 移除，首个稳定版本后根据本地下载失败率和用户反馈决定是否排期。
7. **A-B 复读**：当前保留在播放需求中，但不是首个核心闭环的发布阻断项；若 M1 工期超出则顺延。

---

## 十二、调研参考链接

- yt-dlp: https://github.com/yt-dlp/yt-dlp
- youtubedl-android: https://github.com/yausername/youtubedl-android
- Seal (架构参考): https://github.com/JunkFood02/Seal
- cobalt: https://github.com/imputnet/cobalt
- lux (备选，不作为主力): https://github.com/iawia002/lux
- whisper.cpp (本地 STT，本项目不采用): https://github.com/ggml-org/whisper.cpp
- 豆包录音文件识别极速版（本项目 STT 预设）: https://www.volcengine.com/docs/6561/1631584
- 豆包语音识别大模型产品简介: https://www.volcengine.com/docs/6561/1354871
- Free Dictionary API: https://dictionaryapi.dev
- public-apis 目录: https://github.com/public-apis/public-apis
