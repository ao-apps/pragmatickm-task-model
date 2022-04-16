/*
 * pragmatickm-task-model - Tasks nested within SemanticCMS pages and elements.
 * Copyright (C) 2015, 2016, 2021, 2022  AO Industries, Inc.
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
 * along with pragmatickm-task-model.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.pragmatickm.task.model;

import com.aoapps.hodgepodge.schedule.DayDuration;
import java.util.Collections;
import java.util.List;

/**
 * Tasks may be assigned multiple priorities.  One of them
 * must have a zero "after" value.  The others must have a non-zero value.
 * The priority that matches the most number of days is used.
 */
public class TaskPriority {

	/**
	 * Some commonly used constant assignments.
	 */
	public static final TaskPriority DEFAULT_TASK_PRIORITY = new TaskPriority(Priority.DEFAULT_PRIORITY, DayDuration.ZERO_DAYS);

	/**
	 * An unmodifiable list with the default task priority.
	 */
	public static final List<TaskPriority> DEFAULT_TASK_PRIORITY_LIST = Collections.singletonList(TaskPriority.DEFAULT_TASK_PRIORITY);

	public static TaskPriority getInstance(Priority priority, DayDuration after) {
		// Shortcuts for constants
		if(priority == Priority.DEFAULT_PRIORITY && after == DayDuration.ZERO_DAYS) return DEFAULT_TASK_PRIORITY;
		// Create new object
		return new TaskPriority(priority, after);
	}

	private final Priority priority;
	private final DayDuration after;

	private TaskPriority(Priority priority, DayDuration after) {
		this.priority = priority;
		if(after.getCount() < 0) throw new IllegalArgumentException("after.count < 0: " + after.getCount());
		this.after = after;
	}

	@Override
	public String toString() {
		if(after.getCount() == 0) return priority.toString();
		StringBuilder sb = new StringBuilder();
		sb.append(priority.toString()).append(" (after ");
		after.toString(sb);
		sb.append(')');
		return sb.toString();
	}

	public Priority getPriority() {
		return priority;
	}

	public DayDuration getAfter() {
		return after;
	}
}
