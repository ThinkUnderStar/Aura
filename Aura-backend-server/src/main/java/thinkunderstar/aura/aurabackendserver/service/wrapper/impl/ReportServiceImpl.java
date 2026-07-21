package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.entity.Report;
import thinkunderstar.aura.aurabackendserver.mapper.ReportMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.ReportService;

@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {
}
