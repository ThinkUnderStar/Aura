package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.entity.Notification;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.service.core.SysNotificationService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.NotificationService;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

@Slf4j
@Service
public class SysNotificationServiceImpl implements SysNotificationService {
    private final NotificationService notificationService;
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;

    public SysNotificationServiceImpl(NotificationService notificationService,
                                       RedisTokenBucketLimiter redisTokenBucketLimiter) {
        this.notificationService = notificationService;
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
    }

    @Override
    public Result<IPage<Notification>> getMyNotifications(int page, int size, String type, Integer isRead) {
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
            throw new BusinessException("获取通知操作过于频繁，请稍后再试");
        }

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, loginId)
                .eq(Notification::getStatus, 1);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(Notification::getType, type);
        }
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }

        wrapper.orderByDesc(Notification::getCreateTime);

        Page<Notification> pageParam = new Page<>(page, size);
        IPage<Notification> result = notificationService.page(pageParam, wrapper);

        return Result.success(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> readNotification(Long notificationId) {
        if (notificationId == null) {
            throw new BusinessException("标记已读接口参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 20, 5)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        Notification notification = notificationService.getById(notificationId);
        if (notification == null || notification.getStatus() == 0) {
            throw new BusinessException("通知不存在");
        }
        if (!notification.getUserId().equals(loginId)) {
            throw new BusinessException("无权操作该通知");
        }

        notification.setIsRead(1);
        notificationService.updateById(notification);

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> readAllNotifications() {
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 10, 2)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        Notification update = new Notification();
        update.setIsRead(1);
        notificationService.update(update,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, loginId)
                        .eq(Notification::getIsRead, 0)
                        .eq(Notification::getStatus, 1)
        );

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteNotification(Long notificationId) {
        if (notificationId == null) {
            throw new BusinessException("删除通知接口参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 20, 5)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        Notification notification = notificationService.getById(notificationId);
        if (notification == null || notification.getStatus() == 0) {
            throw new BusinessException("通知不存在");
        }
        if (!notification.getUserId().equals(loginId)) {
            throw new BusinessException("无权操作该通知");
        }

        notification.setStatus(0);
        notificationService.updateById(notification);

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteReadNotifications() {
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 10, 2)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        Notification update = new Notification();
        update.setStatus(0);
        notificationService.update(update,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, loginId)
                        .eq(Notification::getIsRead, 1)
                        .eq(Notification::getStatus, 1)
        );

        return Result.success();
    }

    @Override
    public Result<Long> getUnreadCount() {
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 30, 10)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        long count = notificationService.count(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, loginId)
                        .eq(Notification::getIsRead, 0)
                        .eq(Notification::getStatus, 1)
        );

        return Result.success(count);
    }

    @Override
    public Result<Long> getReadCount() {
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 30, 10)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        long count = notificationService.count(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, loginId)
                        .eq(Notification::getIsRead, 1)
                        .eq(Notification::getStatus, 1)
        );

        return Result.success(count);
    }

    /**
     * 定时物理清理已逻辑删除(status=0)的通知：保留 7 天后删除，避免数据无限累积。
     * 用户"清空已读"/"删除通知"只是置 status=0（updateTime 随之刷新），此处做最终清理。
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanDeletedNotifications() {
        log.info("开始执行定时清除已删除通知任务...");
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(7);
            notificationService.remove(
                    new LambdaQueryWrapper<Notification>()
                            .eq(Notification::getStatus, 0)
                            .lt(Notification::getUpdateTime, threshold)
            );
            log.info("定时清除已删除通知任务完成");
        } catch (Exception e) {
            log.error("定时清除已删除通知任务失败", e);
        }
    }
}
