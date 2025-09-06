
package com.example.api.subscriber;

import com.example.api.repository.RuleRepository;
import com.example.api.entity.RuleMeta;
import com.example.api.entity.RuleBuildHistory;
import com.example.api.drools.KieManager;
import com.example.api.repository.RuleBuildHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

@Component
public class RuleRedisSubscriber implements MessageListener {

    @Autowired
    private RuleRepository repository;

    @Autowired
    private KieManager kieManager;

    @Autowired
    private RuleBuildHistoryRepository historyRepository;

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        String name = new String(message.getBody());
        System.out.println("[RuleRedisSubscriber] received refresh for: " + name);
        try {
            RuleMeta meta = repository.findByName(name).orElse(null);
            if (meta != null) {
                com.example.api.drools.BuildResult res = kieManager.buildOrUpdateReport(meta.getName(), meta.getContent());
                System.out.println("Rebuild result: " + res.getStatus() + " - " + res.getMessage());
                if (historyRepository != null) {
                    try {
                        RuleBuildHistory h = new RuleBuildHistory();
                        h.setRuleName(meta.getName()); 
                        h.setVersion(meta.getVersion()); 
                        h.setStatus(res.getStatus().name()); 
                        h.setMessage(res.getMessage()); 
                        h.setContent(meta.getContent()); 
                        h.setBuiltBy("subscriber");
                        historyRepository.save(h);
                    } catch (Exception hx) { 
                        hx.printStackTrace(); 
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
