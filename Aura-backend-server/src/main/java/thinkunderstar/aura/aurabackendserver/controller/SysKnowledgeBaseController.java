package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
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
            @RequestParam Integer page,
            @RequestParam Integer pageSize
    ){
        return sysKnowledgeBaseService.getMyKnowledgeBases(page,pageSize);
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
}
