package thinkunderstar.aura.aurabackendserver.service.core;

import com.baomidou.mybatisplus.core.metadata.IPage;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.HandleReportDto;
import thinkunderstar.aura.aurabackendserver.dto.request.SubmitReportDto;
import thinkunderstar.aura.aurabackendserver.entity.Report;

public interface SysReportService {
    /**
     * 提交举报
     * <p>
     * 用户提交对某个目标（用户/团队/文档）的举报。
     * 提交后自动根据 targetType 填充对应的被举报对象字段。
     *
     * @param submitReportDto 举报请求参数
     * @return Result
     */
    Result<Void> submitReport(SubmitReportDto submitReportDto);

    /**
     * 获取我的举报列表（分页）
     * <p>
     * 查看当前用户提交的所有举报记录，按创建时间倒序。
     *
     * @param page   当前页码
     * @param size   每页记录数
     * @param status 处理状态筛选（0-待处理, 1-已处理, 2-已驳回），传null表示全部
     * @return Result 分页举报数据
     */
    Result<IPage<Report>> getMyReports(int page, int size, Integer status);

    /**
     * 管理员获取举报列表（分页）
     * <p>
     * 管理员查看所有举报记录，支持按状态和目标类型筛选。
     *
     * @param page       当前页码
     * @param size       每页记录数
     * @param status     处理状态筛选，传null表示全部
     * @param targetType 目标类型筛选，传null表示全部
     * @return Result 分页举报数据
     */
    Result<IPage<Report>> getReportList(int page, int size, Integer status, String targetType);

    /**
     * 管理员处理举报
     * <p>
     * 管理员对举报进行处理（已处理/驳回），处理完成后：
     * <ul>
     *     <li>更新举报状态和结果说明</li>
     *     <li>自动发送通知给举报人</li>
     *     <li>如果处理通过且被举报对象为用户，同时通知被举报人</li>
     * </ul>
     *
     * @param handleReportDto 处理请求参数
     * @return Result
     */
    Result<Void> handleReport(HandleReportDto handleReportDto);

    /**
     * 获取举报详情
     *
     * @param reportId 举报ID
     * @return Result 举报详情
     */
    Result<Report> getReportDetail(Long reportId);
}
