package com.kltb.accounting.core.infrastructure.spel;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessDetailPO;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * SpEL 规则脚本执行器（支持分录金额计算、扩展属性读取及模板表达式动态求值）
 */
@Component
public class RuleScriptExecutor {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 执行 SpEL 脚本，返回 BigDecimal 金额
     */
    public BigDecimal execute(String script, Object rootObject) {
        return execute(script, rootObject, null);
    }

    /**
     * 执行 SpEL 脚本，返回 BigDecimal 金额，支持额外变量注入
     */
    public BigDecimal execute(String script, Object rootObject, Map<String, Object> variables) {
        try {
            StandardEvaluationContext context = buildEvaluationContext(rootObject, variables);
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

    /**
     * 执行模板表达式（解析包含 #{...} 的动态字符串，如动态辅助核算编码）
     */
    public String executeTemplate(String expression, Object rootObject) {
        if (expression == null || !expression.contains("#{")) {
            return expression;
        }
        try {
            StandardEvaluationContext context = buildEvaluationContext(rootObject, null);
            Object result = PARSER.parseExpression(expression, ParserContext.TEMPLATE_EXPRESSION).getValue(context);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            throw new AccountException(ResultCode.SPEL_CALC_ERROR,
                    "SpEL 动态模板表达式求值失败: " + expression + ", 错误: " + e.getMessage());
        }
    }

    private StandardEvaluationContext buildEvaluationContext(Object rootObject, Map<String, Object> variables) {
        StandardEvaluationContext context = new StandardEvaluationContext(rootObject);
        if (rootObject instanceof JournalSpelContext spelCtx) {
            context.setVariable("amount", spelCtx.getAmount());
            context.setVariable("extra", spelCtx.getExtra());
            context.setVariable("detailExtra", spelCtx.getDetailExtra());
            context.setVariable("detail", spelCtx.getDetail());
            context.setVariable("journal", spelCtx.getJournal());
        } else if (rootObject instanceof BusinessDetailPO detailPO) {
            context.setVariable("amount", detailPO.getAmount());
            context.setVariable("detail", detailPO);
        }
        if (variables != null && !variables.isEmpty()) {
            variables.forEach(context::setVariable);
        }
        return context;
    }
}
