/*
rebuild - Building your business-systems freely.
Copyright (C) 2019 devezhao <zhaofang123@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.rebuild.server.portals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.server.Application;
import com.rebuild.server.metadata.MetadataHelper;
import com.rebuild.server.metadata.entityhub.DisplayType;
import com.rebuild.server.metadata.entityhub.EasyMeta;
import com.rebuild.server.portals.value.FieldValueWrapper;
import com.rebuild.utils.JSONUtils;

import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.Field;
import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;

/**
 * 表单自动回填
 * 
 * @author devezhao zhaofang123@gmail.com
 * @since 2019/05/17
 */
public class AutoFillinManager implements PortalsManager {
	
	private static final Log LOG = LogFactory.getLog(AutoFillinManager.class);
	
	/**
	 * 获取回填值
	 * 
	 * @param field
	 * @param source
	 * @return
	 */
	public static JSONArray getFillinValue(Field field, ID source) {
		final List<ConfigEntry> config = getConfig(field);
		
		Entity sourceEntity = MetadataHelper.getEntity(source.getEntityCode());
		Entity targetEntity = field.getOwnEntity();
		Set<String> sourceFields = new HashSet<>();
		for (ConfigEntry e : config) {
			String sourceField = e.getString("source");
			if (!sourceEntity.containsField(sourceField)) {
				LOG.warn("Unknow field '" + sourceField + "' in '" + sourceEntity.getName() + "'");
				continue;
			}
			String targetField = e.getString("target");
			if (!targetEntity.containsField(targetField)) {
				LOG.warn("Unknow field '" + targetField + "' in '" + targetEntity.getName() + "'");
				continue;
			}
			
			Field sourceFieldMeta = sourceEntity.getField(sourceField);
			if (EasyMeta.getDisplayType(sourceFieldMeta) == DisplayType.REFERENCE) {
				sourceFields.add("&" + sourceField);
			}
			sourceFields.add(sourceField);
		}
		if (sourceFields.isEmpty()) {
			return JSONUtils.EMPTY_ARRAY;
		}
		
		String ql = String.format("select %s from %s where %s = ?",
				StringUtils.join(sourceFields, ","),
				sourceEntity.getName(),
				sourceEntity.getPrimaryField().getName());
		Record sourceRecord = Application.createQuery(ql).setParameter(1, source).record();
		if (sourceRecord == null) {
			return JSONUtils.EMPTY_ARRAY;
		}
		
		JSONArray fillin = new JSONArray();
		for (ConfigEntry e : config) {
			String sourceField = e.getString("source");
			Object value = null;
			if (sourceRecord.hasValue(sourceField)) {
				String targetField = e.getString("target");
				value = conversionCompatibleValue(
						sourceEntity.getField(sourceField), targetEntity.getField(targetField),
						sourceRecord.getObjectValue(sourceField));
			}
			
			ConfigEntry clone = e.clone().set("value", value == null ? StringUtils.EMPTY : value);
			clone.set("source", null);
			fillin.add(clone.toJSON());
		}
		return fillin;
	}
	
	/**
	 * 回填值做兼容处理。例如 引用字段回填至文本，要用 Label，而不是 ID 数组
	 * 
	 * @param source
	 * @param target
	 * @param value
	 * @return
	 */
	private static Object conversionCompatibleValue(Field source, Field target, Object value) {
		Object formatted = FieldValueWrapper.wrapFieldValue(value, source);
		return formatted;
	}
	
	/**
	 * 获取配置
	 * 
	 * @param field
	 * @return
	 */
	private static List<ConfigEntry> getConfig(Field field) {
		Object[][] array = Application.createQueryNoFilter(
				"select sourceField,targetField,extConfig from AutoFillinConfig where belongEntity = ? and belongField = ?")
				.setParameter(1, field.getOwnEntity().getName())
				.setParameter(2, field.getName())
				.array();
		
		List<ConfigEntry> entries = new ArrayList<ConfigEntry>();
		for (Object[] o : array) {
			ConfigEntry entry = new ConfigEntry()
					.set("source", o[0])
					.set("target", o[1]);
			JSONObject ext = JSON.parseObject((String) o[2]);
			entry.set("whenCreate", ext.getBoolean("whenCreate"))
					.set("whenUpdate", ext.getBoolean("whenUpdate"))
					.set("fillinForce", ext.getBoolean("fillinForce"));
			entries.add(entry);
		}
		return entries;
	}
}
