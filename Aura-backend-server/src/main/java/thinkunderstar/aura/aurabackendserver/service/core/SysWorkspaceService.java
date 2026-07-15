package thinkunderstar.aura.aurabackendserver.service.core;

import com.baomidou.mybatisplus.core.metadata.IPage;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceVODto;

public interface SysWorkspaceService {
    /**
     * 获取当前用户的所有工作区（团队）列表
     * <p>
     * 返回当前登录用户创建或加入的所有工作区，按创建时间倒序排列。
     * 只有已登录用户才能访问此接口。
     *
     * @return Result 包含工作区列表的响应，每项包括工作区ID、名称、描述、Logo、加入码、创建时间等信息
     */
    Result<IPage<WorkspaceVODto>> getMyWorkspaces(int page, int size);
}
