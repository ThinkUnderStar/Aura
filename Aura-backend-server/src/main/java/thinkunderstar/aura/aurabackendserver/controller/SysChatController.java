package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.response.MessageVODto;
import thinkunderstar.aura.aurabackendserver.service.core.SysChatService;

@RestController
@RequestMapping("/chat")
public class SysChatController {
    private final SysChatService sysChatService;

    public SysChatController(SysChatService sysChatService) {
        this.sysChatService = sysChatService;
    }

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
    @GetMapping("/get/{agentId}")
    @SaCheckLogin
    public Result<Page<MessageVODto>> getChatMessages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @PathVariable Long agentId
    ) {
        return sysChatService.getChatMessages(page, size, agentId);
    }

    @PutMapping("/update")
}
