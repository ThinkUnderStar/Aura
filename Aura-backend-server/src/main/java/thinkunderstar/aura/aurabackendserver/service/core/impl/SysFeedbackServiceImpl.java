package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.ReplyFeedbackDto;
import thinkunderstar.aura.aurabackendserver.dto.request.SubmitFeedbackDto;
import thinkunderstar.aura.aurabackendserver.entity.Feedback;
import thinkunderstar.aura.aurabackendserver.entity.Notification;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.service.core.SysFeedbackService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.FeedbackService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.NotificationService;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class SysFeedbackServiceImpl implements SysFeedbackService {
    private final FeedbackService feedbackService;
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final NotificationService notificationService;

    private static final List<String> VALID_TYPES = Arrays.asList("bug", "suggestion", "experience", "other");
    private static final List<Integer> VALID_STATUSES = Arrays.asList(1, 2, 3);

    public SysFeedbackServiceImpl(FeedbackService feedbackService,
                                   RedisTokenBucketLimiter redisTokenBucketLimiter,
                                   NotificationService notificationService) {
        this.feedbackService = feedbackService;
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> submitFeedback(SubmitFeedbackDto submitFeedbackDto) {
        if (submitFeedbackDto == null
                || submitFeedbackDto.getType() == null
                || submitFeedbackDto.getTitle() == null
                || submitFeedbackDto.getContent() == null) {
            throw new BusinessException("提交反馈接口参数接收异常");
        }

        if (submitFeedbackDto.getTitle().isEmpty()) {
            throw new BusinessException("反馈标题不能为空");
        }
        if (submitFeedbackDto.getContent().isEmpty()) {
            throw new BusinessException("反馈内容不能为空");
        }
        if (!VALID_TYPES.contains(submitFeedbackDto.getType())) {
            throw new BusinessException("反馈类型不合法，仅支持: bug, suggestion, experience, other");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 5, 1)) {
            throw new BusinessException("提交反馈过于频繁，请稍后再试");
        }

        Feedback feedback = new Feedback(
                loginId,
                submitFeedbackDto.getType(),
                submitFeedbackDto.getTitle(),
                submitFeedbackDto.getContent(),
                submitFeedbackDto.getContact()
        );

        feedbackService.save(feedback);
        return Result.success();
    }

    @Override
    public Result<IPage<Feedback>> getMyFeedbacks(int page, int size, Integer status) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 20, 5)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, loginId);

        if (status != null) {
            wrapper.eq(Feedback::getStatus, status);
        }

        wrapper.orderByDesc(Feedback::getCreateTime);

        Page<Feedback> pageParam = new Page<>(page, size);
        IPage<Feedback> result = feedbackService.page(pageParam, wrapper);

        return Result.success(result);
    }

    @Override
    public Result<IPage<Feedback>> getFeedbackList(int page, int size, Integer status, String type) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 30, 10)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<>();

        if (status != null) {
            wrapper.eq(Feedback::getStatus, status);
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Feedback::getType, type);
        }

        wrapper.orderByAsc(Feedback::getStatus)
                .orderByDesc(Feedback::getCreateTime);

        Page<Feedback> pageParam = new Page<>(page, size);
        IPage<Feedback> result = feedbackService.page(pageParam, wrapper);

        return Result.success(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> replyFeedback(ReplyFeedbackDto replyFeedbackDto) {
        if (replyFeedbackDto == null
                || replyFeedbackDto.getFeedbackId() == null
                || replyFeedbackDto.getReply() == null
                || replyFeedbackDto.getReply().isEmpty()) {
            throw new BusinessException("回复反馈接口参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 10, 2)) {
            throw new BusinessException("回复反馈过于频繁，请稍后再试");
        }

        Feedback feedback = feedbackService.getById(replyFeedbackDto.getFeedbackId());
        if (feedback == null) {
            throw new BusinessException("反馈记录不存在");
        }

        // 更新回复信息并自动将状态设为已完成
        feedback.setReply(replyFeedbackDto.getReply());
        feedback.setReplyTime(LocalDateTime.now());
        feedback.setHandlerId(loginId);
        feedback.setStatus(2);
        feedbackService.updateById(feedback);

        // 给反馈人发通知
        Notification notification = new Notification(
                feedback.getUserId(),
                "反馈回复通知",
                "您提交的反馈「" + feedback.getTitle() + "」已收到管理员回复：" + replyFeedbackDto.getReply(),
                "feedback_reply",
                feedback.getId()
        );
        notificationService.save(notification);

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateFeedbackStatus(Long feedbackId, Integer status) {
        if (feedbackId == null || status == null) {
            throw new BusinessException("更新反馈状态接口参数接收异常");
        }

        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException("状态值不合法，仅支持: 1-处理中, 2-已完成, 3-已关闭");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 10, 2)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        Feedback feedback = feedbackService.getById(feedbackId);
        if (feedback == null) {
            throw new BusinessException("反馈记录不存在");
        }

        feedback.setStatus(status);
        feedback.setHandlerId(loginId);
        feedbackService.updateById(feedback);

        return Result.success();
    }

    @Override
    public Result<Feedback> getFeedbackDetail(Long feedbackId) {
        if (feedbackId == null) {
            throw new BusinessException("获取反馈详情接口参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 30, 10)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        Feedback feedback = feedbackService.getById(feedbackId);
        if (feedback == null) {
            throw new BusinessException("反馈记录不存在");
        }

        // 仅反馈人本人和管理员可查看详情
        if (!feedback.getUserId().equals(loginId) && !StpUtil.hasRole("admin")) {
            throw new BusinessException("无权查看该反馈详情");
        }

        return Result.success(feedback);
    }
}
