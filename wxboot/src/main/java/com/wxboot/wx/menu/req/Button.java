package com.wxboot.wx.menu.req;

import java.io.Serializable;
import java.util.List;

public class Button implements Serializable{
	private static final long serialVersionUID = 1L;
	// 菜单类型
	private String type;
	// 菜单名称
	private String name;
	// 二级菜单
	private List<Button> sub_button;
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<Button> getSub_button() {
		return sub_button;
	}
	public void setSub_button(List<Button> sub_button) {
		this.sub_button = sub_button;
	}

	
}
