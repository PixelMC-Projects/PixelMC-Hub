# 排行榜 Web 读投影 v1

`projection.schema.json` 定义公开 DTO；`../contract.js` 额外校验排序、并列名次、分页偏移、本人身份与状态一致性。字段是边界白名单，公开投影不带食物明细、签历、凭据、管理载荷。

- `food` / `online` 为 archive（本存档），`fortunes` 为 global；范围 ID 是不含秘密的服务端标识，正式接入由可信上下文确定。
- `value` 使用非负安全整数；online 为原始毫秒，不能按 tick/秒上传。unknown self 的 value/rank 可为 null，已知 0 仍为 0。
- `ready` 有已确认总数/时间和当前页；`empty` 是已确认 total=0。其他状态不伪造总数、本人分数或行。旧的已确认缓存可由页面单独保留并标过期，不能伪装当前 ready。
- 全榜按 value DESC、规范小写 playerId ASC 排序后计算 competition rank（1,2,2,4），最后分页；不能对每页重排。`revision` 是向后兼容的可选不透明字符串；真实后端必须用稳定修订绑定分页，修订变化需显式刷新，不混接两版数据。
- page 从 0 开始，size 为 1..100；默认20。self 可在本页之外；viewer 是当前已鉴权玩家，前端不能自选生产 UUID。
- `avatar` 可省略；有值时为 `{url,version}`，仅允许已批准的同源公开头像路径。演示只使用已有 `/assets/avatar.png`，不提交测试皮肤或远程请求。

`demo.js` 是两个 Web 预览共用的固定虚构数据，只能进入明确预览依赖图。它按全榜一次计算排序/名次；只持久保存预览 status/selfAbsent 控制，不存玩家数据。getDemoState 返回稳定引用，setDemoState 与 storage 事件触发订阅，无轮询。

正式请求壳计划：`GET /api/playerhub/leaderboards?board=food&period=all&page=0&size=20`。当前无后端实现；404/缺少 capability 必须显示 unsupported，不能回退 demo。该管理页面路径受既有认证边界约束；游戏 Hub 仍经已鉴权连接的专用 player provider，不能调用管理员 HTTP/RPC。

持久总规范：`pixelmcmanager/forge/pixelmcmanager-forge-1.20.1/docs/模组接口规范.md`。本 Web 契约是首个页面用例，不表示 Java、数据库、原生客户端或生产已完成接入。

## 排名涨跌扩展（2026-11-06）

公开行和self兼容新增可选 rankDelta（有符号安全整数或null），顶层兼容新增 comparisonRevision（非空不透明字符串或null）。正数上升、负数下降、0未变；首次快照或该玩家在上次未入榜时为null，旧响应缺字段等价未知。数值delta要求当前revision与比较修订非空且不同，当前rank及 previousRank=rank+rankDelta 均是>=1的安全整数，self与可见本人字段一致。语义校验承担字段关系，不把JSON Schema当成完整授权/跨字段关系检查。

比较必须来自同board/scope前一次服务端已发布的完整快照，按competition rank之差计算，再分页。不能与当前页、浏览器缓存或本机上次访问时间比较。后端实现仍待后续；通知应把同一revision的rows/self/comparisonRevision原子替换，不轮询。

固定demo-v2默认zhan_cpu三榜第1且从第4上升3位，并包含下降、未变和新入榜无基准行。getDemoProjection(board,{selfOutside:true})保留本人页外场景，selfAbsent:true仍可用；withBaseline:false模拟首次快照、全部delta=null。选项只用于显式检查，不增加产品控件。
