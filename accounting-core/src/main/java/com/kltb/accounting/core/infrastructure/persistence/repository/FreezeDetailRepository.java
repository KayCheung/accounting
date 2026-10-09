package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountFreezeDetailMapper;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 冻结明细持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class FreezeDetailRepository {

    private final AccountFreezeDetailMapper accountFreezeDetailMapper;

    /**
     * 插入冻结记录
     */
    public void insert(AccountFreezeDetailPO po) {
        if (po == null) {
            throw new AccountException(ResultCode.PARAM_ERROR, "待插入冻结明细对象不能为空");
        }
        // 防御性补全：数据库 NOT NULL 且无默认值的字段统一防 null 兜底
        if (po.getTxnNo() == null) {
            po.setTxnNo("");
        }
        if (po.getBusinessCode() == null) {
            po.setBusinessCode(Constants.DEFAULT_BUSINESS_CODE);
        }
        if (po.getTradingCode() == null) {
            po.setTradingCode(Constants.TRADING_CODE_FREEZE);
        }
        if (po.getTraceNo() == null) {
            po.setTraceNo(cn.hutool.core.util.StrUtil.isNotBlank(po.getVoucherNo()) ? po.getVoucherNo() : "");
        }
        if (po.getTraceSeq() == null) {
            po.setTraceSeq(1);
        }
        int affected = accountFreezeDetailMapper.insert(po);
        if (affected == 0) {
            throw new AccountException(ResultCode.SYSTEM_ERROR,
                    "插入资金冻结明细失败: " + po.getVoucherNo());
        }
    }

    /**
     * 按凭证号查询冻结记录
     *
     * @param voucherNo 冻结编号
     * @return 冻结记录 PO，不存在时返回 null
     */
    public AccountFreezeDetailPO selectByVoucherNo(String voucherNo) {
        return accountFreezeDetailMapper.selectOne(new LambdaQueryWrapper<AccountFreezeDetailPO>()
                .eq(AccountFreezeDetailPO::getVoucherNo, voucherNo)
                .eq(AccountFreezeDetailPO::getIsDelete, 0));
    }

    /**
     * 查询已过期的冻结记录
     *
     * @param now 当前时间
     * @return 已过期且状态为冻结中的记录列表
     */
    public List<AccountFreezeDetailPO> selectExpiredRecords(LocalDateTime now) {
        return accountFreezeDetailMapper.selectExpired(now);
    }

    /**
     * 更新冻结记录状态（乐观锁）
     *
     * @param voucherNo 冻结编号
     * @param status    新状态
     * @param version   当前版本号
     */
    public void updateStatus(String voucherNo, FreezeStatusEnum status, Integer version) {
        int affected = accountFreezeDetailMapper.updateStatus(voucherNo, status.getCode(), version.longValue());
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED, "冻结记录状态更新冲突: " + voucherNo);
        }
    }

    /**
     * 更新冻结记录金额与状态（乐观锁）
     *
     * @param voucherNo       冻结编号
     * @param newFreezeAmount 扣减后的剩余冻结金额
     * @param status          新状态
     * @param version         当前版本号
     */
    public void updateAmountAndStatus(String voucherNo, java.math.BigDecimal newFreezeAmount, FreezeStatusEnum status, Integer version) {
        int affected = accountFreezeDetailMapper.updateAmountAndStatus(voucherNo, newFreezeAmount, status.getCode(), version);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED, "冻结记录金额与状态更新冲突: " + voucherNo);
        }
    }

    /**
     * 统计已过期但未解冻的冻结记录数量（Step 17 P0-8）
     */
    public int countExpiredUnfrozen() {
        return accountFreezeDetailMapper.countExpiredUnfrozen();
    }

    /**
     * 按条件查询冻结记录列表
     *
     * @param wrapper 查询条件
     * @return 冻结记录列表
     */
    public List<AccountFreezeDetailPO> selectByCondition(LambdaQueryWrapper<AccountFreezeDetailPO> wrapper) {
        return accountFreezeDetailMapper.selectList(wrapper);
    }

    /**
     * 分页查询冻结记录（按 accountNo）
     *
     * @param accountNo 账户编号
     * @param status    状态过滤（可选）
     * @param offset    偏移量
     * @param limit     每页条数
     * @return 分页结果（含 total/pages）
     */
    public IPage<AccountFreezeDetailPO> selectPageByAccountNo(String accountNo, Integer status,
                                                               long offset, int limit) {
        return accountFreezeDetailMapper.selectPageByAccountNo(accountNo, status, offset, limit);
    }

    /**
     * 通用条件分页查询冻结记录
     */
    public IPage<AccountFreezeDetailPO> selectPage(LambdaQueryWrapper<AccountFreezeDetailPO> wrapper,
                                                  int pageNo, int pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AccountFreezeDetailPO> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNo, pageSize);
        return accountFreezeDetailMapper.selectPage(page, wrapper);
    }
}
