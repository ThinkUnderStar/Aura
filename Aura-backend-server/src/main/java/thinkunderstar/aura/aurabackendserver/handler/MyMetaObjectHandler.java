package thinkunderstar.aura.aurabackendserver.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.info("开始插入填充...");
        LocalDateTime now = LocalDateTime.now();

        // 1. 填充创建时间
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);

        // 2. 必须同时填充更新时间，否则乐观锁会因为 NULL 而报错！
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);

        // 3. 填充加入时间
        this.strictInsertFill(metaObject, "joinedAt", LocalDateTime.class, now);

        // 4. 填充乐观锁版本字段的初始值
        this.strictInsertFill(metaObject, "version", Integer.class, 1);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("开始更新填充...");
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}