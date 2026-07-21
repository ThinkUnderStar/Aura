package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.HandleReportDto;
import thinkunderstar.aura.aurabackendserver.dto.request.SubmitReportDto;
import thinkunderstar.aura.aurabackendserver.entity.Report;
import thinkunderstar.aura.aurabackendserver.service.core.SysReportService;

@RestController
@RequestMapping("/report")
public class SysReportController {
    private final SysReportService sysReportService;

    public SysReportController(SysReportService sysReportService) {
        this.sysReportService = sysReportService;
    }

    /**
     * 提交举报
     * <p>
     * 用户提交对某个目标（用户/团队/文档）的举报。
     * 提交成功后将由管理员进行审核处理。
     * <p>
     * <b>校验规则：</b>
     * <ul>
     *     <li>targetType 只能为: user, workspace, document</li>
     *     <li>reason 只能为: spam, harassment, inappropriate, violation, other</li>
     *     <li>不能举报自己</li>
     * </ul>
     *
     * @param submitReportDto 举报请求参数
     * @return Result
     */
    @PostMapping("/submit")
    @SaCheckLogin
    public Result<Void> submitReport(@RequestBody SubmitReportDto submitReportDto) {
        return sysReportService.submitReport(submitReportDto);
    }

    /**
     * 获取我的举报列表（分页）
     * <p>
     * 查看当前用户提交的所有举报记录，按创建时间倒序排列。
     *
     * @param page   当前页码，默认1
     * @param size   每页记录数，默认20，最大100
     * @param status 处理状态筛选（0-待处理, 1-已处理, 2-已驳回），可选
     * @return Result 分页举报数据
     */
    @GetMapping("/my")
    @SaCheckLogin
    public Result<IPage<Report>> getMyReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status
    ) {
        return sysReportService.getMyReports(page, size, status);
    }

    /**
     * 管理员获取举报列表（分页）
     * <p>
     * 管理员查看所有用户的举报记录。
     * 按处理状态升序（待处理优先）、创建时间倒序排列。
     *
     * @param page       当前页码，默认1
     * @param size       每页记录数，默认20，最大100
     * @param status     处理状态筛选，可选
     * @param targetType 目标类型筛选（user/workspace/document），可选
     * @return Result 分页举报数据
     */
    @GetMapping("/list")
    @SaCheckLogin
    @SaCheckRole("admin")
    public Result<IPage<Report>> getReportList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String targetType
    ) {
        return sysReportService.getReportList(page, size, status, targetType);
    }

    /**
     * 管理员处理举报
     * <p>
     * 管理员对举报进行审核处理，处理完成后：
     * <ul>
     *     <li>更新举报状态（1-已处理 / 2-已驳回）和处理结果说明</li>
     *     <li>自动发送通知给举报人告知处理结果</li>
     *     <li>如果处理通过且被举报对象为用户，同时通知被举报人</li>
     * </ul>
     * <p>
     * <b>注意：</b>已处理过的举报不可重复操作。
     *
     * @param handleReportDto 处理请求参数，含举报ID、处理状态、结果说明
     * @return Result
     */
    @PutMapping("/handle")
    @SaCheckLogin
    @SaCheckRole("admin")
    public Result<Void> handleReport(@RequestBody HandleReportDto handleReportDto) {
        return sysReportService.handleReport(handleReportDto);
    }

    /**
     * 获取举报详情
     * <p>
     * 查看指定举报的完整信息，包括举报人信息、被举报对象、处理结果等。
     *
     * @param reportId 举报ID
     * @return Result 举报详情
     */
    @GetMapping("/detail")
    @SaCheckLogin
    public Result<Report> getReportDetail(@RequestParam Long reportId) {
        return sysReportService.getReportDetail(reportId);
    }
}
