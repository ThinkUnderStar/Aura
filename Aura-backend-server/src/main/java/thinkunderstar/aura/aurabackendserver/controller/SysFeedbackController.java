package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.ReplyFeedbackDto;
import thinkunderstar.aura.aurabackendserver.dto.request.SubmitFeedbackDto;
import thinkunderstar.aura.aurabackendserver.entity.Feedback;
import thinkunderstar.aura.aurabackendserver.service.core.SysFeedbackService;

@RestController
@RequestMapping("/feedback")
public class SysFeedbackController {
    private final SysFeedbackService sysFeedbackService;

    public SysFeedbackController(SysFeedbackService sysFeedbackService) {
        this.sysFeedbackService = sysFeedbackService;
    }

    /**
     * 提交反馈
     * <p>
     * 用户提交对平台的使用反馈，反馈提交后将由管理员进行查看和回复。
     * <p>
     * <b>参数要求：</b>
     * <ul>
     *     <li>type: 反馈类型，必须为 bug/suggestion/experience/other 之一</li>
     *     <li>title: 反馈标题，不能为空</li>
     *     <li>content: 反馈内容，不能为空</li>
     *     <li>contact: 联系方式，选填</li>
     * </ul>
     *
     * @param submitFeedbackDto 反馈请求参数
     * @return Result
     */
    @PostMapping("/submit")
    @SaCheckLogin
    public Result<Void> submitFeedback(@RequestBody SubmitFeedbackDto submitFeedbackDto) {
        return sysFeedbackService.submitFeedback(submitFeedbackDto);
    }

    /**
     * 获取我的反馈列表（分页）
     * <p>
     * 查看当前用户提交的所有反馈记录，按创建时间倒序排列。
     *
     * @param page   当前页码，默认1
     * @param size   每页记录数，默认20，最大100
     * @param status 处理状态筛选，可选
     * @return Result 分页反馈数据
     */
    @GetMapping("/my")
    @SaCheckLogin
    public Result<IPage<Feedback>> getMyFeedbacks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status
    ) {
        return sysFeedbackService.getMyFeedbacks(page, size, status);
    }

    /**
     * 管理员获取反馈列表（分页）
     * <p>
     * 管理员查看所有用户的反馈记录。
     * 按处理状态升序（待处理优先）、创建时间倒序排列。
     *
     * @param page   当前页码，默认1
     * @param size   每页记录数，默认20，最大100
     * @param status 处理状态筛选，可选
     * @param type   反馈类型筛选，可选
     * @return Result 分页反馈数据
     */
    @GetMapping("/list")
    @SaCheckLogin
    @SaCheckRole("admin")
    public Result<IPage<Feedback>> getFeedbackList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String type
    ) {
        return sysFeedbackService.getFeedbackList(page, size, status, type);
    }

    /**
     * 管理员回复反馈
     * <p>
     * 管理员对用户反馈进行回复，回复后：
     * <ul>
     *     <li>保存回复内容和回复时间</li>
     *     <li>自动将反馈状态更新为"已完成"</li>
     *     <li>自动发送通知给反馈人</li>
     * </ul>
     *
     * @param replyFeedbackDto 回复请求参数
     * @return Result
     */
    @PutMapping("/reply")
    @SaCheckLogin
    @SaCheckRole("admin")
    public Result<Void> replyFeedback(@RequestBody ReplyFeedbackDto replyFeedbackDto) {
        return sysFeedbackService.replyFeedback(replyFeedbackDto);
    }

    /**
     * 管理员更新反馈处理状态
     * <p>
     * 手动更新反馈的处理状态。
     * <p>
     * <b>状态说明：</b>
     * <ul>
     *     <li>1 - 处理中</li>
     *     <li>2 - 已完成</li>
     *     <li>3 - 已关闭</li>
     * </ul>
     *
     * @param feedbackId 反馈ID
     * @param status     新状态（1-处理中, 2-已完成, 3-已关闭）
     * @return Result
     */
    @PutMapping("/status")
    @SaCheckLogin
    @SaCheckRole("admin")
    public Result<Void> updateFeedbackStatus(
            @RequestParam Long feedbackId,
            @RequestParam Integer status
    ) {
        return sysFeedbackService.updateFeedbackStatus(feedbackId, status);
    }

    /**
     * 获取反馈详情
     * <p>
     * 查看指定反馈的完整信息，包括反馈内容、管理员回复等。
     *
     * @param feedbackId 反馈ID
     * @return Result 反馈详情
     */
    @GetMapping("/detail")
    @SaCheckLogin
    public Result<Feedback> getFeedbackDetail(@RequestParam Long feedbackId) {
        return sysFeedbackService.getFeedbackDetail(feedbackId);
    }
}
