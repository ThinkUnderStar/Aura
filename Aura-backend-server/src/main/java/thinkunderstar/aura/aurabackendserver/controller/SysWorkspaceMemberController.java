package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceMemberVODto;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceVODto;
import thinkunderstar.aura.aurabackendserver.service.core.SysWorkspaceMemberService;

@RestController
@RequestMapping("/member")
public class SysWorkspaceMemberController {
    private final SysWorkspaceMemberService sysWorkspaceMemberService;

    public SysWorkspaceMemberController(SysWorkspaceMemberService sysWorkspaceMemberService) {
        this.sysWorkspaceMemberService = sysWorkspaceMemberService;
    }

    /**
     * 获取团队成员列表（分页）
     * <p>
     * 查询指定团队的所有正常成员（status=1），按加入时间倒序排列。
     * 返回数据包含成员的用户信息（用户名、头像）和在团队中的角色。
     * 只有该团队的成员才能查看成员列表。
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须是该团队的成员（status=1）</li>
     * </ul>
     *
     * @param workspaceId 团队ID（路径参数）
     * @param page 当前页码，从1开始，默认1
     * @param size 每页记录数，默认20，最大100
     * @return Result 分页成员数据，包含：
     *         <ul>
     *             <li>total - 总记录数</li>
     *             <li>records - 成员列表（含用户ID、用户名、头像、角色、加入时间）</li>
     *         </ul>
     */
    @GetMapping("/{workspaceId}/get")
    @SaCheckLogin
    public Result<Page<WorkspaceMemberVODto>> getWorkspaceMembers(
            @PathVariable Long workspaceId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ){
        return sysWorkspaceMemberService.getWorkspaceMembers(workspaceId, page, size);
    }

    /**
     * 使用邀请码加入团队
     * <p>
     * 用户通过输入有效的邀请码加入指定团队，加入后默认为普通成员（role=2）。
     * 加入成功后，用户可以查看团队信息、知识库内容，并在团队内进行对话。
     * <p>
     * <b>校验规则：</b>
     * <ul>
     *     <li>邀请码必须存在且有效</li>
     *     <li>团队必须处于正常状态（未解散）</li>
     *     <li>用户不能已是该团队的成员</li>
     *     <li>用户不能被移出过该团队（如已被移除，需联系管理员重新邀请）</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>无需团队管理员权限，任何已登录用户均可通过邀请码加入</li>
     * </ul>
     *
     * @param inviteCode 邀请码（查询参数），格式如：aura-550e8400e29b41d4a716446655440000
     * @return Result 加入成功，返回团队基本信息（WorkspaceVODto），包含：
     *         <ul>
     *             <li>团队ID、名称、描述</li>
     *             <li>邀请码</li>
     *             <li>当前用户角色（普通成员 role=2）</li>
     *             <li>创建时间</li>
     *         </ul>
     */
    @PostMapping("/join")
    @SaCheckLogin
    public Result<WorkspaceVODto> joinWorkspace(@RequestParam String inviteCode){
        return  sysWorkspaceMemberService.joinWorkspace(inviteCode);
    }

    /**
     * 退出团队
     * <p>
     * 当前用户主动退出指定的团队，退出后：
     * <ul>
     *     <li>用户的成员记录被物理删除，不再出现在团队成员列表中</li>
     *     <li>用户无法再查看该团队的文档、知识库及团队信息</li>
     *     <li>用户的 Agent 与该团队的关联不再生效</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须是该团队的正常成员（status=1）</li>
     *     <li>用户不能是群主（群主需先转让群主身份或解散团队）</li>
     * </ul>
     *
     * @param workspaceId 团队ID
     * @return Result 退出结果
     */
    @DeleteMapping("/quit")
    @SaCheckLogin
    public Result<Void> quitWorkspace(@RequestParam Long workspaceId){
        return  sysWorkspaceMemberService.quitWorkspace(workspaceId);
    }

    /**
     * 移除团队成员（踢出团队）
     * <p>
     * 管理员将指定成员从团队中移除，被移除的成员：
     * <ul>
     *     <li>成员记录被物理删除，不再出现在团队成员列表中</li>
     *     <li>无法再查看该团队的文档、知识库及团队信息</li>
     *     <li>该用户的 Agent 与该团队的关联不再生效</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须是该团队的群主（role=0）或管理员（role=1）</li>
     *     <li>不能移除自己（如需退出团队，请调用退出接口）</li>
     *     <li>不能移除群主（群主需先转让身份）</li>
     * </ul>
     *
     * @param workspaceId 团队ID
     * @param userId 被移除的成员ID
     * @return Result 移除结果
     */
    @DeleteMapping("/remove")
    @SaCheckLogin
    public Result<Void> removeMember(@RequestParam Long workspaceId ,@RequestParam Long userId){
        return  sysWorkspaceMemberService.removeMember(workspaceId, userId);
    }
}
