from sqlalchemy import (Column, BigInteger, Integer, SmallInteger,
                        String, Text, JSON, DateTime, func)

from app.db.mysql.session import Base


class UserEntity(Base):
    """用户表"""
    __tablename__ = "users"

    id = Column(BigInteger, primary_key=True, autoincrement=True, comment="用户ID")
    username = Column(String(50), nullable=False, index=True, comment="用户名")
    password = Column(String(255), nullable=False, comment="BCrypt加密密码")
    phone = Column(String(20), nullable=True, unique=True, comment="手机号")
    email = Column(String(100), nullable=True, unique=True, comment="邮箱")
    avatar = Column(String(255), nullable=True, comment="头像URL")
    role = Column(SmallInteger, nullable=False, default=1, comment="1-普通用户 2-管理员")
    status = Column(SmallInteger, nullable=True, default=1, index=True, comment="1-正常 0-禁用")
    ban_start_time = Column(DateTime, nullable=True, comment="封禁开始时间")
    ban_end_time = Column(DateTime, nullable=True, comment="封禁结束时间（null=永久）")
    ban_reason = Column(String(255), nullable=True, comment="封禁原因")
    ban_by = Column(BigInteger, nullable=True, comment="执行封禁的管理员ID")
    create_time = Column(DateTime, nullable=True, server_default=func.now(), comment="创建时间")
    update_time = Column(DateTime, nullable=True, server_default=func.now(), onupdate=func.now(), comment="更新时间")
    deleted = Column(SmallInteger, nullable=True, default=0, comment="0-未删 1-已删")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")


class AgentEntity(Base):
    """Agent / 对话表"""
    __tablename__ = "agents"

    id = Column(BigInteger, primary_key=True, autoincrement=True, comment="AgentID（对话ID）")
    user_id = Column(BigInteger, nullable=False, index=True, comment="所属用户ID")
    name = Column(String(100), nullable=True, comment="对话名称")
    status = Column(SmallInteger, nullable=True, default=1, index=True, comment="1-活跃 0-已归档")
    create_time = Column(DateTime, nullable=True, server_default=func.now(), comment="创建时间")
    update_time = Column(DateTime, nullable=True, server_default=func.now(), onupdate=func.now(), comment="更新时间")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")


class WorkspaceEntity(Base):
    """工作空间 / 团队表"""
    __tablename__ = "workspaces"

    id = Column(BigInteger, primary_key=True, autoincrement=True, comment="团队ID")
    name = Column(String(100), nullable=False, comment="团队名称")
    description = Column(String(500), nullable=True, comment="团队描述")
    logo = Column(String(255), nullable=True, comment="团队Logo")
    invite_code = Column(String(20), nullable=True, unique=True, comment="邀请码（个人空间为NULL）")
    owner_id = Column(BigInteger, nullable=False, index=True, comment="创建者ID")
    kb_id = Column(BigInteger, nullable=True, index=True, comment="默认知识库ID")
    status = Column(SmallInteger, nullable=True, default=1, index=True, comment="1-正常 0-已解散 2-已归档")
    create_time = Column(DateTime, nullable=True, server_default=func.now(), comment="创建时间")
    update_time = Column(DateTime, nullable=True, server_default=func.now(), onupdate=func.now(), comment="更新时间")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")


class WorkspaceMemberEntity(Base):
    """工作空间成员表"""
    __tablename__ = "workspace_members"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    workspace_id = Column(BigInteger, nullable=False, index=True, comment="团队ID")
    user_id = Column(BigInteger, nullable=False, index=True, comment="用户ID")
    role = Column(SmallInteger, nullable=False, index=True, comment="0-创建者 1-管理员 2-普通成员")
    joined_at = Column(DateTime, nullable=True, server_default=func.now(), comment="加入时间")
    status = Column(Integer, nullable=False, default=1, comment="0-已退出团队 1-在团队中")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")


class MessageEntity(Base):
    """消息表"""
    __tablename__ = "messages"

    id = Column(BigInteger, primary_key=True, autoincrement=True, comment="消息ID")
    agent_id = Column(BigInteger, nullable=False, index=True, comment="所属AgentID（对话ID）")
    role = Column(String(20), nullable=False, comment="user / assistant / tool_confirm")
    content = Column(Text, nullable=True, comment="消息内容")

    # ============== Human 消息独有 创建新的对话分支用 ==============
    from_checkpoint_id = Column(String(255), nullable=True, comment="分支源快照ID（LangGraph checkpoint_id），仅role=human时有效，主分支为NULL")

    # ===== 工具确认专用字段（仅 tool_confirm 时有值） =====
    action = Column(String(20), nullable=True, comment="用户确认动作: approve / reject / edit")
    edited_content = Column(Text, nullable=True, comment="用户编辑后的新内容（仅 action='edit' 时有值）")

    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")
    create_time = Column(DateTime, nullable=True, server_default=func.now(), comment="创建时间")


class KnowledgeBaseEntity(Base):
    """知识库表"""
    __tablename__ = "knowledge_bases"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    owner_id = Column(BigInteger, nullable=False, index=True, comment="创建者ID")
    is_team = Column(SmallInteger, nullable=False, default=0, index=True, comment="1-团队知识库 0-个人知识库")
    name = Column(String(100), nullable=False, comment="知识库名称")
    description = Column(String(500), nullable=True, comment="知识库描述")
    doc_count = Column(Integer, nullable=True, default=0, comment="文档数量")
    status = Column(SmallInteger, nullable=True, default=1, comment="1-启用 0-停用")
    create_time = Column(DateTime, nullable=True, server_default=func.now(), comment="创建时间")
    update_time = Column(DateTime, nullable=True, server_default=func.now(), onupdate=func.now(), comment="更新时间")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")


class DocumentEntity(Base):
    """文档表"""
    __tablename__ = "documents"

    id = Column(BigInteger, primary_key=True, autoincrement=True, comment="文档ID")
    kb_id = Column(BigInteger, nullable=False, index=True, comment="所属知识库ID")
    file_name = Column(String(255), nullable=False, comment="原始文件名")
    file_size = Column(BigInteger, nullable=True, default=0, comment="文件大小（字节）")
    file_type = Column(String(20), nullable=True, comment="pdf / doc / docx / txt / md / xlsx / pptx")
    file_path = Column(String(500), nullable=False, comment="存储路径")
    status = Column(SmallInteger, nullable=True, default=0, index=True, comment="0-处理中 1-已完成 2-失败")
    upload_by = Column(BigInteger, nullable=True, index=True, comment="上传者ID")
    create_time = Column(DateTime, nullable=True, index=True, server_default=func.now(), comment="创建时间")
    update_time = Column(DateTime, nullable=True, server_default=func.now(), onupdate=func.now(), comment="更新时间")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")


class AgentKbBindingEntity(Base):
    """Agent 与知识库绑定表"""
    __tablename__ = "agent_kb_bindings"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    agent_id = Column(BigInteger, nullable=False, index=True, comment="AgentID")
    kb_id = Column(BigInteger, nullable=False, index=True, comment="知识库ID")
    create_time = Column(DateTime, nullable=True, server_default=func.now(), comment="绑定时间")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")


class FeedbackEntity(Base):
    """用户反馈表"""
    __tablename__ = "feedbacks"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(BigInteger, nullable=False, index=True, comment="反馈用户ID")
    type = Column(String(50), nullable=False, comment="反馈类型: bug-功能异常, suggestion-功能建议, experience-使用体验, other-其他")
    title = Column(String(255), nullable=False, comment="反馈标题")
    content = Column(Text, nullable=False, comment="反馈内容")
    contact = Column(String(255), nullable=True, comment="联系方式(选填)")
    status = Column(SmallInteger, nullable=True, default=0, index=True, comment="0-待处理 1-处理中 2-已完成 3-已关闭")
    handler_id = Column(BigInteger, nullable=True, comment="处理人ID(管理员)")
    reply = Column(Text, nullable=True, comment="管理员回复内容")
    reply_time = Column(DateTime, nullable=True, comment="回复时间")
    create_time = Column(DateTime, nullable=True, server_default=func.now(), comment="创建时间")
    update_time = Column(DateTime, nullable=True, server_default=func.now(), onupdate=func.now(), comment="更新时间")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")


class NotificationEntity(Base):
    """通知表"""
    __tablename__ = "notifications"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(BigInteger, nullable=False, index=True, comment="接收通知的用户ID")
    title = Column(String(255), nullable=False, comment="通知标题")
    content = Column(Text, nullable=False, comment="通知内容")
    type = Column(String(50), nullable=False, comment="通知类型: report_result-举报结果, feedback_reply-反馈回复")
    related_id = Column(BigInteger, nullable=True, comment="关联业务ID(report_id或feedback_id)")
    is_read = Column(SmallInteger, nullable=True, default=0, comment="0-未读 1-已读")
    status = Column(SmallInteger, nullable=True, default=1, comment="0-已删除 1-正常")
    create_time = Column(DateTime, nullable=True, server_default=func.now(), comment="创建时间")
    update_time = Column(DateTime, nullable=True, server_default=func.now(), onupdate=func.now(), comment="更新时间")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")


class ReportEntity(Base):
    """举报表"""
    __tablename__ = "reports"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    reporter_id = Column(BigInteger, nullable=False, index=True, comment="举报人用户ID")
    reported_user_id = Column(BigInteger, nullable=True, comment="被举报用户ID")
    reported_workspace_id = Column(BigInteger, nullable=True, comment="被举报团队ID")
    target_type = Column(String(50), nullable=False, index=True, comment="举报目标类型: user-用户, workspace-团队, document-文档")
    target_id = Column(BigInteger, nullable=False, comment="举报目标ID")
    reason = Column(String(100), nullable=False, comment="举报原因: spam-垃圾信息, harassment-骚扰, inappropriate-不当内容, violation-违规行为, other-其他")
    description = Column(Text, nullable=True, comment="举报详细描述")
    status = Column(SmallInteger, nullable=True, default=0, index=True, comment="0-待处理 1-已处理 2-已驳回")
    handler_id = Column(BigInteger, nullable=True, comment="处理人ID(管理员)")
    handle_result = Column(String(500), nullable=True, comment="处理结果说明")
    handle_time = Column(DateTime, nullable=True, comment="处理时间")
    create_time = Column(DateTime, nullable=True, server_default=func.now(), comment="创建时间")
    update_time = Column(DateTime, nullable=True, server_default=func.now(), onupdate=func.now(), comment="更新时间")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")


class SensitiveWordEntity(Base):
    """敏感词表"""
    __tablename__ = "sensitive_words"

    id = Column(BigInteger, primary_key=True, autoincrement=True, comment="敏感词ID")
    word = Column(String(50), nullable=False, comment="敏感词")
    type = Column(SmallInteger, nullable=False, default=2, comment="处理方式: 1-替换 2-直接拦截(默认)")
    create_time = Column(DateTime, nullable=True, server_default=func.now(), comment="创建时间")


class WorkspaceOperationLogEntity(Base):
    """工作空间操作日志表"""
    __tablename__ = "workspace_operation_logs"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    workspace_id = Column(BigInteger, nullable=True, index=True, comment="团队ID（个人操作为NULL）")
    user_id = Column(BigInteger, nullable=False, index=True, comment="操作用户ID")
    module = Column(String(30), nullable=False, index=True, comment="操作模块: auth/user/workspace/member/kb/doc/agent/chat")
    operation = Column(String(50), nullable=False, index=True, comment="操作类型: create/update/delete/upload/login/logout")
    request_summary = Column(String(500), nullable=True, comment="请求摘要")
    status = Column(SmallInteger, nullable=True, default=1, comment="1-成功 0-失败")
    create_time = Column(DateTime, nullable=True, index=True, server_default=func.now(), comment="操作时间")
    username = Column(String(50), nullable=False, comment="操作者的用户昵称")
    version = Column(Integer, nullable=False, default=1, comment="乐观锁版本号")
