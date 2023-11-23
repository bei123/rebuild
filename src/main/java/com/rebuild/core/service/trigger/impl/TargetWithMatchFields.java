/*!
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.service.trigger.impl;

import cn.devezhao.commons.CalendarUtils;
import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;
import cn.devezhao.persist4j.metadata.MissingMetaExcetion;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.core.Application;
import com.rebuild.core.configuration.general.ClassificationManager;
import com.rebuild.core.metadata.MetadataHelper;
import com.rebuild.core.metadata.easymeta.DisplayType;
import com.rebuild.core.metadata.easymeta.EasyField;
import com.rebuild.core.metadata.easymeta.EasyMetaFactory;
import com.rebuild.core.metadata.impl.EasyFieldConfigProps;
import com.rebuild.core.service.trigger.ActionContext;
import com.rebuild.core.support.i18n.Language;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 寻找目标实体记录
 *
 * @author devezhao
 * @since 2023/10/25
 */
@Slf4j
public class TargetWithMatchFields {

    private Entity sourceEntity;

    @Getter
    private List<String> qFieldsFollow;
    @Getter
    private Object targetRecordId;

    protected TargetWithMatchFields() {
        super();
    }

    /**
     * @param actionContext
     * @return
     */
    public ID match(ActionContext actionContext) {
        return (ID) match(actionContext, false);
    }

    /**
     * @param actionContext
     * @return
     */
    public ID[] matchMulti(ActionContext actionContext) {
        Object o = match(actionContext, true);
        return o == null ? new ID[0] : (ID[]) o;
    }

    private Object match(ActionContext actionContext, boolean m) {
        if (sourceEntity != null) return targetRecordId;  // 已做匹配

        final JSONObject actionContent = (JSONObject) actionContext.getActionContent();
        sourceEntity = actionContext.getSourceEntity();
        Entity targetEntity = MetadataHelper.getEntity(actionContent.getString("targetEntity").split("\\.")[1]);

        // 0.字段关联 <Source, Target>

        Map<String, String> matchFieldsMapping = new HashMap<>();
        for (Object o : actionContent.getJSONArray("targetEntityMatchFields")) {
            JSONObject item = (JSONObject) o;
            String sourceField = item.getString("sourceField");
            String targetField = item.getString("targetField");

            if (MetadataHelper.getLastJoinField(sourceEntity, sourceField) == null) {
                throw new MissingMetaExcetion(sourceField, sourceEntity.getName());
            }
            if (!targetEntity.containsField(targetField)) {
                throw new MissingMetaExcetion(targetField, targetEntity.getName());
            }
            matchFieldsMapping.put(sourceField, targetField);
        }

        if (matchFieldsMapping.isEmpty()) {
            log.warn("No match-fields specified");
            return null;
        }

        // 1.源记录数据

        String aSql = String.format("select %s from %s where %s = ?",
                StringUtils.join(matchFieldsMapping.keySet().iterator(), ","),
                sourceEntity.getName(), sourceEntity.getPrimaryField().getName());

        final Record sourceRecord = Application.createQueryNoFilter(aSql)
                .setParameter(1, actionContext.getSourceRecord())
                .record();

        // 2.找到目标记录

        boolean allNull = true;
        List<String> qFields = new ArrayList<>();
        qFieldsFollow = new ArrayList<>();

        for (Map.Entry<String, String> e : matchFieldsMapping.entrySet()) {
            String sourceField = e.getKey();
            String targetField = e.getValue();

            Object val = sourceRecord.getObjectValue(sourceField);
            if (val == null) {
                qFields.add(String.format("%s is null", targetField));
                qFieldsFollow.add(String.format("%s is null", sourceField));
            } else {
                //noinspection ConstantConditions
                EasyField sourceFieldEasy = EasyMetaFactory.valueOf(MetadataHelper.getLastJoinField(sourceEntity, sourceField));
                //noinspection ConstantConditions
                EasyField targetFieldEasy = EasyMetaFactory.valueOf(MetadataHelper.getLastJoinField(targetEntity, targetField));

                // @see Dimension#getSqlName

                // 日期分组
                if (sourceFieldEasy.getDisplayType() == DisplayType.DATE
                        || sourceFieldEasy.getDisplayType() == DisplayType.DATETIME) {

                    String formatKey = sourceFieldEasy.getDisplayType() == DisplayType.DATE
                            ? EasyFieldConfigProps.DATE_FORMAT
                            : EasyFieldConfigProps.DATETIME_FORMAT;
                    int sourceFieldLength = StringUtils.defaultIfBlank(
                            sourceFieldEasy.getExtraAttr(formatKey), sourceFieldEasy.getDisplayType().getDefaultFormat()).length();

                    // 目标字段仅使用日期
                    int targetFieldLength = StringUtils.defaultIfBlank(
                            targetFieldEasy.getExtraAttr(EasyFieldConfigProps.DATE_FORMAT), targetFieldEasy.getDisplayType().getDefaultFormat()).length();

                    // 目标格式（长度）必须小于等于源格式
                    Assert.isTrue(targetFieldLength <= sourceFieldLength,
                            Language.L("日期字段格式不兼容") + String.format(" (%d,%d)", targetFieldLength, sourceFieldLength));

                    if (targetFieldLength == 4) {  // 'Y'
                        targetField = String.format("DATE_FORMAT(%s,'%s')", targetField, "%Y");
                        val = CalendarUtils.format("yyyy", (Date) val);
                    } else if (targetFieldLength == 7) {  // 'M'
                        targetField = String.format("DATE_FORMAT(%s,'%s')", targetField, "%Y-%m");
                        val = CalendarUtils.format("yyyy-MM", (Date) val);
                    } else {  // 'D' is default
                        targetField = String.format("DATE_FORMAT(%s,'%s')", targetField, "%Y-%m-%d");
                        val = CalendarUtils.format("yyyy-MM-dd", (Date) val);
                    }
                }
                // 分类分组
                else if (sourceFieldEasy.getDisplayType() == DisplayType.CLASSIFICATION) {
                    int sourceFieldLevel = ClassificationManager.instance.getOpenLevel(
                            MetadataHelper.getLastJoinField(sourceEntity, sourceField));
                    int targetFieldLevel = ClassificationManager.instance.getOpenLevel(
                            MetadataHelper.getLastJoinField(targetEntity, targetField));

                    // 目标等级必须小于等于源等级
                    Assert.isTrue(targetFieldLevel <= sourceFieldLevel,
                            Language.L("分类字段等级不兼容") + String.format(" (%d,%d)", targetFieldLevel, sourceFieldLevel));

                    // 需要匹配等级的值
                    if (sourceFieldLevel != targetFieldLevel) {
                        ID parent = getItemWithLevel((ID) val, targetFieldLevel);
                        Assert.isTrue(parent != null, Language.L("分类字段等级不兼容"));

                        val = parent;
                        sourceRecord.setID(sourceField, (ID) val);

                        for (int i = 0; i < sourceFieldLevel - targetFieldLevel; i++) {
                            //noinspection StringConcatenationInLoop
                            sourceField += ".parent";
                        }
                    }
                }

                qFields.add(String.format("%s = '%s'", targetField, val));
                qFieldsFollow.add(String.format("%s = '%s'", sourceField, val));
                allNull = false;
            }
        }

        if (allNull) {
            log.warn("All values of match-fields are null, ignored");
            return null;
        }

        aSql = String.format("select %s from %s where ( %s )",
                targetEntity.getPrimaryField().getName(), targetEntity.getName(),
                StringUtils.join(qFields.iterator(), " and "));

        // 多个 1:N
        if (m) {
            Object[][] array = Application.createQueryNoFilter(aSql).array();
            List<ID> targetRecordIds = new ArrayList<>();
            for (Object[] o : array) targetRecordIds.add((ID) o[0]);

            targetRecordId = targetRecordIds.toArray(new ID[0]);
            return targetRecordId;
        }

        // 单个
        Object[] targetRecord = Application.createQueryNoFilter(aSql).unique();
        targetRecordId = targetRecord == null ? null : (ID) targetRecord[0];
        return targetRecordId;
    }

    /**
     * 分类字段级别
     *
     * @param itemId
     * @param specLevel
     * @return
     */
    static ID getItemWithLevel(ID itemId, int specLevel) {
        ID current = itemId;
        for (int i = 0; i < 4; i++) {
            Object[] o = Application.createQueryNoFilter(
                    "select level,parent from ClassificationData where itemId = ?")
                    .setParameter(1, current)
                    .unique();

            if (o == null) break;
            if ((int) o[0] < specLevel) break;

            if ((int) o[0] == specLevel) return current;
            else current = (ID) o[1];
        }
        return null;
    }
}
