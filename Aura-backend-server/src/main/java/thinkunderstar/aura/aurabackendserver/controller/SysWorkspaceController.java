package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.UpdateWorkspaceDto;
import thinkunderstar.aura.aurabackendserver.dto.request.WorkspaceDto;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceVODto;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceOperationLog;
import thinkunderstar.aura.aurabackendserver.service.core.SysWorkspaceService;

import java.util.List;

@RestController
@RequestMapping("/workspace")
public class SysWorkspaceController {
    private final SysWorkspaceService sysWorkspaceService;

    public SysWorkspaceController(SysWorkspaceService sysWorkspaceService) {
        this.sysWorkspaceService = sysWorkspaceService;
    }

    /**
     * 获取当前用户的所有工作区（团队）列表
     * <p>
     * 返回当前登录用户创建或加入的所有工作区，按创建时间倒序排列。
     * 只有已登录用户才能访问此接口。
     * 同时显示团队状态和自己是否被踢除的状态，若即被踢除，团队也解散了，只显示被踢除
     *
     * @return Result 包含工作区列表的响应，每项包括工作区ID、名称、描述、Logo、加入码、创建时间等信息
     */
    @GetMapping("/get")
    @SaCheckLogin
    public Result<IPage<WorkspaceVODto>> getMyWorkspaces(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ){
        return sysWorkspaceService.getMyWorkspaces(page, size);
    }

    /**
     * 创建团队
     * <p>
     * 当前登录用户创建一个新的团队，创建者自动成为群主（role=0）。
     * 创建团队时会自动完成以下操作：
     * <ul>
     *     <li>生成团队邀请码（格式：aura-{UUID}）</li>
     *     <li>自动创建与该团队绑定的知识库（名称默认与团队名称相同）</li>
     *     <li>将创建者添加为团队成员，角色为群主</li>
     * </ul>
     * 创建成功后，团队和知识库立即可用。
     *
     * @param workspaceDto 团队创建请求参数，包含团队名称（必填）、描述（选填）
     * @return Result 创建成功，返回团队信息（WorkspaceVODto），包含：
     *         <ul>
     *             <li>团队ID、名称、描述、Logo</li>
     *             <li>邀请码</li>
     *             <li>当前用户角色（群主）</li>
     *             <li>关联知识库ID</li>
     *         </ul>
     */
    @PostMapping("/create")
    @SaCheckLogin
    public Result<WorkspaceVODto> createWorkspace(@RequestBody WorkspaceDto workspaceDto){
        return sysWorkspaceService.createWorkspace(workspaceDto);
    }

    /**
     * 上传团队 Logo
     * <p>
     * 当前登录用户为指定团队上传 Logo 图片。
     * 支持 JPG、PNG、WEBP 格式，文件大小限制为 2MB。
     * 上传成功后，旧 Logo 文件会被自动删除，并更新团队的 logo 字段。
     * 仅团队管理员（群主或管理员）可以上传。
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须是该团队的群主或管理员</li>
     * </ul>
     *
     * @param workspaceId 团队ID（请求参数）
     * @param file 上传的 Logo 文件（multipart/form-data）
     * @return Result 上传结果，成功时返回 Logo 访问路径
     */
    @PutMapping("/logo")
    @SaCheckLogin
    public Result<Void> logo(
            @RequestParam Long workspaceId,
            @RequestParam("file") MultipartFile file
    ){
        return sysWorkspaceService.logo(workspaceId, file);
    }

    /**
     * 更新团队信息
     * <p>
     * 更新当前用户所在团队的名称和/或描述信息。
     * 仅团队管理员（群主或管理员）可以操作。
     * 更新成功后，团队的 updateTime 字段会自动刷新。
     * 如果名称未变化，不会触发额外校验，直接返回成功。
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须是该团队的群主（role=0）或管理员（role=1）</li>
     * </ul>
     * <p>
     * <b>校验规则：</b>
     * <ul>
     *     <li>团队名称不能为空（如果传入）</li>
     *     <li>团队描述可为空</li>
     *     <li>团队必须存在且状态正常（未解散）</li>
     * </ul>
     *
     * @param updateWorkspaceDto 更新请求参数，包含团队ID、名称、描述
     * @return Result 更新成功，返回更新后的团队信息（WorkspaceVODto）
     */
    @PostMapping("/update")
    @SaCheckLogin
    public Result<WorkspaceVODto> updateWorkspace(
            @RequestBody UpdateWorkspaceDto updateWorkspaceDto
    ){
        return sysWorkspaceService.updateWorkspace(updateWorkspaceDto);
    }

    /**
     * 解散团队
     * <p>
     * 群主解散团队，执行逻辑删除（status=0），团队及其关联数据（知识库、成员关系）将被标记为已解散。
     * 已解散的团队仍会显示在团队列表中，状态标记为“已解散”，用户可手动清除记录。
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须是该团队的群主（role=0）</li>
     * </ul>
     * <p>
     * <b>操作影响：</b>
     * <ul>
     *     <li>团队状态变为已解散（status=0）</li>
     *     <li>关联知识库被标记为已删除（status=0）</li>
     *     <li>解除所有 Agent 与知识库的绑定</li>
     *     <li>团队成员关系保留，成员可在列表中看到“已解散”状态</li>
     *     <li>Milvus Collection 立即删除（释放向量存储资源）</li>
     * </ul>
     * <p>
     *
     * @param workspaceId 团队ID
     * @return Result 解散结果，成功返回空数据
     */
    @DeleteMapping("/delete")
    @SaCheckLogin
    public Result<List<WorkspaceOperationLog>> deleteWorkspace(@RequestParam Long workspaceId){
        return  sysWorkspaceService.deleteWorkspace(workspaceId);
    }

    /**
     * 清除团队通知记录
     * <p>
     * 用户主动清除已解散团队或已被移出团队的通知记录。
     * 实际执行的是删除当前用户在 workspace_members 表中的关联记录。
     * 清除后，该团队将从用户的团队列表中消失。
     * <p>
     * <b>适用场景：</b>
     * <ul>
     *     <li>用户看到团队已解散的通知，点击清除</li>
     *     <li>用户被管理员移出团队，点击清除</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须是该团队的成员（workspace_members 表中存在记录）</li>
     * </ul>
     * <p>
     * <b>注意：</b>
     * <ul>
     *     <li>此操作仅删除当前用户的成员记录，不影响其他成员</li>
     *     <li>群主解散团队后，自己的记录也可通过此接口清除</li>
     *     <li>清除后该团队不再出现在用户的团队列表中</li>
     * </ul>
     *
     * @param workspaceId 团队ID
     * @return Result 清除结果，成功返回空数据
     */
    @DeleteMapping("/clean")
    @SaCheckLogin
    public Result<Void> cleanWorkspace(@RequestParam Long workspaceId){
        return  sysWorkspaceService.cleanWorkspace(workspaceId);
    }

    /**
     * 重置团队邀请码
     * <p>
     * 管理员主动更新团队的邀请码，重置后旧邀请码立即失效，
     * 新邀请码将作为返回值直接返回给前端。
     * 此操作不会影响已有的团队成员，仅影响后续新成员通过邀请码加入。
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须是该团队的群主（role=0）或管理员（role=1）</li>
     * </ul>
     * <p>
     * <b>使用场景：</b>
     * <ul>
     *     <li>邀请码泄露，需要紧急更换</li>
     *     <li>团队安全升级，定期更换邀请码</li>
     *     <li>团队信息变更，同步更新邀请码</li>
     * </ul>
     *
     * @param workspaceId 团队ID
     * @return Result 重置结果，成功时返回新的邀请码（格式：aura-{UUID}），失败时返回错误信息
     */
    @PutMapping("/invite-code/reset")
    @SaCheckLogin
    public Result<String> resetInviteCode(@RequestParam Long workspaceId){
        return  sysWorkspaceService.resetInviteCode(workspaceId);
    }
}
