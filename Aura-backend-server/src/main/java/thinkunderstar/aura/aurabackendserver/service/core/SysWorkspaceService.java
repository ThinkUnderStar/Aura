package thinkunderstar.aura.aurabackendserver.service.core;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.springframework.web.multipart.MultipartFile;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.UpdateWorkspaceDto;
import thinkunderstar.aura.aurabackendserver.dto.request.WorkspaceDto;
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
    Result<WorkspaceVODto> createWorkspace(WorkspaceDto workspaceDto);

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
    Result<Void> logo(Long workspaceId,MultipartFile file);

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
    Result<WorkspaceVODto> updateWorkspace(UpdateWorkspaceDto updateWorkspaceDto);

    /**
     * 解散团队
     * <p>
     * 群主解散团队，执行逻辑删除（status=0），团队及其关联数据（知识库、成员关系）将被标记为已解散。
     * 解散后团队不再可用，但数据保留30天，30天后由定时任务自动清理。
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
     * <b>注意：</b>
     * <ul>
     *     <li>此操作为逻辑删除，30天内可通过恢复接口恢复（待实现）</li>
     *     <li>如需立即清除记录，可调用清除接口 /workspace/clear/{workspaceId}</li>
     * </ul>
     *
     * @param workspaceId 团队ID
     * @return Result 解散结果，成功返回空数据
     */
    Result<Void> deleteWorkspace( Long workspaceId);
}
