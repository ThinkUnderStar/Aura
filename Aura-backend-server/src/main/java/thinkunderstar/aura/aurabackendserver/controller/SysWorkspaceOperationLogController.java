package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceOperationLog;
import thinkunderstar.aura.aurabackendserver.service.core.SysWorkspaceOperationLogService;

@RestController
@RequestMapping("/operation-log")
public class SysWorkspaceOperationLogController {
    private final SysWorkspaceOperationLogService sysWorkspaceOperationLogService;

    public SysWorkspaceOperationLogController(SysWorkspaceOperationLogService sysWorkspaceOperationLogService) {
        this.sysWorkspaceOperationLogService = sysWorkspaceOperationLogService;
    }

    /**
     * 分页查询团队操作日志
     * <p>
     * 查看指定团队的操作日志记录，按创建时间倒序排列。
     * 日志内容包括：团队成员对团队信息、知识库、文档、Logo 等资源的操作记录。
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须是该团队的群主（role=0）或管理员（role=1）</li>
     *     <li>普通成员（role=2）无权查看</li>
     * </ul>
     *
     * @param workspaceId 团队ID
     * @param page 当前页码，从1开始，默认1
     * @param size 每页记录数，默认20
     * @return Result 分页日志数据，包含：
     *         <ul>
     *             <li>total - 总记录数</li>
     *             <li>records - 日志列表（含操作人、操作模块、操作类型、摘要、时间等）</li>
     *         </ul>
     */
    @GetMapping("/{workspaceId}/get")
    @SaCheckLogin
    public Result<Page<WorkspaceOperationLog>> getWorkspaceOperationLogs(
            @PathVariable Long workspaceId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ){
        return sysWorkspaceOperationLogService.getWorkspaceOperationLogs(workspaceId, page, size);
    }
}
