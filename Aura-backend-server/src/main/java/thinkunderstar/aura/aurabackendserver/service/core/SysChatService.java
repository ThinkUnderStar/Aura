package thinkunderstar.aura.aurabackendserver.service.core;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.ChatDto;
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
}
