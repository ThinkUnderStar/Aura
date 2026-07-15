package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import thinkunderstar.aura.aurabackendserver.common.Result;
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
    public Result<IPage<WorkspaceVODto>>  getMyWorkspaces(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ){
        return sysWorkspaceService.getMyWorkspaces(page, size);
    }


}
