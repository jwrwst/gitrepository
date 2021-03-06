/**
 * Copyright &copy; 2012-2016 <a href="https://github.com/thinkgem/jeesite">JeeSite</a> All rights reserved.
 */
package com.thinkgem.jeesite.modules.weixin.web;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.thinkgem.jeesite.common.config.Global;
import com.thinkgem.jeesite.common.utils.StringUtils;
import com.thinkgem.jeesite.common.web.BaseController;
import com.thinkgem.jeesite.modules.weixin.entity.WeixinMenu;
import com.thinkgem.jeesite.modules.weixin.service.WeixinMenuService;

/**
 * 微信菜单管理Controller
 * @author WHW
 * @version 2016-08-17
 */
@Controller
@RequestMapping(value = "${adminPath}/weixin/weixinMenu")
public class WeixinMenuController extends BaseController {

	@Autowired
	private WeixinMenuService weixinMenuService;
	
	@ModelAttribute
	public WeixinMenu get(@RequestParam(required=false) String id) {
		WeixinMenu entity = null;
		if (StringUtils.isNotBlank(id)){
			entity = weixinMenuService.get(id);
		}
		if (entity == null){
			entity = new WeixinMenu();
		}
		return entity;
	}
	
	@RequiresPermissions("weixin:weixinMenu:view")
	@RequestMapping(value = {"list", ""})
	public String list(WeixinMenu weixinMenu, HttpServletRequest request, HttpServletResponse response, Model model) {
		List<WeixinMenu> list = weixinMenuService.findList(weixinMenu); 
		model.addAttribute("list", list);
		return "modules/weixin/weixinMenuList";
	}

	@RequiresPermissions("weixin:weixinMenu:view")
	@RequestMapping(value = "form")
	public String form(WeixinMenu weixinMenu, Model model) {
		if (weixinMenu.getParent()!=null && StringUtils.isNotBlank(weixinMenu.getParent().getId())){
			weixinMenu.setParent(weixinMenuService.get(weixinMenu.getParent().getId()));
			// 获取排序号，最末节点排序号+30
			if (StringUtils.isBlank(weixinMenu.getId())){
				WeixinMenu weixinMenuChild = new WeixinMenu();
				weixinMenuChild.setParent(new WeixinMenu(weixinMenu.getParent().getId()));
				List<WeixinMenu> list = weixinMenuService.findList(weixinMenu); 
				if (list.size() > 0){
					weixinMenu.setSort(list.get(list.size()-1).getSort());
					if (weixinMenu.getSort() != null){
						weixinMenu.setSort(weixinMenu.getSort() + 30);
					}
				}
			}
		}
		if (weixinMenu.getSort() == null){
			weixinMenu.setSort(30);
		}
		model.addAttribute("weixinMenu", weixinMenu);
		return "modules/weixin/weixinMenuForm";
	}

	@RequiresPermissions("weixin:weixinMenu:edit")
	@RequestMapping(value = "save")
	public String save(WeixinMenu weixinMenu, Model model, RedirectAttributes redirectAttributes) {
		if (!beanValidator(model, weixinMenu)){
			return form(weixinMenu, model);
		}
		weixinMenuService.save(weixinMenu);
		addMessage(redirectAttributes, "保存菜单成功");
		return "redirect:"+Global.getAdminPath()+"/weixin/weixinMenu/?repage";
	}
	
	@RequiresPermissions("weixin:weixinMenu:edit")
	@RequestMapping(value = "delete")
	public String delete(WeixinMenu weixinMenu, RedirectAttributes redirectAttributes) {
		weixinMenuService.delete(weixinMenu);
		addMessage(redirectAttributes, "删除菜单成功");
		return "redirect:"+Global.getAdminPath()+"/weixin/weixinMenu/?repage";
	}

	@RequiresPermissions("user")
	@ResponseBody
	@RequestMapping(value = "treeData")
	public List<Map<String, Object>> treeData(@RequestParam(required=false) String extId, HttpServletResponse response) {
		List<Map<String, Object>> mapList = Lists.newArrayList();
		List<WeixinMenu> list = weixinMenuService.findList(new WeixinMenu());
		for (int i=0; i<list.size(); i++){
			WeixinMenu e = list.get(i);
			if (StringUtils.isBlank(extId) || (extId!=null && !extId.equals(e.getId()) && e.getParentIds().indexOf(","+extId+",")==-1)){
				Map<String, Object> map = Maps.newHashMap();
				map.put("id", e.getId());
				map.put("pId", e.getParentId());
				map.put("name", e.getName());
				mapList.add(map);
			}
		}
		return mapList;
	}
	
}