package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.entity.Notification;
import thinkunderstar.aura.aurabackendserver.service.core.SysNotificationService;

@RestController
@RequestMapping("/notification")
public class SysNotificationController {
    private final SysNotificationService sysNotificationService;

    public SysNotificationController(SysNotificationService sysNotificationService) {
        this.sysNotificationService = sysNotificationService;
    }

    /**
     * 获取我的通知列表（分页）
     * <p>
     * 返回当前登录用户的通知列表，按创建时间倒序排列。
     * 支持按通知类型和已读状态筛选。
     * <p>
     * <b>通知类型：</b>
     * <ul>
     *     <li>report_result - 举报结果通知</li>
     *     <li>feedback_reply - 反馈回复通知</li>
     * </ul>
     *
     * @param page   当前页码，默认1
     * @param size   每页记录数，默认20，最大100
     * @param type   通知类型筛选，可选
     * @param isRead 已读状态筛选（0-未读, 1-已读），可选
     * @return Result 分页通知数据
     */
    @GetMapping("/get")
    @SaCheckLogin
    public Result<IPage<Notification>> getMyNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer isRead
    ) {
        return sysNotificationService.getMyNotifications(page, size, type, isRead);
    }

    /**
     * 标记单条通知为已读
     * <p>
     * 只能标记自己的通知，系统会校验通知归属权。
     *
     * @param notificationId 通知ID
     * @return Result
     */
    @PutMapping("/read")
    @SaCheckLogin
    public Result<Void> readNotification(@RequestParam Long notificationId) {
        return sysNotificationService.readNotification(notificationId);
    }

    /**
     * 一键全部已读
     * <p>
     * 将当前用户所有未读通知标记为已读。
     *
     * @return Result
     */
    @PutMapping("/read-all")
    @SaCheckLogin
    public Result<Void> readAllNotifications() {
        return sysNotificationService.readAllNotifications();
    }

    /**
     * 删除通知（软删除）
     * <p>
     * 将指定通知的 status 标记为 0（已删除）。
     * 只能删除自己的通知。
     *
     * @param notificationId 通知ID
     * @return Result
     */
    @DeleteMapping("/delete")
    @SaCheckLogin
    public Result<Void> deleteNotification(@RequestParam Long notificationId) {
        return sysNotificationService.deleteNotification(notificationId);
    }

    /**
     * 一键清除所有已读通知（软删除）
     * <p>
     * 将当前用户所有已读通知的 status 标记为 0（已删除）。
     *
     * @return Result
     */
    @DeleteMapping("/delete-read")
    @SaCheckLogin
    public Result<Void> deleteReadNotifications() {
        return sysNotificationService.deleteReadNotifications();
    }

    /**
     * 获取未读通知数量
     * <p>
     * 返回当前登录用户的未读通知总数。
     *
     * @return Result 未读数量
     */
    @GetMapping("/unread-count")
    @SaCheckLogin
    public Result<Long> getUnreadCount() {
        return sysNotificationService.getUnreadCount();
    }

    /**
     * 获取已读通知数量
     * <p>
     * 返回当前登录用户的已读通知总数。
     *
     * @return Result 已读数量
     */
    @GetMapping("/read-count")
    @SaCheckLogin
    public Result<Long> getReadCount() {
        return sysNotificationService.getReadCount();
    }
}
