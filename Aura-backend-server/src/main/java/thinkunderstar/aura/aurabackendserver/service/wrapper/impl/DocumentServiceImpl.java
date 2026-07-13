package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.entity.Document;
import thinkunderstar.aura.aurabackendserver.mapper.DocumentMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.DocumentService;

@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {
}
