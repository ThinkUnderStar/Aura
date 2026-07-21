package thinkunderstar.aura.aurabackendserver.service.core;

import com.baomidou.mybatisplus.core.metadata.IPage;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.entity.Notification;

public interface SysNotificationService {
    /**
     * 获取当前用户的通知列表（分页）
     * <p>
     * 按创建时间倒序排列，支持按通知类型和已读状态筛选。
     *
     * @param page   当前页码，从1开始
     * @param size   每页记录数
     * @param type   通知类型筛选（report_result / feedback_reply），传null表示全部
     * @param isRead 已读状态筛选（0-未读, 1-已读），传null表示全部
     * @return Result 分页通知数据
     */
    Result<IPage<Notification>> getMyNotifications(int page, int size, String type, Integer isRead);

    /**
     * 标记单条通知为已读
     * <p>
     * 只能操作自己的通知，校验通知归属。
     *
     * @param notificationId 通知ID
     * @return Result
     */
    Result<Void> readNotification(Long notificationId);

    /**
     * 一键标记所有未读通知为已读
     *
     * @return Result
     */
    Result<Void> readAllNotifications();

    /**
     * 软删除通知（status=0）
     * <p>
     * 只能删除自己的通知。
     *
     * @param notificationId 通知ID
     * @return Result
     */
    Result<Void> deleteNotification(Long notificationId);

    /**
     * 获取当前用户未读通知数量
     *
     * @return Result 未读数量
     */
    Result<Long> getUnreadCount();
}
