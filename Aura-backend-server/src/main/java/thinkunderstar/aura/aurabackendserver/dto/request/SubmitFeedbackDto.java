package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class SubmitFeedbackDto {
    /**
     * 反馈类型: bug, suggestion, experience, other
     */
    private String type;

    private String title;
    private String content;

    /**
     * 联系方式(选填)
     */
    private String contact;
}
