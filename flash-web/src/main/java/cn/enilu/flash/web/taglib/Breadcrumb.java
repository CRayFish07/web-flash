package cn.enilu.flash.web.taglib;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * 面包屑导航
 *
 *  @author  enilu(einluzt@qq.com)
 */
public class Breadcrumb {
	/**
	 * 一个导航项目,包含名称和链接
	 */
	public static class Item {
		private final String name;
		private final String link;

		public Item(String name, String link) {
			this.name = name;
			this.link = link;
		}

		public String getName() {
			return name;
		}

		public String getLink() {
			return link;
		}

	}

	private List<Item> items = Lists.newArrayList();

	public Breadcrumb() {
	}

	public Breadcrumb(String... nameLinks) {
		if (nameLinks.length % 2 != 0) {
			throw new IllegalArgumentException("nameLinks must be 2 * n");
		}
		
		for (int i = 0; i < nameLinks.length; i += 2) {
			add(nameLinks[i], nameLinks[i+1]);
		}
	}

	public Breadcrumb add(String name, String link) {
		items.add(new Item(name, link));
		return this;
	}
	public Breadcrumb add(int index,String name, String link) {
		items.add(index,new Item(name, link));
		return this;
	}

	public void clear() {
		items.clear();
	}
	
	public List<Item> getItems() {
		return items;
	}
}
