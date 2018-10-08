/*
rebuild - Building your system freely.
Copyright (C) 2018 devezhao <zhaofang123@gmail.com>

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

package com.rebuild.server.helper.manager;

import java.text.DecimalFormat;

import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;

import com.rebuild.server.entityhub.AccessibleMeta;
import com.rebuild.server.entityhub.DisplayType;
import com.rebuild.server.metadata.MetadataHelper;

import cn.devezhao.commons.CalendarUtils;
import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.engine.ID;

/**
 * 字段值包装
 * 
 * @author zhaofang123@gmail.com
 * @since 09/23/2018
 */
public class FieldValueWrapper {

	/**
	 * @param date
	 * @param field
	 * @return
	 */
	public static Object wrapFieldValue(Object value, AccessibleMeta field) {
		if (value == null || StringUtils.isBlank(value.toString())) {
			return StringUtils.EMPTY;
		}
		
		// 密码型字段返回
		if (field.getName().toLowerCase().contains("password")) {
			return "******";
		}
		
		DisplayType dt = field.getDisplayType();
		if (dt == DisplayType.DATE) {
			return wrapDate(value, field);
		} else if (dt == DisplayType.DATETIME) {
			return wrapDatetime(value, field);
		} else if (dt == DisplayType.NUMBER) {
			return wrapNumber(value, field);
		} else if (dt == DisplayType.DECIMAL) {
			return wrapDecimal(value, field);
		} else if (dt == DisplayType.REFERENCE) {
			return wrapReference(value, field);
		} else if (/*dt == DisplayType.ID ||*/ dt == DisplayType.PICKLIST 
				|| dt == DisplayType.IMAGE || dt == DisplayType.FILE || dt == DisplayType.LOCATION) {
			// 无需处理
			return value;
		} else if (dt == DisplayType.BOOL) {
			return wrapBool(value, field);
		} else {
			return wrapSimple(value, field);
		}
	}
	
	/**
	 * @param date
	 * @param field
	 * @return
	 */
	public static String wrapDate(Object date, AccessibleMeta field) {
		String format = field.getFieldExtConfig().getString("dateFormat");
		format = StringUtils.defaultIfEmpty(format, field.getDisplayType().getDefaultFormat());
		Assert.notNull(format, "No format : " + field.getBaseMeta());
		return CalendarUtils.getDateFormat(format).format(date);
	}

	/**
	 * @param date
	 * @param field
	 * @return
	 */
	public static String wrapDatetime(Object date, AccessibleMeta field) {
		String format = field.getFieldExtConfig().getString("datetimeFormat");
		format = StringUtils.defaultIfEmpty(format, field.getDisplayType().getDefaultFormat());
		Assert.notNull(format, "No format : " + field.getBaseMeta());
		return CalendarUtils.getDateFormat(format).format(date);
	}
	
	/**
	 * @param number
	 * @param field
	 * @return
	 */
	public static String wrapNumber(Object number, AccessibleMeta field) {
		String format = field.getFieldExtConfig().getString("numberFormat");
		format = StringUtils.defaultIfEmpty(format, field.getDisplayType().getDefaultFormat());
		Assert.notNull(format, "No format : " + field.getBaseMeta());
		return new DecimalFormat(format).format(number);
	}

	/**
	 * @param decimal
	 * @param field
	 * @return
	 */
	public static String wrapDecimal(Object decimal, AccessibleMeta field) {
		String format = field.getFieldExtConfig().getString("decimalFormat");
		format = StringUtils.defaultIfEmpty(format, field.getDisplayType().getDefaultFormat());
		Assert.notNull(format, "No format : " + field.getBaseMeta());
		return new DecimalFormat(format).format(decimal);
	}

	/**
	 * @param reference
	 * @param field
	 * @return
	 */
	public static Object wrapReference(Object reference, AccessibleMeta field) {
		
		// TODO 名称字段
		
		if (!(reference instanceof Object[])) {
			return reference.toString();
		}
		
		Assert.isTrue(reference instanceof Object[], "Must be 'Object[]'");
		Object[] referenceValue = (Object[]) reference;
		Object[] idNamed = new Object[3];
		Entity idEntity = MetadataHelper.getEntity(((ID) referenceValue[0]).getEntityCode());
		idNamed[2] = idEntity.getName();
		idNamed[1] = referenceValue[1] == null ? StringUtils.EMPTY : referenceValue[1].toString();
		idNamed[0] = referenceValue[0].toString();
		return idNamed;
	}
	
	/**
	 * @param bool
	 * @param field
	 * @return
	 */
	public static String wrapBool(Object bool, AccessibleMeta field) {
		return ((Boolean) bool) ? "是" : "否";
	}
	
	/**
	 * @param simple
	 * @param field
	 * @return
	 */
	public static String wrapSimple(Object simple, AccessibleMeta field) {
		return simple.toString().trim();
	}
}
