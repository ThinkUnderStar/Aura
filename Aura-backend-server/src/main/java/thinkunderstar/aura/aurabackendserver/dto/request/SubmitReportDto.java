package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class SubmitReportDto {
    /**
     * 举报目标类型: user, workspace, document
     */
    private String targetType;

    /**
     * 举报目标ID
     */
    private Long targetId;

    /**
     * 举报原因: spam, harassment, inappropriate, violation, other
     */
    private String reason;

    /**
     * 举报详细描述
     */
    private String description;
}
