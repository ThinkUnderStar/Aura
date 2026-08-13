package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.entity.*;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.DocumentMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysDocumentService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.*;
import thinkunderstar.aura.aurabackendserver.service.wrapper.impl.SensitiveWordManager;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class SysDocumentServiceImpl implements SysDocumentService {
    private final static long MAX_FILE_SIZE = 500 * 1024 * 1024;
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final KnowledgeBaseService knowledgeBaseService;
    private final WorkspaceService workspaceService;
    private final WorkspaceMemberService workspaceMemberService;
    private final DocumentService documentService;
    private final UserService userService;
    private final WorkspaceOperationLogService workspaceOperationLogService;
    private final DocumentMapper documentMapper;
    private final WebClient webClient;
    private final SensitiveWordManager sensitiveWordManager;

    public SysDocumentServiceImpl(RedisTokenBucketLimiter redisTokenBucketLimiter, KnowledgeBaseService knowledgeBaseService, WorkspaceService workspaceService, WorkspaceMemberService workspaceMemberService, DocumentService documentService, UserService userService, WorkspaceOperationLogService workspaceOperationLogService, DocumentMapper documentMapper, WebClient webClient, SensitiveWordManager sensitiveWordManager) {
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.knowledgeBaseService = knowledgeBaseService;
        this.workspaceService = workspaceService;
        this.workspaceMemberService = workspaceMemberService;
        this.documentService = documentService;
        this.userService = userService;
        this.workspaceOperationLogService = workspaceOperationLogService;
        this.documentMapper = documentMapper;
        this.webClient = webClient;
        this.sensitiveWordManager = sensitiveWordManager;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Document> uploadDocument(MultipartFile file, Long kbId) {
        if (file == null || kbId == null) {
            throw new BusinessException("上传文档业务接口的参数接收异常");
        }

        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("上传的文件过大,超过了500MB");
        }

        //格式过滤模块
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new BusinessException("头像文件名不能为空");
        }

        if (!fileName.contains(".")) {
            throw new BusinessException("头像文件缺少扩展名");
        }

        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (!List.of("docx","pdf","txt","md").contains(ext)){
            throw new BusinessException("暂时只支持上传docx,pdf,txt,md格式的文件");
        }

        if (sensitiveWordManager.checkSensitiveWord(fileName)) {
            throw new BusinessException("文件名中包含敏感词");
        }

        //限流模块
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),3,1)){
            throw new BusinessException("上传文档过于频繁请稍后再试");
        }

        //鉴权模块
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(kbId);
        if (knowledgeBase == null || knowledgeBase.getStatus() == 0) {
            throw new BusinessException("未查询到该知识库");
        }

        if (knowledgeBase.getIsTeam() == 0){ //私人知识库鉴权
            if (loginId != knowledgeBase.getOwnerId()){
                throw new BusinessException("您无权上传文件到该知识库中");
            }
        } else if (knowledgeBase.getIsTeam() == 1) { //团队知识库鉴权
            Workspace workspace = workspaceService.getOne(
                    new LambdaQueryWrapper<Workspace>()
                            .eq(Workspace::getKbId, knowledgeBase.getId())
            );

            if (workspace == null || workspace.getStatus() != 1) {
                throw new BusinessException("该团队可能已被解散或封禁");
            }

            WorkspaceMember member = workspaceMemberService.getOne(
                    new LambdaQueryWrapper<WorkspaceMember>()
                            .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                            .eq(WorkspaceMember::getUserId, loginId)
                            .eq(WorkspaceMember::getStatus,1)
                            .eq(WorkspaceMember::getRole, 0)
                            .or()
                            .eq(WorkspaceMember::getRole, 1)
            );

            if (member == null) {
                throw new BusinessException("你无权上传文件到该知识库中");
            }
            
            //怎加一条团队日志
            String name = userService.getById(loginId).getUsername();
            WorkspaceOperationLog  operationLog = new WorkspaceOperationLog();
            operationLog.setWorkspaceId(workspace.getId());
            operationLog.setUserId(loginId);
            operationLog.setUsername(name);
            operationLog.setModule("document");
            operationLog.setOperation("create");
            operationLog.setStatus(1);
            operationLog.setRequestSummary(
                    "用户: "
                    +name
                    +" 将 "
                    +fileName
                    +" 上传到该团队知识库中"
            );
            
            workspaceOperationLogService.save(operationLog);
        }else { //团队字段值异常
            throw new BusinessException("该知识库的团队指向异常");
        }

        //上传文件业务代码
        String docDirectoryPath = "/documents/aura-kb-" + System.currentTimeMillis() + "-" + kbId;
        String newFileName = "aura-document-" + System.currentTimeMillis() + "-";
        Document document = new Document(
                 kbId,
                 fileName,
                 file.getSize(),
                 ext,
                docDirectoryPath
                        + "/" + newFileName,
                 loginId
         );

        documentService.save(document);
        document.setFilePath(
                document.getFilePath()+document.getId()+"."+document.getFileType()
        );
        newFileName = newFileName + document.getId()+"."+document.getFileType();

        File directory = new File("./docs"+docDirectoryPath);
        if (!directory.exists()) {
            boolean mkDirs = directory.mkdirs();
            if (!mkDirs) {
                throw new BusinessException("创建知识库的对应仓库失败");
            }
        }

        File docFile = new File("./docs"+docDirectoryPath+"/"+newFileName);
        try {
            file.transferTo(docFile);
        } catch (IOException e) {
            throw new BusinessException("文件上传失败");
        }

        //调用python端的服务接口上传文档到知识库中
        Result result = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/document/upload")
                        .queryParam("kb_id", kbId)
                        .queryParam("doc_id", document.getId())
                        .build()
                ).retrieve()
                .bodyToMono(Result.class)
                .block();

        if (result == null || result.getCode() != 200) {
            throw new BusinessException("文档上传失败");
        }

        //设置字段检索完成
        document.setStatus(1);
        documentService.updateById(document);
        return Result.success(document);
    }

    @Override
    public Result<Page<Document>> getDocument(Long kbId, Long page, Long size) {
        if (kbId == null || page == null || size == null) {
            throw new BusinessException("获取知识库的文件信息接口的参数接收异常");
        }

        if (page < 1){
            page = 1L;
        }

        if (size < 1){
            size = 20L;
        }

        if (size > 100){
            size = 100L;
        }

        //限流模块
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("获取知识库相关文件信息过于频繁，请稍后再试");
        }

        //鉴权模块
        authentication(kbId, loginId);

        //获取该知识库相关文件信息的业务代码
        Page<Document> documentPage = new Page<>(page, size);
        Page<Document> resultRage = documentMapper.selectPage(
                documentPage,
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKbId, kbId)
                        .orderByDesc(Document::getCreateTime)
        );

        return Result.success(resultRage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteDocument(Long documentId) {
        if (documentId == null) {
            throw new BusinessException("删除文档接口的参数接收异常");
        }

        //限流模块
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),3,1)){
            throw new BusinessException("删除文档过于频繁，请稍后再试");
        }

        //鉴权模块
        Document document = documentService.getById(documentId);
        if (document == null) {
            throw new BusinessException("未查询到该文档");
        }

        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(document.getKbId());
        if (knowledgeBase == null || knowledgeBase.getStatus() != 1) {
            throw new BusinessException("未查询到该知识库");
        }

        if (knowledgeBase.getIsTeam() == 0){ //私人知识库鉴权
            if (loginId != knowledgeBase.getOwnerId()){
                throw new BusinessException("您无权从该知识库中删除文件");
            }
        } else if (knowledgeBase.getIsTeam() == 1) { //团队知识库鉴权
            Workspace workspace = workspaceService.getOne(
                    new LambdaQueryWrapper<Workspace>()
                            .eq(Workspace::getKbId, knowledgeBase.getId())
            );

            if (workspace == null || workspace.getStatus() != 1) {
                throw new BusinessException("该团队可能已被解散或封禁");
            }

            WorkspaceMember member = workspaceMemberService.getOne(
                    new LambdaQueryWrapper<WorkspaceMember>()
                            .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                            .eq(WorkspaceMember::getUserId, loginId)
                            .eq(WorkspaceMember::getStatus,1)
                            .eq(WorkspaceMember::getRole, 0)
                            .or()
                            .eq(WorkspaceMember::getRole, 1)
            );

            if (member == null) {
                throw new BusinessException("您无权从该知识库中删除文件");
            }

            //怎加一条团队日志
            String name = userService.getById(loginId).getUsername();
            WorkspaceOperationLog operationLog = new WorkspaceOperationLog();
            operationLog.setWorkspaceId(workspace.getId());
            operationLog.setUserId(loginId);
            operationLog.setUsername(name);
            operationLog.setModule("document");
            operationLog.setOperation("delete");
            operationLog.setStatus(1);
            operationLog.setRequestSummary(
                    "用户: "
                            +name
                            +" 将 "
                            +document.getFileName()
                            +" 从该团队知识库中删除"
            );

            workspaceOperationLogService.save(operationLog);
        }else { //团队字段值异常
            throw new BusinessException("该知识库的团队指向异常");
        }

        //删除知识库中的一条相关文件的业务代码
        //删除mysql中的文件记录
        documentService.removeById(documentId);

        //删除磁盘中的文件
        File file = new File("./docs"+document.getFilePath());
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                throw new BusinessException("文件删除失败");
            }
        }

        //调用python服务端的接口删除milvus中的数据
        Result result = webClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/document/delete")
                        .queryParam("kb_id", knowledgeBase.getId())
                        .queryParam("doc_id", document.getId())
                        .build()
                ).retrieve()
                .bodyToMono(Result.class)
                .block();

        if (result == null || result.getCode() != 200) {
            throw new BusinessException("从知识库中删除该文档失败");
        }

        return Result.success();
    }

    @Override
    public ResponseEntity<Resource> getDocumentContent(Long documentId, String disposition) {
        if (documentId == null || disposition == null) {
            throw new BusinessException("获取文件内容接口的参数接收异常");
        }

        if(!disposition.equals("inline") && !disposition.equals("attachment")) {
            throw new BusinessException("获取文件内容接口的获取形式参数异常");
        }

        //限流模块
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("获取文件内容过于频繁，请稍后再试");
        }

        //鉴权模块
        Document document = documentService.getById(documentId);
        if (document == null) {
            throw new BusinessException("未查询带该文件");
        }
        
        Long kbId = document.getKbId();
        authentication(kbId, loginId);

        //获取该文件内容的业务代码
        File file = new File("./docs"+document.getFilePath());

        if (!file.exists()) {
            throw new BusinessException("该文件不存在");
        }

        Resource resource = new FileSystemResource(file);
        //设置请求头
        String encodedFileName = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String contentDisposition = disposition + "; filename=\"" + encodedFileName + "\"";

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

    private void authentication(Long kbId, long loginId) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(kbId);
        if (knowledgeBase == null || knowledgeBase.getStatus() == 0) {
            throw new BusinessException("未查询到该知识库");
        }

        if (knowledgeBase.getIsTeam() == 0){ 
            if (loginId != knowledgeBase.getOwnerId()){
                throw new BusinessException("你无权获取该知识库中的相关文件信息");
            }
        }else if (knowledgeBase.getIsTeam() == 1) {
            Workspace workspace = workspaceService.getOne(
                    new LambdaQueryWrapper<Workspace>()
                            .eq(Workspace::getKbId, knowledgeBase.getId())
                            .eq(Workspace::getStatus, 1)
            );

            if (workspace == null) {
                throw new BusinessException("该团队可能已被解散或封禁");
            }

            WorkspaceMember member = workspaceMemberService.getOne(
                    new LambdaQueryWrapper<WorkspaceMember>()
                            .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                            .eq(WorkspaceMember::getUserId, loginId)
                            .eq(WorkspaceMember::getStatus, 1)
            );

            if (member == null) {
                throw new BusinessException("你无权获取该知识库中的相关文件信息");
            }
        }else {
            throw new BusinessException("该知识库的团队指向异常");
        }
    }
}
