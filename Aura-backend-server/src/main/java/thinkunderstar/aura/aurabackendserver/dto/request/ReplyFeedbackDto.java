package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class ReplyFeedbackDto {
    /**
     * 反馈ID
     */
    private Long feedbackId;

    /**
     * 管理员回复内容
     */
    private String reply;
}
