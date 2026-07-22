package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.CreateKnowledgeBaseDto;
import thinkunderstar.aura.aurabackendserver.dto.request.UpdateKnowledgeBaseDto;
import thinkunderstar.aura.aurabackendserver.entity.KnowledgeBase;
import thinkunderstar.aura.aurabackendserver.service.core.SysKnowledgeBaseService;

@RestController
@RequestMapping("/kb")
public class SysKnowledgeBaseController {
    private final SysKnowledgeBaseService sysKnowledgeBaseService;

    public SysKnowledgeBaseController(SysKnowledgeBaseService sysKnowledgeBaseService) {
        this.sysKnowledgeBaseService = sysKnowledgeBaseService;
    }

    /**
     * 创建知识库
     * <p>
     * 用户可创建个人知识库或团队知识库。创建后在 Milvus 中创建对应的向量集合，
     * 并在数据库中记录知识库元数据。
     * 个人知识库仅自己可见，团队知识库对团队成员共享。
     *
     * @param createKnowledgeBaseDto 创建知识库请求参数，包含名称、描述、类型（个人/团队）
     * @return Result 创建结果，成功时返回知识库信息（含 ID、名称、集合名等）,前端将这些信息刷新到泪飙最前面
     */
    @PostMapping("/create")
    @SaCheckLogin
    public Result<KnowledgeBase> createKnowledgeBase(@RequestBody CreateKnowledgeBaseDto createKnowledgeBaseDto){
        return sysKnowledgeBaseService.createKnowledgeBase(createKnowledgeBaseDto);
    }

    /**
     * 获取当前用户的所有知识库（分页）
     * <p>
     * 返回当前登录用户创建的所有知识库，包括个人知识库和团队知识库，
     * 按创建时间倒序排列。需要用户已登录。
     *
     * @param page 当前页码，从1开始
     * @param pageSize 每页记录数
     * @return Result 包含分页知识库数据的响应，每项包含知识库ID、名称、描述、类型、文档数量、状态、创建时间等
     */
    @GetMapping("/get")
    @SaCheckLogin
    public Result<Page<KnowledgeBase>> getMyKnowledgeBases(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize
    ){
        return sysKnowledgeBaseService.getMyKnowledgeBases(page,pageSize);
    }

    /**
     * 获取指定团队绑定的知识库详情
     * <p>
     * 根据团队ID查询该团队当前绑定的知识库完整信息。
     * 用于团队首页展示知识库概况、Agent 绑定知识库时确认绑定对象等场景。
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须是该团队的成员（status=1），或者是管理员（role=2）</li>
     * </ul>
     * <p>
     * <b>返回数据：</b>
     * <ul>
     *     <li>知识库完整信息（KnowledgeBase），包含ID、名称、描述、文档数量、创建时间等</li>
     *     <li>若团队未绑定知识库（kbId为null），返回错误提示</li>
     *     <li>若绑定的知识库已被删除或停用，返回错误提示</li>
     * </ul>
     *
     * @param workspaceId 团队ID（路径参数）
     * @return Result 知识库详情
     */
    @GetMapping("/get/{workspaceId}")
    @SaCheckLogin
    public Result<KnowledgeBase> getTeamKnowledgeBases(@PathVariable Long workspaceId){
        return sysKnowledgeBaseService.getTeamKnowledgeBases(workspaceId);
    }

    /**
     * 更新当前用户的知识库信息
     * <p>
     * 支持更新知识库的名称和/或描述信息，只能更新当前登录用户创建的知识库。
     * 已停用（status=0）的知识库不允许更新，需先调用恢复接口。
     * 更新成功后，知识库的 updateTime 字段会自动刷新为当前时间。
     * 如果更新名称，会校验当前用户下是否存在同名知识库（排除自身）。
     * <p>
     * <b>校验规则：</b>
     * <ul>
     *     <li>知识库必须存在</li>
     *     <li>当前用户必须是知识库的创建者</li>
     *     <li>知识库必须是正常状态（status=1）</li>
     *     <li>名称不能与同用户下的其他正常知识库重复</li>
     * </ul>
     *
     * @param updateKnowledgeBaseDto 更新请求体，包含知识库ID、名称和描述（至少传入一个字段）
     * @return Result 更新后的知识库完整信息，包含最新的 updateTime
     */
    @PutMapping("/update/my")
    @SaCheckLogin
    public Result<KnowledgeBase> updateMyKnowledgeBase(
            @RequestBody UpdateKnowledgeBaseDto updateKnowledgeBaseDto
    ){
        return sysKnowledgeBaseService.updateMyKnowledgeBase(updateKnowledgeBaseDto);
    }

    /**
     * 更新团队知识库信息
     * <p>
     * 用于更新团队知识库的名称或描述，调用用户必须是该团队的管理员（群主或管理员）。
     * 只有正常状态（status=1）的知识库才允许更新，已停用的知识库需先恢复。
     * 更新成功后 updateTime 会自动刷新为当前时间。
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须登录</li>
     *     <li>用户必须是该知识库所在团队的管理员或群主</li>
     *     <li>知识库必须处于正常状态（status=1）</li>
     * </ul>
     * <p>
     * <b>校验规则：</b>
     * <ul>
     *     <li>type 只能是 "name" 或 "description"</li>
     *     <li>value 不能为空</li>
     *     <li>知识库必须存在且未被删除</li>
     * </ul>
     *
     * @param updateKnowledgeBaseDto 更新请求体，包含知识库ID、修改类型、新值
     * @return Result 更新后的知识库完整信息
     */
    @PutMapping("/update/team")
    @SaCheckLogin
    public Result<KnowledgeBase> updateTeamKnowledgeBase(
            @RequestBody UpdateKnowledgeBaseDto updateKnowledgeBaseDto
    ){
        return sysKnowledgeBaseService.updateTeamKnowledgeBase(updateKnowledgeBaseDto);
    }

    /**
     * 逻辑删除个人知识库
     * <p>
     * 将当前用户拥有的个人知识库标记为已删除（status=0），
     * 知识库数据仍保留在数据库中，可通过恢复接口还原。
     * 已停用的知识库不能再次删除，需先恢复。
     * 删除后该知识库将不再出现在知识库列表中，
     * 但关联的 Milvus Collection 不会被立即删除（保留30天，过期后自动清理）。
     * <p>
     * <b>校验规则：</b>
     * <ul>
     *     <li>知识库必须存在</li>
     *     <li>知识库必须属于当前用户</li>
     *     <li>知识库必须是个人知识库（isTeam=0）</li>
     *     <li>知识库必须处于正常状态（status=1）</li>
     * </ul>
     *
     * @param id 知识库ID
     * @return Result 删除结果，成功返回被删除的知识库信息（status已变为0）
     */
    @DeleteMapping("/delete/logic")
    @SaCheckLogin
    public Result<KnowledgeBase> logicDeleteKnowledgeBase(@RequestParam Integer id){
        return sysKnowledgeBaseService.logicDeleteKnowledgeBase(id);
    }

    /**
     * 强制删除知识库（物理删除，不可恢复）
     * <p>
     * 立即从数据库和 Milvus 中永久删除知识库及其所有数据（包括文档向量），
     * 删除后数据不可恢复，请谨慎操作。
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须登录</li>
     *     <li>知识库必须属于当前用户</li>
     *     <li>知识库必须是个人知识库（团队知识库需通过团队管理接口删除）</li>
     * </ul>
     * <p>
     * <b>操作行为：</b>
     * <ul>
     *     <li>物理删除 MySQL 记录（不经过软删除）</li>
     *     <li>调用 Python 服务删除 Milvus Collection</li>
     *     <li>删除所有关联的 Agent 绑定记录</li>
     * </ul>
     * <p>
     * <b>风险提示：</b>
     * <ul>
     *     <li>此操作不可逆，删除后数据无法恢复</li>
     *     <li>建议前端调用前进行二次确认</li>
     * </ul>
     *
     * @param id 知识库ID
     * @return Result 删除结果，成功返回空数据
     */
    @DeleteMapping("/delete/force")
    @SaCheckLogin
    public Result<Void> forceDeleteKnowledgeBase(@RequestParam Integer id){
        return  sysKnowledgeBaseService.forceDeleteKnowledgeBase(id);
    }

    /**
     * 恢复已删除的知识库
     * <p>
     * 将处于回收站（status=0）的知识库恢复为正常状态（status=1），
     * 恢复后知识库将重新出现在列表中，可正常使用。
     * 仅限知识库所有者操作，团队知识库需管理员权限。
     * <p>
     * <b>前提条件：</b>
     * <ul>
     *     <li>知识库必须存在</li>
     *     <li>知识库必须处于已删除状态（status=0）</li>
     *     <li>当前用户必须是知识库所有者（个人知识库）</li>
     *     <li>当前用户必须是团队管理员（团队知识库）</li>
     * </ul>
     *
     * @param id 知识库ID
     * @return Result 恢复成功，返回恢复后的知识库完整信息（status已变为1）
     */
    @PutMapping("/restore")
    @SaCheckLogin
    public Result<KnowledgeBase> restoreKnowledgeBase(@RequestParam Integer id){
        return sysKnowledgeBaseService.restoreKnowledgeBase(id);
    }

    /**
     * 模糊搜索个人知识库（分页）
     * <p>
     * 根据关键词在当前登录用户的个人知识库范围内进行模糊匹配。
     * <b>搜索范围：</b>仅匹配知识库的 <b>名称（name）</b> 字段，采用前后模糊匹配（%keyword%）。
     * <p>
     * <b>适用范围：</b>
     * <ul>
     *     <li>仅查询当前用户创建的个人知识库（is_team = 0）</li>
     *     <li>仅返回正常状态（status = 1）的知识库，已停用的不会出现</li>
     *     <li>按创建时间倒序排列（最新的在前）</li>
     * </ul>
     * <p>
     * <b>使用场景：</b>用户通过名称快速定位特定知识库，如输入“技术”匹配“技术文档库”。
     * <p>
     * <b>性能提示：</b>
     * <ul>
     *     <li>建议在数据库表 knowledge_bases 的 name 字段上建立普通索引</li>
     *     <li>由于使用 %keyword% 前后模糊匹配，传统 B-Tree 索引在大数据量下可能失效</li>
     *     <li>单用户知识库数量通常有限（百级以内），当前实现足以满足；若未来扩展至万级，可考虑引入 Elasticsearch 或改用前缀匹配</li>
     * </ul>
     *
     * @param keyword  搜索关键词（不能为空，空字符串会抛出业务异常）
     * @param page     当前页码，从1开始，默认1
     * @param pageSize 每页记录数，默认20，最大限制100
     * @return Result 包含分页知识库数据的响应
     */
    @GetMapping("/search")
    @SaCheckLogin
    public Result<Page<KnowledgeBase>> searchMyKnowledgeBase(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long pageSize
    ){
        return sysKnowledgeBaseService.searchMyKnowledgeBase(keyword,page,pageSize);
    }
}
