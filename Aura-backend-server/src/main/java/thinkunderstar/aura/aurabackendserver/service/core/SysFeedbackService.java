package thinkunderstar.aura.aurabackendserver.service.core;

import com.baomidou.mybatisplus.core.metadata.IPage;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.ReplyFeedbackDto;
import thinkunderstar.aura.aurabackendserver.dto.request.SubmitFeedbackDto;
import thinkunderstar.aura.aurabackendserver.entity.Feedback;

public interface SysFeedbackService {
    /**
     * 提交反馈
     *
     * @param submitFeedbackDto 反馈请求参数
     * @return Result
     */
    Result<Void> submitFeedback(SubmitFeedbackDto submitFeedbackDto);

    /**
     * 获取我的反馈列表（分页）
     * <p>
     * 查看当前用户提交的所有反馈记录，按创建时间倒序。
     *
     * @param page   当前页码
     * @param size   每页记录数
     * @param status 处理状态筛选，传null表示全部
     * @return Result 分页反馈数据
     */
    Result<IPage<Feedback>> getMyFeedbacks(int page, int size, Integer status);

    /**
     * 管理员获取反馈列表（分页）
     * <p>
     * 管理员查看所有反馈记录，支持按状态和类型筛选。
     *
     * @param page   当前页码
     * @param size   每页记录数
     * @param status 处理状态筛选，传null表示全部
     * @param type   反馈类型筛选，传null表示全部
     * @return Result 分页反馈数据
     */
    Result<IPage<Feedback>> getFeedbackList(int page, int size, Integer status, String type);

    /**
     * 管理员回复反馈
     * <p>
     * 管理员对反馈进行回复，回复后：
     * <ul>
     *     <li>保存回复内容和回复时间</li>
     *     <li>自动将状态更新为"已完成"</li>
     *     <li>自动发送通知给反馈人</li>
     * </ul>
     *
     * @param replyFeedbackDto 回复请求参数
     * @return Result
     */
    Result<Void> replyFeedback(ReplyFeedbackDto replyFeedbackDto);

    /**
     * 管理员更新反馈处理状态
     * <p>
     * 更新状态为：1-处理中, 2-已完成, 3-已关闭
     *
     * @param feedbackId 反馈ID
     * @param status     新状态
     * @return Result
     */
    Result<Void> updateFeedbackStatus(Long feedbackId, Integer status);

    /**
     * 获取反馈详情
     *
     * @param feedbackId 反馈ID
     * @return Result 反馈详情
     */
    Result<Feedback> getFeedbackDetail(Long feedbackId);
}
