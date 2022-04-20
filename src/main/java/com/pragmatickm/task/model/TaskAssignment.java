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

/**
 * Tasks may be assigned to multiple people.  At least one of them
 * must have a zero "after" value.  The others may have a non-zero value.
 */
public class TaskAssignment {

  /**
   * Some commonly used constant assignments.
   */
  public static final TaskAssignment UNASSIGNED = new TaskAssignment(User.Unassigned, DayDuration.ZERO_DAYS);

  public static TaskAssignment getInstance(User who, DayDuration after) {
    // Shortcuts for constants
    if (who == User.Unassigned && after == DayDuration.ZERO_DAYS) {
      return UNASSIGNED;
    }
    // Create new object
    return new TaskAssignment(who, after);
  }

  private final User who;
  private final DayDuration after;

  private TaskAssignment(User who, DayDuration after) {
    this.who = who;
    if (after.getCount() < 0) {
      throw new IllegalArgumentException("after.count < 0: " + after.getCount());
    }
    this.after = after;
  }

  @Override
  public String toString() {
    if (after.getCount() == 0) {
      return who.toString();
    }
    StringBuilder sb = new StringBuilder();
    sb.append(who.toString()).append(" (after ");
    after.toString(sb);
    sb.append(')');
    return sb.toString();
  }

  public User getWho() {
    return who;
  }

  public DayDuration getAfter() {
    return after;
  }
}
