package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class HandleReportDto {
    /**
     * 举报ID
     */
    private Long reportId;

    /**
     * 处理状态: 1-已处理, 2-已驳回
     */
    private Integer status;

    /**
     * 处理结果说明
     */
    private String handleResult;
}
