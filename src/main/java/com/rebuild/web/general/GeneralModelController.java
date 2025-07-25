/*!
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.web.general;

import cn.devezhao.commons.CalendarUtils;
import cn.devezhao.commons.web.ServletUtils;
import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.engine.ID;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.core.Application;
import com.rebuild.core.configuration.ConfigBean;
import com.rebuild.core.configuration.general.EasyActionManager;
import com.rebuild.core.configuration.general.FormsBuilder;
import com.rebuild.core.configuration.general.FormsBuilderContextHolder;
import com.rebuild.core.configuration.general.FormsManager;
import com.rebuild.core.configuration.general.TransformManager;
import com.rebuild.core.configuration.general.ViewAddonsManager;
import com.rebuild.core.metadata.EntityHelper;
import com.rebuild.core.metadata.MetadataHelper;
import com.rebuild.core.metadata.easymeta.EasyMetaFactory;
import com.rebuild.core.privileges.UserHelper;
import com.rebuild.core.service.general.transform.RecordTransfomer39;
import com.rebuild.core.service.query.QueryHelper;
import com.rebuild.core.support.ConfigurationItem;
import com.rebuild.core.support.RebuildConfiguration;
import com.rebuild.core.support.i18n.Language;
import com.rebuild.utils.JSONUtils;
import com.rebuild.web.EntityController;
import com.rebuild.web.IdParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表单/视图
 *
 * @author Zixin (RB)
 * @since 08/22/2018
 */
@Slf4j
@RestController
@RequestMapping("/app/{entity}/")
public class GeneralModelController extends EntityController {

    @GetMapping("view/{id}")
    public ModelAndView pageView(@PathVariable String entity, @PathVariable ID id,
                                 HttpServletRequest request, HttpServletResponse response) throws IOException {
        final ID user = getRequestUser(request);
        int status = getAccessibleStatus(entity, user, response);
        if (status > 0) return null;

        final Entity viewEntity = MetadataHelper.getEntity(entity);

        if (Application.devMode() && !Objects.equals(id.getEntityCode(), MetadataHelper.getEntity(entity).getEntityCode())) {
            log.warn("Entity and ID do not match : {}", request.getRequestURI());
        }

        boolean isDetail = viewEntity.getMainEntity() != null;
        ModelAndView mv;
        if (isDetail) {
            mv = createModelAndView("/general/detail-view", id, user);
        } else {
            mv = createModelAndView("/general/record-view", id, user);
        }
        // 视图扩展
        mv.getModel().putAll(getViewExtras(user, entity, isDetail));
        // 显示历史
        mv.getModel().put("ShowViewHistory", RebuildConfiguration.getBool(ConfigurationItem.ShowViewHistory));
        // EasyAction
        mv.getModel().put("easyAction", EasyActionManager.instance.getEasyAction(entity, user));

        mv.getModel().put("id", id);
        return mv;
    }

    private Map<String, Object> getViewExtras(ID user, String entity, boolean isDetail) {
        Map<String, Object> extras = new HashMap<>();
        if (!isDetail) {
            // 视图相关项
            JSONObject vtab = ViewAddonsManager.instance.getViewTab(entity, user);
            extras.put("ViewTabs", vtab.getJSONArray("items"));
            extras.put("ViewTabsAutoExpand", vtab.getBooleanValue("autoExpand"));
            extras.put("ViewTabsAutoHide", vtab.getBooleanValue("autoHide"));
            extras.put("ViewTabsDefaultList", vtab.getBooleanValue("defaultList"));
            JSONObject vadd = ViewAddonsManager.instance.getViewAdd(entity, user);
            extras.put("ViewAdds", vadd.getJSONArray("items"));
        }

        // 记录转换
        JSON trans = TransformManager.instance.getTransforms(entity, user);
        extras.put("TransformTos", trans);

        return extras;
    }

    @RequestMapping("form-model")
    public JSON entityForm(@PathVariable String entity, @IdParam(required = false) ID id,
                           HttpServletRequest request) {
        final ID user = getRequestUser(request);
        final Entity modelEntity = MetadataHelper.getEntity(entity);

        JSON initialVal = null;
        if (id == null) {
            initialVal = ServletUtils.getRequestJson(request);
            if (initialVal != null) {
                // 新建明细记录时必须指定主实体
                String mainid = ((JSONObject) initialVal).getString(FormsBuilder.DV_MAINID);
                if (ID.isId(mainid)) {
                    FormsBuilderContextHolder.setMainIdOfDetail(ID.valueOf(mainid));
                }
                // v2.8
                // v3.9 明细直接新建
                else if (FormsBuilder.DV_MAINID.equals(mainid) || FormsBuilder.DV_MAINID_FJS.equals(mainid)) {
                    ID fakeMainid = EntityHelper.newUnsavedId(modelEntity.getMainEntity().getEntityCode());
                    FormsBuilderContextHolder.setMainIdOfDetail(fakeMainid);
                }
            }
        }

        // 指定布局
        ID specLayout = getIdParameter(request, "layout");
        // v4.1 ProTable 携带主实体布局
        ID followMainLayout = getIdParameter(request, "mainLayout");
        if (followMainLayout != null) {
            FormsBuilderContextHolder.setFromProTable();
            // 明细绑定主实体布局
            if (specLayout == null) {
                ConfigBean cb = FormsBuilder.instance.getLayoutById(followMainLayout);
                Object attrs = cb.getObject("shareTo");
                if (attrs instanceof JSONObject) {
                    JSONObject detailsFromsAttr = (JSONObject) ((JSONObject) attrs).get("detailsFromsAttr");
                    Object specLayout41 = detailsFromsAttr == null ? null : detailsFromsAttr.get(entity);
                    if (ID.isId(specLayout41)) {
                        specLayout = ID.valueOf((String) specLayout41);
                    }
                }
            }
        }

        try {
            if (specLayout != null) FormsBuilderContextHolder.setSpecLayout(specLayout);
            JSON model = FormsBuilder.instance.buildForm(entity, user, id);

            // 填充前端设定的初始值
            if (id == null && initialVal != null) {
                FormsBuilder.instance.setFormInitialValue(modelEntity, model, (JSONObject) initialVal);
            }

            // 明细导入
            if (modelEntity.getDetailEntity() != null) {
                ((JSONObject) model).put("detailImports", RecordTransfomer39.buildDetailImports39(modelEntity));
            }

            return model;

        } finally {
            FormsBuilderContextHolder.getMainIdOfDetail(true);
            FormsBuilderContextHolder.isFromProTable(true);
            FormsBuilderContextHolder.getSpecLayout(true);
        }
    }

    @GetMapping("view-model")
    public JSON entityView(@PathVariable String entity, @IdParam ID id,
                           HttpServletRequest request) {
        final ID user = getRequestUser(request);
        // 指定布局
        final ID forceLayout = getIdParameter(request, "layout");
        if (forceLayout != null) FormsBuilderContextHolder.setSpecLayout(forceLayout);

        JSONObject model;
        try {
            model = (JSONObject) FormsBuilder.instance.buildView(entity, user, id);
        } finally {
            if (forceLayout != null) FormsBuilderContextHolder.getSpecLayout(true);
        }

        // 返回扩展
        if (getBoolParameter(request, "extras") && !model.containsKey("error")) {
            Entity e = MetadataHelper.getEntity(entity);
            model.putAll(getViewExtras(user, entity, e.getMainEntity() != null));
            model.put("entityPrivileges", buildEntityPrivileges(id, user));
            model.put("entityLabel", Language.L(e));
            model.put("isDetail", e.getMainEntity() != null);
        }
        return model;
    }

    // 打印视图
    @GetMapping("print")
    public ModelAndView printPreview(@PathVariable String entity, @IdParam ID recordId, HttpServletRequest request) {
        final ID user = getRequestUser(request);

        ModelAndView mv = createModelAndView("/general/print-preview");
        mv.getModel().put("recordId", recordId);
        // 主记录
        JSON model = FormsBuilder.instance.buildView(entity, user, recordId);
        mv.getModel().put("contentBody", model);

        // v4.1 明细记录
        Entity entity2 = MetadataHelper.getEntity(entity);
        if (entity2.getDetailEntity() != null) {
            JSONArray details = new JSONArray();
            for (Entity de : entity2.getDetialEntities()) {
                List<ID> ids = QueryHelper.detailIdsNoFilter(recordId, de);
                if (ids.isEmpty()) {
                    details.add(JSONUtils.toJSONObject("name", EasyMetaFactory.getLabel(de)));
                    continue;
                }

                ConfigBean forceLayout = FormsManager.instance
                        .getFormLayout(de.getName(), ids.get(0), FormsManager.APPLY_VIEW);
                FormsBuilderContextHolder.setSpecLayout(forceLayout.getID("id"));

                JSONArray dd = new JSONArray();
                try {
                    for (ID did : ids) {
                        JSON m = FormsBuilder.instance.buildForm(de.getName(), user, did);
                        dd.add(m);
                    }
                    details.add(JSONUtils.toJSONObject(
                            new String[]{"name", "details"},
                            new Object[]{EasyMetaFactory.getLabel(de), dd}));

                } finally {
                    FormsBuilderContextHolder.getSpecLayout(true);
                }
            }
            mv.getModel().put("detailsContentBody", details);
        }

        mv.getModel().put("printTime", CalendarUtils.getUTCDateTimeFormat().format(CalendarUtils.now()));
        mv.getModel().put("printUser", UserHelper.getName(user));
        return mv;
    }

    @RequestMapping("detail-models")
    public JSON entityFormDetails(
            @PathVariable String entity, @IdParam(name = "mainid", required = false) ID mainid,
            HttpServletRequest request) {
        final ID user = getRequestUser(request);

        Entity detailEntityMeta = MetadataHelper.getEntity(entity);
        List<ID> ids = QueryHelper.detailIdsNoFilter(mainid, detailEntityMeta);
        if (ids.isEmpty()) return JSONUtils.EMPTY_ARRAY;

        JSONArray details = new JSONArray();

        // v3.7 使用指定记录布局
        ConfigBean forceLayout = FormsManager.instance
                .getFormLayout(detailEntityMeta.getName(), ids.get(0), FormsManager.APPLY_EDIT);
        FormsBuilderContextHolder.setSpecLayout(forceLayout.getID("id"));

        try {
            for (ID did : ids) {
                JSON model = FormsBuilder.instance.buildForm(entity, user, did);
                ((JSONObject) model).put("id", did);
                details.add(model);
            }
        } finally {
            FormsBuilderContextHolder.getSpecLayout(true);
        }

        return details;
    }
}
