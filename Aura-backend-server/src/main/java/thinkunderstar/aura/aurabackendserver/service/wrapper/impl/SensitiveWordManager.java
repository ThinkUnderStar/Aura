package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import cn.hutool.dfa.WordTree;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.entity.SensitiveWord;
import thinkunderstar.aura.aurabackendserver.service.wrapper.SensitiveWordService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SensitiveWordManager {
    private final WordTree wordTree = new WordTree();
    private final SensitiveWordService sensitiveWordService;

    public SensitiveWordManager(SensitiveWordService sensitiveWordService) {
        this.sensitiveWordService = sensitiveWordService;
    }

    /**
     * 初始化敏感词库
     */
    @PostConstruct
    public void init(){
        List<SensitiveWord> list = sensitiveWordService.list(new QueryWrapper<SensitiveWord>().eq("type", 2));
        List<String> sensitiveWordList = list.stream().map(SensitiveWord::getWord).collect(Collectors.toList());

        if (sensitiveWordList.isEmpty()){
            log.error("敏感词加载失败:未查询到任何敏感词");
        }else {
            //加载敏感词到trie数据结构中
            wordTree.addWords(sensitiveWordList);

            log.info("敏感词加载成功，已加载{}条敏感词",sensitiveWordList.size());
        }
    }

    /**
     * 审核是否有敏感词
     *
     * @return 审核结果
     */
    public boolean checkSensitiveWord(String text){
        boolean match = wordTree.isMatch(text);

        if (match){
            log.warn("文本中具有敏感词");
        }

        return match;
    }

}
