package thinkunderstar.aura.aurabackendserver.service.core;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.ChatDto;
import thinkunderstar.aura.aurabackendserver.dto.request.ToolAllowDto;
import thinkunderstar.aura.aurabackendserver.dto.response.MessageVODto;

public interface SysChatService {
    /**
     * 获取指定 Agent 的对话消息列表（分页）
     *
     * <p><b>功能说明</b>：</p>
     * 根据 Agent ID 查询该 Agent 下的所有消息，按创建时间倒序排列。
     * 支持分页查询，每页大小可自定义。
     *
     * <p><b>限流策略</b>：</p>
     * <ul>
     *     <li><b>频率限制</b>：每个用户（基于登录态）每分钟最多请求 10 次。</li>
     *     <li><b>单次最大数量</b>：每页最多返回 100 条消息（超出自动截断）。</li>
     *     <li><b>并发控制</b>：同一 Agent 同时最多 5 个请求（可配置）。</li>
     * </ul>
     *
     * <p><b>参数说明</b>：</p>
     * @param page    页码，从 1 开始，默认 1
     * @param size    每页条数，默认 20，最大 100（超过 100 自动截断）
     * @param agentId Agent ID（路径参数），必填
     *
     * @return 分页消息列表，包含消息内容、角色、创建时间等字段
     *          <br>建议前端做好防抖，避免短时间内频繁请求。
     */
    Result<Page<MessageVODto>> getChatMessages(int page, int size, Long agentId);

    /**
     * 与 Agent 进行流式对话（SSE 实时推送）
     *
     * <p>该接口使用 Server-Sent Events (SSE) 实现实时流式响应，支持：
     * <ul>
     *     <li><b>打字机效果</b>：大模型生成的文本逐字/逐块推送给前端</li>
     *     <li><b>中断确认</b>：当 Agent 需要用户确认时（如保存记忆），推送 interrupt 事件</li>
     *     <li><b>长连接保持</b>：连接保持到对话结束或超时（默认 60 秒）</li>
     * </ul>
     *
     * <p><b>调用流程</b>：
     * <ol>
     *     <li>前端发起 POST 请求，建立 SSE 连接</li>
     *     <li>后端调用 LangGraph 流式接口，逐块接收消息</li>
     *     <li>每收到一块数据，通过 emitter.send() 推送给前端</li>
     *     <li>对话结束或中断时，关闭 SSE 连接</li>
     * </ol>
     *
     * <p><b>SSE 事件类型</b>：
     * <ul>
     *     <li><b>text</b>：文本片段（流式生成的内容）</li>
     *     <li><b>interrupt</b>：中断确认（等待用户操作）</li>
     *     <li><b>done</b>：对话结束</li>
     *     <li><b>error</b>：错误信息</li>
     * </ul>
     *
     * @param agentId Agent ID（同时也是会话 ID，对应 LangGraph 的 thread_id）
     * @param chatDto 对话请求体，包含用户消息内容
     * @return SseEmitter 用于 SSE 流式推送的发射器，前端通过 EventSource 监听
     *
     * @apiNote 接口需要登录（SaToken 拦截），未登录返回 401
     *          <br>前端需配合 EventSource API 使用，监听不同事件类型
     *          <br>中断确认后需调用 POST /api/v1/chat/resume 接口恢复对话
     */
    SseEmitter chatWithAgent(Long agentId, ChatDto chatDto);

    /**
     * 恢复工具调用中断，继续执行对话（SSE 流式响应）。
     * <p>
     * 当 Agent 在对话中触发中断（如询问是否保存用户级记忆）时，
     * 前端会展示确认对话框，用户做出选择后调用此接口将选择结果传回，
     * 使中断的图从暂停处恢复执行，并继续流式返回 AI 响应。
     * <p>
     * <b>调用流程</b>：
     * <ol>
     *   <li>前端展示中断对话框（如“是否保存记忆？”）</li>
     *   <li>用户选择：approve / reject / edit（编辑后同意）</li>
     *   <li>调用此接口，传递用户选择</li>
     *   <li>Java 端透传给 Python 端，Python 端恢复 LangGraph 执行</li>
     *   <li>恢复后的 SSE 流原样透传给前端</li>
     * </ol>
     * <p>
     * <b>用户选择类型</b>：
     * <ul>
     *   <li>{@code approve}：同意工具调用，继续执行</li>
     *   <li>{@code reject}：拒绝工具调用，图将处理拒绝逻辑</li>
     *   <li>{@code edit}：用户编辑了内容，需同时提供 {@code edition} 字段</li>
     * </ul>
     *
     * @param agentId        Agent ID（会话 ID），用于定位对话状态
     * @param toolAllowDto   包含用户选择及可选编辑内容的请求体
     * @return SseEmitter    SSE 流发射器，前端通过 EventSource 监听并实时渲染
     *
     * @apiNote 该接口需登录（SaToken 拦截），未登录返回 401
     *          <br>当前接口只负责透传，不解析中断数据，所有业务逻辑由 Python 端处理
     *          <br>前端应监听以下 SSE 事件：
     *          <ul>
     *            <li>{@code data: ...}：文本片段，直接追加显示</li>
     *            <li>{@code event: interrupt}：新的中断事件（理论上恢复后不会再次触发，但保留）</li>
     *            <li>{@code event: done}：对话结束，关闭连接</li>
     *          </ul>
     */
    SseEmitter toolAllow(Long agentId, ToolAllowDto toolAllowDto);

    /**
     * 清空指定 Agent 的所有会话记忆（**不可恢复**）。
     * <p>
     * 该接口会删除该 Agent 在以下存储中的所有数据：
     * <ul>
     *   <li><b>MySQL</b>：`messages` 表中该 Agent 的所有对话消息（物理删除）</li>
     *   <li><b>PostgreSQL</b>：`checkpoints` 表中该 Agent 的所有状态快照（通过 LangGraph checkpointer）</li>
     *   <li><b>Milvus</b>：该 Agent 对应的向量集合（`aura_agent_{agentId}_session_memory`），释放存储空间</li>
     * </ul>
     * <p>
     * 此操作会彻底清除该 Agent 的全部历史对话记录、图执行状态和向量记忆，
     * 清空后该 Agent 将恢复为初始状态，无法恢复任何历史数据。
     * 建议前端调用前展示二次确认弹窗。
     *
     * @param agentId Agent ID（路径参数），用于定位要清空的 Agent
     * @return {@code Result.success()} 表示操作成功
     *
     * @apiNote 该接口需登录（SaToken 拦截），未登录返回 401
     *          <br>操作幂等：重复调用不会报错（已删除的数据再次删除视为成功）
     *          <br>由于涉及跨服务调用（Python 端），建议设置合理的超时时间
     *          <br>成功响应仅表示操作已触发，具体清理结果需通过日志或状态查询确认
     */
    Result<Void> clearSessionMessage(Long agentId);

    /**
     * 编辑指定消息的内容，并从该消息处回溯重新生成后续对话（流式响应）。
     * <p>
     * 该接口用于用户修改某条历史消息（通常是用户消息），然后系统会删除该消息之后的所有对话，
     * 并基于修改后的内容重新执行 LangGraph 图，生成新的 AI 回复。
     * <p>
     * <b>核心流程</b>：
     * <ol>
     *   <li>校验消息归属和用户权限</li>
     *   <li>更新消息内容（`content` 字段）</li>
     *   <li>删除该消息之后的所有消息（MySQL 物理删除）</li>
     *   <li>调用 Python 端回溯接口，将图状态恢复到该消息对应的 checkpoint</li>
     *   <li>重新执行图（从该消息开始），以 SSE 流式返回新的 AI 回复</li>
     * </ol>
     * <p>
     * <b>限流策略</b>：
     * <ul>
     *   <li><b>容量 (capacity)</b>：5（允许短时间内最多 5 次突发编辑请求）</li>
     *   <li><b>速率 (rate)</b>：0.1（即每 10 秒允许 1 次请求，防止频繁编辑刷对话）</li>
     * </ul>
     *
     * @param messageId 要编辑的消息 ID（路径参数）
     * @param chatDto   包含新消息内容（`humanContent`）及可选配置（知识库、联网搜索等）
     * @return SSE 流发射器，前端通过 EventSource 监听恢复后的实时响应
     *
     * @apiNote 该接口需登录（SaToken 拦截），未登录返回 401
     *          <br>此操作会丢失该消息之后的所有对话历史，不可恢复，建议前端二次确认
     *          <br>修改成功后，前端应刷新消息列表并展示新的流式响应
     *          <br>传入的 ChatDto 中需包含完整的新消息内容，以及知识库绑定和联网搜索配置
     */
    SseEmitter updateMessage(Long messageId, ChatDto chatDto);
}
