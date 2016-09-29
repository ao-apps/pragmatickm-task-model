/*
 * pragmatickm-task-model - Tasks nested within SemanticCMS pages and elements.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of pragmatickm-task-model.
 *
 * pragmatickm-task-model is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * pragmatickm-task-model is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with pragmatickm-task-model.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.pragmatickm.task.model;

/**
 * Task priorities.
 */
public enum Priority {
	FUTURE("Future", "task_priority_future"),
	LOW("Low", "task_priority_low"),
	MEDIUM("Medium", "task_priority_medium"),
	HIGH("High", "task_priority_high"),
	CRITICAL("Critical", "task_priority_critical");

	public static final Priority DEFAULT_PRIORITY = MEDIUM;
	public static final Priority MAX_PRIORITY = CRITICAL;

	private final String display;
	private final String cssClass;

	private Priority(String display, String cssClass) {
		this.display = display;
		this.cssClass = cssClass;
	}

	@Override
	public String toString() {
		return display;
	}

	public String getCssClass() {
		return cssClass;
	}
}
