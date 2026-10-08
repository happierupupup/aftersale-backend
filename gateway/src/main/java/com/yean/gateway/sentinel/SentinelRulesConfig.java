package com.yean.gateway.sentinel;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地硬编码限流规则（Demo 不依赖 Sentinel Dashboard / Nacos 规则源）。
 * <ul>
 *   <li>登录：{@link #RES_LOGIN} QPS（WebFilter，因 RouterFunction 不经 Gateway 路由）</li>
 *   <li>申请售后：自定义 API {@code aftersale-apply}</li>
 *   <li>trade 整路由：routeId {@code trade-service} 总保护</li>
 * </ul>
 */
@Slf4j
@Component
public class SentinelRulesConfig {

    /** 与 {@link LoginRateLimitFilter} 资源名一致 */
    public static final String RES_LOGIN = "api-auth-login";

    public static final String API_AFTERSALE_APPLY = "aftersale-apply";

    /**
     * 登录全局限流（单网关实例）。联调/生产向默认值。
     * 演示 429：可临时调低到 3～5，用 curl 连打即可触发。
     */
    private static final double LOGIN_QPS = 30;
    /**
     * 申请售后全局限流。演示可临时调低到 5。
     */
    private static final double APPLY_QPS = 50;
    /**
     * trade / portal 整路由保护。演示可临时调低到 20～50。
     */
    private static final double TRADE_ROUTE_QPS = 500;

    @PostConstruct
    public void init() {
        initLoginFlowRules();
        initGatewayApiDefinitions();
        initGatewayFlowRules();
        log.info("Sentinel rules loaded: login QPS={}, apply QPS={}, trade-route QPS={}",
                LOGIN_QPS, APPLY_QPS, TRADE_ROUTE_QPS);
    }

    private void initLoginFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        FlowRule login = new FlowRule();
        login.setResource(RES_LOGIN);
        login.setGrade(RuleConstant.FLOW_GRADE_QPS);
        login.setCount(LOGIN_QPS);
        login.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(login);
        FlowRuleManager.loadRules(rules);
    }

    private void initGatewayApiDefinitions() {
        Set<ApiDefinition> definitions = new HashSet<>();

        ApiDefinition apply = new ApiDefinition(API_AFTERSALE_APPLY);
        Set<ApiPredicateItem> applyPredict = new HashSet<>();
        applyPredict.add(new ApiPathPredicateItem()
                .setPattern("/aftersale/apply")
                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
        apply.setPredicateItems(applyPredict);
        definitions.add(apply);

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    private void initGatewayFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        rules.add(new GatewayFlowRule(API_AFTERSALE_APPLY)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(APPLY_QPS)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("trade-service")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(TRADE_ROUTE_QPS)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("portal")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(TRADE_ROUTE_QPS)
                .setIntervalSec(1));

        GatewayRuleManager.loadRules(rules);
    }
}
