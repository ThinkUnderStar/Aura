package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.entity.Document;
import thinkunderstar.aura.aurabackendserver.service.core.SysDocumentService;

@RestController
@RequestMapping("/document")
public class SysDocumentController {
    private final SysDocumentService sysDocumentService;

    public SysDocumentController(SysDocumentService sysDocumentService) {
        this.sysDocumentService = sysDocumentService;
    }

    /**
     * 上传文档到知识库
     * <p>
     * 将文件上传到指定知识库，上传完成后自动进入异步索引流程。
     * 文件会存储在本地磁盘（./docs/documents/{kbId}/），
     * 并在数据库中创建 Document 记录，初始状态为“索引中（0）”。
     * <p>
     * <b>异步索引流程：</b>
     * <ol>
     *     <li>保存文件到本地磁盘</li>
     *     <li>创建 Document 记录，status=0</li>
     *     <li>异步调用 Python 服务进行解析、分块、向量化</li>
     *     <li>处理完成后更新 status（1-成功 / 2-失败）和 chunk_count</li>
     * </ol>
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户对目标知识库有写入权限（个人库的所有者或团队库的成员）</li>
     * </ul>
     * <p>
     * <b>文件限制：</b>
     * <ul>
     *     <li>支持格式：pdf, docx, txt, md, pptx, xlsx</li>
     *     <li>最大文件大小：10MB</li>
     * </ul>
     *
     * @param file 上传的文件（multipart/form-data）
     * @param kbId 目标知识库 ID
     * @return Result 包含 Document 实体（含 id、status=0、filePath 等）
     */
    @PostMapping("/upload")
    @SaCheckLogin
    public Result<Document> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long kbId
    ) {
        return sysDocumentService.uploadDocument(file, kbId);
    }

    /**
     * 分页查询知识库下的文档列表
     * <p>
     * 根据知识库 ID 分页查询该知识库下的所有文档，按创建时间倒序排列。
     * <p>
     * <b>查询范围：</b>
     * <ul>
     *     <li>仅返回指定知识库（kbId）下的文档</li>
     *     <li>不区分文档状态（包含索引中、已完成、失败）</li>
     *     <li>按创建时间倒序排列（最新上传的在前）</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>用户必须有权限访问该知识库（个人库所有者 或 团队库成员）</li>
     * </ul>
     * <p>
     * <b>使用场景：</b>
     * <ul>
     *     <li>知识库详情页展示文档列表</li>
     *     <li>文档管理界面查看已上传的文档</li>
     * </ul>
     *
     * @param kbId 知识库 ID（必填）
     * @param page 当前页码，从1开始，默认1
     * @param size 每页记录数，默认20，最大100
     * @return Result 分页文档数据
     */
    @GetMapping("/get")
    @SaCheckLogin
    public Result<Page<Document>> getDocument(
            @RequestParam Long kbId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size
    ) {
        return  sysDocumentService.getDocument(kbId, page, size);
    }

    /**
     * 删除知识库中的文档（物理删除）
     * <p>
     * 永久删除指定的文档及其关联的物理文件，操作不可恢复。
     * <p>
     * <b>删除范围：</b>
     * <ul>
     *     <li>删除数据库中的文档记录（物理删除，不可恢复）</li>
     *     <li>删除磁盘上对应的物理文件（释放存储空间）</li>
     *     <li>异步清理 Milvus 中对应的向量数据（待 Python 服务实现）</li>
     * </ul>
     * <p>
     * <b>权限要求：</b>
     * <ul>
     *     <li>用户必须已登录</li>
     *     <li>个人知识库：仅知识库所有者可删除</li>
     *     <li>团队知识库：仅团队创建者或管理员可删除</li>
     * </ul>
     * <p>
     * <b>使用场景：</b>
     * <ul>
     *     <li>用户清理知识库中不需要的文档</li>
     *     <li>管理员删除违规或错误的文档</li>
     * </ul>
     *
     * @param documentId 文档 ID（必填）
     * @return Result 删除结果，成功返回空数据
     */
    @DeleteMapping("/delete")
    @SaCheckLogin
    public Result<Void> deleteDocument(@RequestParam Long documentId) {
        return  sysDocumentService.deleteDocument(documentId);
    }
}
