package com.kltb.accounting.core.infrastructure.spel;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * SpEL 规则脚本执行器（P0-5）
 */
@Component
public class RuleScriptExecutor {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 执行 SpEL 脚本，返回 BigDecimal 金额
     */
    public BigDecimal execute(String script, Object rootObject) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext(rootObject);
            Object result = PARSER.parseExpression(script).getValue(context);
            if (result == null) {
                throw new AccountException(ResultCode.SPEL_CALC_ERROR, "SpEL 脚本返回 null: " + script);
            }
            if (result instanceof BigDecimal) {
                return (BigDecimal) result;
            }
            if (result instanceof Number) {
                // Critical 3 修复：通过 toString() 构造 BigDecimal，避免 double 精度丢失
                return new BigDecimal(result.toString());
            }
            throw new AccountException(ResultCode.SPEL_CALC_ERROR,
                    "SpEL 脚本返回值非数值类型: " + script + ", 实际类型: " + result.getClass().getName());
        } catch (AccountException e) {
            throw e;
        } catch (Exception e) {
            throw new AccountException(ResultCode.SPEL_CALC_ERROR,
                    "SpEL 脚本执行失败: " + script + ", 错误: " + e.getMessage());
        }
    }
}
