package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.WorkspaceDto;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceVODto;
import thinkunderstar.aura.aurabackendserver.service.core.SysWorkspaceService;

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

    }

    @PostMapping("/update")
    @SaCheckLogin
}
