package thinkunderstar.aura.aurabackendserver.service.core;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.KbIds;
import thinkunderstar.aura.aurabackendserver.dto.response.BindingKbInformationVODto;
import thinkunderstar.aura.aurabackendserver.entity.Agent;

public interface SysAgentService {
    /**
     * 创建一个新的智能体（Agent）
     * <p>
     * 创建一个仅包含名称的智能体实体，创建后默认为“活跃”（status=1）状态。
     * 智能体创建完成后，后续可通过更新接口绑定知识库、设置系统提示词等。
     * <p>
     * <b>默认配置：</b>
     * <ul>
     *     <li>状态：1-活跃（可直接用于对话）</li>
     *     <li>绑定知识库：无（需后续通过绑定接口添加）</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>用户必须已登录
     *
     * @param name 智能体名称（不能为空，建议长度 1~20 字符）
     * @return Result 创建成功的智能体完整信息（含 ID、创建时间等）
     */
    Result<Agent> createAgent( String name);

    /**
     * 分页查询当前用户的智能体列表
     * <p>
     * 返回当前登录用户创建的所有 <b>活跃状态（status=1）</b> 的智能体。
     * 已归档（status=0）的智能体不会出现在列表中。
     * <p>
     * <b>查询范围：</b>
     * <ul>
     *     <li>仅查询当前登录用户（userId）创建的智能体</li>
     *     <li>仅返回 status = 1（活跃）的智能体</li>
     *     <li>按创建时间倒序排列（最新的在前）</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>用户必须已登录
     * <p>
     * <b>使用场景：</b>
     * <ul>
     *     <li>智能体主列表页展示</li>
     *     <li>对话时选择智能体的下拉框</li>
     *     <li>Agent 绑定知识库时的选择列表</li>
     * </ul>
     *
     * @param page 当前页码，从1开始，默认1
     * @param size 每页记录数，默认20，最大限制100
     * @return Result 分页智能体数据，每项包含ID、名称、状态、创建时间、更新时间
     */
    Result<Page<Agent>> getAgent(Long page, Long size);

    /**
     * 分页搜索当前用户的智能体（按名称模糊匹配）
     * <p>
     * 根据关键词在当前登录用户的 Agent 列表中进行模糊搜索。
     * <b>搜索范围：</b>仅匹配智能体的 <b>名称（name）</b> 字段，采用前后模糊匹配（%keyword%）。
     * <p>
     * <b>查询范围约束：</b>
     * <ul>
     *     <li>仅查询当前登录用户（userId）创建的 Agent</li>
     *     <li>仅返回 status = 1（活跃）的 Agent，已归档的不会出现</li>
     *     <li>按创建时间倒序排列（最新的在前）</li>
     * </ul>
     * <p>
     * <b>使用场景：</b>
     * <ul>
     *     <li>Agent 列表页的快速筛选</li>
     *     <li>绑定知识库时通过名称快速定位目标 Agent</li>
     *     <li>对话时快速找到某个 Agent</li>
     * </ul>
     *
     * @param keyWord 搜索关键词（不能为空）
     * @param page    当前页码，从1开始，默认1
     * @param size    每页记录数，默认20，最大100
     * @return Result 分页智能体数据
     */
    Result<Page<Agent>> searchAgent(String keyWord, Long page, Long size);

    /**
     * 更新智能体名称
     * <p>
     * 修改当前登录用户创建的指定智能体的名称。
     * 仅允许修改<b>活跃状态（status=1）</b>的智能体，
     * 已归档（status=0）的智能体需先恢复后才能修改。
     * <p>
     * <b>校验规则：</b>
     * <ul>
     *     <li>智能体必须存在</li>
     *     <li>智能体必须属于当前登录用户</li>
     *     <li>智能体必须处于活跃状态（status=1）</li>
     *     <li>新名称不能为空，需符合命名规范（1~20位，支持中文/字母/数字/空格/下划线/连字符）</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>用户必须已登录
     *
     * @param id   智能体ID
     * @param name 新的智能体名称
     * @return Result 更新后的智能体完整信息
     */
    Result<Agent> updateAgent(Long id, String name);

    /**
     * 批量绑定智能体与知识库（全量覆盖）
     * <p>
     * 将指定 Agent 与多个知识库建立绑定关系。
     * <b>注意：此操作为全量覆盖，会先解除该 Agent 现有的所有绑定，再绑定新的列表。</b>
     * <p>
     * <b>传入空列表（[]）的含义：</b>表示清空该 Agent 当前绑定的所有知识库。
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户只能操作自己创建的 Agent</li>
     * </ul>
     * <p>
     * <b>校验规则：</b>
     * <ul>
     *     <li>Agent 必须存在且属于当前用户</li>
     *     <li>Agent 必须处于活跃状态（status=1）</li>
     *     <li>传入的知识库 ID 必须存在且为当前用户所有（或为团队知识库且用户有权限）</li>
     * </ul>
     * <p>
     * <b>使用场景：</b>
     * <ul>
     *     <li>用户在 Agent 设置页勾选要绑定的知识库</li>
     *     <li>批量调整 Agent 的知识库关联</li>
     * </ul>
     *
     * @param agentId 智能体 ID（路径参数）
     * @param kbIds   知识库 ID 列表（请求体），如 [1, 2, 3]，传 [] 表示清空所有绑定
     * @return Result 绑定结果，成功返回空数据
     */
    Result<Void> bindKnowledgeBases(Long agentId, KbIds kbIds);

    /**
     * 获取智能体当前绑定的知识库信息
     * <p>
     * 查询指定 Agent 当前绑定的所有知识库，按<b>个人知识库</b>和<b>团队知识库</b>分类返回。
     * 个人知识库直接返回知识库 ID 列表，团队知识库返回对应的团队（Workspace）ID 列表。
     * 前端拿到团队 ID 后，可进一步调用团队知识库详情接口获取具体信息。
     * <p>
     * <b>查询范围：</b>
     * <ul>
     *     <li>仅返回当前 Agent 绑定的知识库</li>
     *     <li>私人知识库：直接返回 kbId</li>
     *     <li>团队知识库：返回对应团队的 workspaceId（团队知识库通过团队间接访问）</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>用户必须已登录，且只能查看自己创建的 Agent 的绑定信息
     * <p>
     * <b>使用场景：</b>
     * <ul>
     *     <li>Agent 绑定设置页回显已绑定的知识库（勾选状态）</li>
     *     <li>确认 Agent 当前关联了哪些知识库</li>
     * </ul>
     *
     * @param agentId 智能体 ID
     * @return Result 包含私人知识库ID列表和团队知识库对应的团队ID列表
     */
    Result<BindingKbInformationVODto> getBindingKbInformation(Long agentId);

    /**
     * 逻辑删除（归档）智能体
     * <p>
     * 将指定智能体标记为已归档状态（status=0），不会物理删除数据。
     * 归档后的智能体不再出现在列表和搜索中，但数据仍保留在数据库。
     * 已归档的智能体不能再次归档，需先恢复。
     * <p>
     * <b>校验规则：</b>
     * <ul>
     *     <li>智能体必须存在</li>
     *     <li>智能体必须属于当前登录用户</li>
     *     <li>智能体必须处于活跃状态（status=1），已归档的不能重复归档</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>用户必须已登录
     *
     * @param id 智能体ID
     * @return Result 归档后的智能体信息（status已变为0）
     */
    Result<Void> deleteAgent(Long id);
}
