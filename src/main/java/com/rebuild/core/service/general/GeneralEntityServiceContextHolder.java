/*!
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.service.general;

import cn.devezhao.persist4j.engine.ID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NamedThreadLocal;
import org.springframework.util.Assert;

/**
 * @author devezhao
 * @since 2020/9/29
 */
@Slf4j
public class GeneralEntityServiceContextHolder {

    private static final ThreadLocal<Boolean> SKIP_SERIES_VALUE = new NamedThreadLocal<>("Skip series value");

    private static final ThreadLocal<ID> ALLOW_FORCE_UPDATE = new NamedThreadLocal<>("Allow force update");

    private static final ThreadLocal<Integer> REPEATED_CHECK_MODE = new NamedThreadLocal<>("Repeated check mode");

    private static final ThreadLocal<ID> FROM_TRIGGERS = new NamedThreadLocal<>("From triggers");

    private static final ThreadLocal<Boolean> QUICK_MODE = new NamedThreadLocal<>("Quick mode");

    private static final ThreadLocal<ID> SKIP_GUARD = new NamedThreadLocal<>("Skip some check once");

    /**
     * 新建记录时允许跳过自动编号字段
     */
    public static void setSkipSeriesValue() {
        SKIP_SERIES_VALUE.set(true);
    }

    /**
     * @param once
     * @return
     * @see #setSkipSeriesValue()
     */
    public static boolean isSkipSeriesValue(boolean once) {
        Boolean s = SKIP_SERIES_VALUE.get();
        if (s != null && once) SKIP_SERIES_VALUE.remove();
        return s != null && s;
    }

    /**
     * 允许强制修改（审批中的）记录
     *
     * @param recordId
     */
    public static void setAllowForceUpdate(ID recordId) {
        ALLOW_FORCE_UPDATE.set(recordId);
    }

    /**
     * @return
     * @see #setAllowForceUpdate(ID)
     */
    public static boolean isAllowForceUpdateOnce() {
        return isAllowForceUpdate(true);
    }

    /**
     * @param once
     * @return
     * @see #setAllowForceUpdate(ID)
     */
    public static boolean isAllowForceUpdate(boolean once) {
        ID s = ALLOW_FORCE_UPDATE.get();
        if (s != null && once) ALLOW_FORCE_UPDATE.remove();
        return s != null;
    }

    /**
     * 从触发器执行允许跳过（某些）权限
     *
     * @param recordId
     */
    public static void setFromTrigger(ID recordId) {
        FROM_TRIGGERS.set(recordId);
    }

    /**
     * @return
     * @see #setFromTrigger(ID)
     */
    public static boolean isFromTrigger(boolean once) {
        ID s = FROM_TRIGGERS.get();
        if (s != null && once) FROM_TRIGGERS.remove();
        return s != null;
    }

    // 检查全部
    public static final int RCM_CHECK_MAIN = 1;
    // 检查主记录
    public static final int RCM_CHECK_DETAILS = 2;
    // 检查明细记录
    public static final int RCM_CHECK_ALL = 4;

    /**
     * 设定重复检查模式（仅在需要时设定）
     *
     * @param mode
     */
    public static void setRepeatedCheckMode(int mode) {
        REPEATED_CHECK_MODE.set(mode);
    }

    /**
     * @return
     * @see #setRepeatedCheckMode(int)
     */
    public static int getRepeatedCheckModeOnce() {
        Integer s = REPEATED_CHECK_MODE.get();
        if (s != null) REPEATED_CHECK_MODE.remove();
        return s == null ? 0 : s;
    }

    /**
     * 忽略一些操作/触发器的执行，达到快速操作的目的
     */
    public static void setQuickMode() {
        QUICK_MODE.set(true);
    }

    /**
     * @param once
     * @return
     * @see #setQuickMode()
     */
    public static boolean isQuickMode(boolean once) {
        Boolean s = QUICK_MODE.get();
        if (s != null && once) QUICK_MODE.remove();
        return s != null && s;
    }

    /**
     * 允许无权限操作一次
     *
     * @param recordId
     */
    public static void setSkipGuard(ID recordId) {
        Assert.notNull(recordId, "[recordId] cannot be null");

        ID existsWarn = SKIP_GUARD.get();
        if (existsWarn != null) {
            log.warn("Not removed skip record : {}", existsWarn);
            SKIP_GUARD.remove();
        }
        SKIP_GUARD.set(recordId);
    }

    /**
     * @return
     * @see #setSkipGuard(ID)
     */
    public static ID isSkipGuardOnce() {
        ID s = SKIP_GUARD.get();
        if (s != null) SKIP_GUARD.remove();
        return s;
    }
}
