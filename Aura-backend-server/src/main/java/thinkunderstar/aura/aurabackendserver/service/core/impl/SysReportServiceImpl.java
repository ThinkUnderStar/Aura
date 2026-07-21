package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.HandleReportDto;
import thinkunderstar.aura.aurabackendserver.dto.request.SubmitReportDto;
import thinkunderstar.aura.aurabackendserver.entity.Notification;
import thinkunderstar.aura.aurabackendserver.entity.Report;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.service.core.SysReportService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.NotificationService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.ReportService;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class SysReportServiceImpl implements SysReportService {
    private final ReportService reportService;
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final NotificationService notificationService;

    private static final List<String> VALID_TARGET_TYPES = Arrays.asList("user", "workspace", "document");
    private static final List<String> VALID_REASONS = Arrays.asList("spam", "harassment", "inappropriate", "violation", "other");

    public SysReportServiceImpl(ReportService reportService,
                                 RedisTokenBucketLimiter redisTokenBucketLimiter,
                                 NotificationService notificationService) {
        this.reportService = reportService;
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> submitReport(SubmitReportDto submitReportDto) {
        if (submitReportDto == null
                || submitReportDto.getTargetType() == null
                || submitReportDto.getTargetId() == null
                || submitReportDto.getReason() == null) {
            throw new BusinessException("提交举报接口参数接收异常");
        }

        if (!VALID_TARGET_TYPES.contains(submitReportDto.getTargetType())) {
            throw new BusinessException("举报目标类型不合法，仅支持: user, workspace, document");
        }

        if (!VALID_REASONS.contains(submitReportDto.getReason())) {
            throw new BusinessException("举报原因不合法，仅支持: spam, harassment, inappropriate, violation, other");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 5, 1)) {
            throw new BusinessException("提交举报过于频繁，请稍后再试");
        }

        // 不能举报自己
        if ("user".equals(submitReportDto.getTargetType())
                && submitReportDto.getTargetId().equals(loginId)) {
            throw new BusinessException("不能举报自己");
        }

        Report report = new Report(
                loginId,
                submitReportDto.getTargetType(),
                submitReportDto.getTargetId(),
                submitReportDto.getReason(),
                submitReportDto.getDescription()
        );

        // 根据举报目标类型填充对应字段
        switch (submitReportDto.getTargetType()) {
            case "user":
                report.setReportedUserId(submitReportDto.getTargetId());
                break;
            case "workspace":
                report.setReportedWorkspaceId(submitReportDto.getTargetId());
                break;
            case "document":
                // 文档举报仅记录 targetId
                break;
        }

        reportService.save(report);
        return Result.success();
    }

    @Override
    public Result<IPage<Report>> getMyReports(int page, int size, Integer status) {
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

        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, loginId);

        if (status != null) {
            wrapper.eq(Report::getStatus, status);
        }

        wrapper.orderByDesc(Report::getCreateTime);

        Page<Report> pageParam = new Page<>(page, size);
        IPage<Report> result = reportService.page(pageParam, wrapper);

        return Result.success(result);
    }

    @Override
    public Result<IPage<Report>> getReportList(int page, int size, Integer status, String targetType) {
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

        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();

        if (status != null) {
            wrapper.eq(Report::getStatus, status);
        }
        if (targetType != null && !targetType.isEmpty()) {
            wrapper.eq(Report::getTargetType, targetType);
        }

        wrapper.orderByAsc(Report::getStatus)
                .orderByDesc(Report::getCreateTime);

        Page<Report> pageParam = new Page<>(page, size);
        IPage<Report> result = reportService.page(pageParam, wrapper);

        return Result.success(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> handleReport(HandleReportDto handleReportDto) {
        if (handleReportDto == null
                || handleReportDto.getReportId() == null
                || handleReportDto.getStatus() == null) {
            throw new BusinessException("处理举报接口参数接收异常");
        }

        if (handleReportDto.getStatus() != 1 && handleReportDto.getStatus() != 2) {
            throw new BusinessException("处理状态不合法，仅支持: 1-已处理, 2-已驳回");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 10, 2)) {
            throw new BusinessException("处理举报过于频繁，请稍后再试");
        }

        Report report = reportService.getById(handleReportDto.getReportId());
        if (report == null) {
            throw new BusinessException("举报记录不存在");
        }
        if (report.getStatus() != 0) {
            throw new BusinessException("该举报已被处理，不可重复操作");
        }

        // 更新举报状态
        report.setStatus(handleReportDto.getStatus());
        report.setHandlerId(loginId);
        report.setHandleResult(handleReportDto.getHandleResult());
        report.setHandleTime(LocalDateTime.now());
        reportService.updateById(report);

        // 给举报人发通知
        String statusText = handleReportDto.getStatus() == 1 ? "已处理" : "已驳回";
        Notification reporterNotification = new Notification(
                report.getReporterId(),
                "举报结果通知",
                "您对「" + report.getTargetType() + "」的举报（原因：" + report.getReason() + "）已被管理员" + statusText
                        + (handleReportDto.getHandleResult() != null ? "。处理结果：" + handleReportDto.getHandleResult() : ""),
                "report_result",
                report.getId()
        );
        notificationService.save(reporterNotification);

        // 如果处理通过且被举报对象是用户，同时通知被举报人
        if (handleReportDto.getStatus() == 1
                && "user".equals(report.getTargetType())
                && report.getReportedUserId() != null) {
            Notification targetNotification = new Notification(
                    report.getReportedUserId(),
                    "举报处理通知",
                    "您被其他用户举报（原因：" + report.getReason() + "），管理员已受理并对您做出相应处理。",
                    "report_result",
                    report.getId()
            );
            notificationService.save(targetNotification);
        }

        return Result.success();
    }

    @Override
    public Result<Report> getReportDetail(Long reportId) {
        if (reportId == null) {
            throw new BusinessException("获取举报详情接口参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 30, 10)) {
            throw new BusinessException("操作过于频繁，请稍后再试");
        }

        Report report = reportService.getById(reportId);
        if (report == null) {
            throw new BusinessException("举报记录不存在");
        }

        // 仅举报人本人和管理员可查看详情
        if (!report.getReporterId().equals(loginId) && !StpUtil.hasRole("admin")) {
            throw new BusinessException("无权查看该举报详情");
        }

        return Result.success(report);
    }
}
