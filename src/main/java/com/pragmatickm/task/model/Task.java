/*
 * pragmatickm-task-model - Tasks nested within SemanticCMS pages and elements.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2020, 2021, 2022, 2024  AO Industries, Inc.
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

import static com.aoapps.lang.Strings.nullIfEmpty;

import com.aoapps.collections.AoCollections;
import com.aoapps.hodgepodge.schedule.DayDuration;
import com.aoapps.hodgepodge.schedule.Recurring;
import com.aoapps.lang.NullArgumentException;
import com.aoapps.lang.util.UnmodifiableCalendar;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.ElementRef;
import com.semanticcms.core.model.PageRef;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Task extends Element {

  private volatile String label;
  private volatile UnmodifiableCalendar on;
  private volatile Recurring recurring;
  private volatile boolean relative;
  private List<TaskAssignment> assignedTo;
  private volatile String pay;
  private volatile String cost;
  private List<TaskPriority> priorities;
  private Set<ElementRef> doBefores;
  /* TODO: public static class CustomLog {
    private final String name;
    private final EnumSet<TaskLog.Status> required;
    // indicate which status where task log entry must have a value
  }*/
  private Set<String> customLogs;
  private volatile PageRef xmlFile;

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException if the task has been setup in an inconsistent state
   */
  @Override
  public Task freeze() throws IllegalStateException {
    synchronized (lock) {
      if (!frozen) {
        assignedTo = AoCollections.optimalUnmodifiableList(assignedTo);
        priorities = AoCollections.optimalUnmodifiableList(priorities);
        doBefores = AoCollections.optimalUnmodifiableSet(doBefores);
        customLogs = AoCollections.optimalUnmodifiableSet(customLogs);
        super.freeze();
          // At least one person must be assigned the "0 days" task.
          {
            boolean found = false;
            if (assignedTo == null) {
              found = true;
            } else {
              for (TaskAssignment assignment : assignedTo) {
                if (assignment.getAfter().getCount() == 0) {
                  found = true;
                  break;
                }
              }
            }
            if (!found) {
              throw new IllegalStateException("At least one person must be assigned the \"0 days\" task");
            }
          }
          // One and only one priority may have the "0 days" priority.
          {
            boolean found = false;
            if (priorities == null) {
              found = true;
            } else {
              for (TaskPriority priority : priorities) {
                if (priority.getAfter().getCount() == 0) {
                  if (found) {
                    throw new IllegalStateException("Only one priority may be assigned the \"0 days\" task");
                  }
                  found = true;
                }
              }
            }
            if (!found) {
              throw new IllegalStateException("At least priority must be assigned the \"0 days\" task");
            }
          }
      }
    }
    return this;
  }

  @Override
  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    checkNotFrozen();
    this.label = nullIfEmpty(label);
  }

  @Override
  protected String getDefaultIdPrefix() {
    return "task";
  }

  @SuppressWarnings("ReturnOfDateField") // UnmodifiableCalendar
  public UnmodifiableCalendar getOn() {
    return on;
  }

  public void setOn(Calendar on) {
    synchronized (lock) {
      checkNotFrozen();
      this.on = UnmodifiableCalendar.wrap(on);
      checkPriorityAndOn();
    }
  }

  public Recurring getRecurring() {
    return recurring;
  }

  public void setRecurring(Recurring recurring) {
    checkNotFrozen();
    this.recurring = recurring;
  }

  public String getRecurringDisplay() {
    Recurring r = recurring;
    return r == null ? null : r.getRecurringDisplay();
  }

  public boolean getRelative() {
    return relative;
  }

  public void setRelative(boolean relative) {
    checkNotFrozen();
    this.relative = relative;
  }

  /**
   * Gets the users the task is assigned to or a single-element list of "Unassigned"
   * if unassigned.
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public List<TaskAssignment> getAssignedTo() {
    synchronized (lock) {
      if (assignedTo == null) {
        return Collections.singletonList(TaskAssignment.UNASSIGNED);
      }
      if (frozen) {
        return assignedTo;
      }
      return AoCollections.unmodifiableCopyList(assignedTo);
    }
  }

  /**
   * Checks if this task is assigned to the given user.
   *
   * @return  The assignment or {@literal null} if not assigned to the given person.
   */
  public TaskAssignment getAssignedTo(User who) {
    synchronized (lock) {
      if (assignedTo == null) {
        if (who == User.Unassigned) {
          return TaskAssignment.UNASSIGNED;
        }
      } else {
        // Sequential scan to see that person not already in list.  Sequential
        // expected to be fastest since tasks should rarely be assigned to many people
        for (TaskAssignment assignment : assignedTo) {
          if (assignment.getWho() == who) {
            return assignment;
          }
        }
      }
    }
    return null;
  }

  public void addAssignedTo(User who, DayDuration after) {
    synchronized (lock) {
      checkNotFrozen();
      if (!who.isPerson()) {
        throw new IllegalArgumentException("Not a person: " + who);
      }
      if (assignedTo == null) {
        assignedTo = new ArrayList<>();
      } else {
        if (getAssignedTo(who) != null) {
          throw new IllegalStateException("Assigned to person twice: " + who);
        }
      }
      assignedTo.add(TaskAssignment.getInstance(who, after));
    }
  }

  public String getPay() {
    return pay;
  }

  public void setPay(String pay) {
    checkNotFrozen();
    this.pay = nullIfEmpty(pay);
  }

  public String getCost() {
    return cost;
  }

  public void setCost(String cost) {
    checkNotFrozen();
    this.cost = nullIfEmpty(cost);
  }

  /**
   * Gets the priorities of the task.
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public List<TaskPriority> getPriorities() {
    synchronized (lock) {
      if (priorities == null) {
        return TaskPriority.DEFAULT_TASK_PRIORITY_LIST;
      }
      if (frozen) {
        return priorities;
      }
      return AoCollections.unmodifiableCopyList(priorities);
    }
  }

  /**
   * Gets the priorities without defensive copy.
   *
   * <p>Must hold lock already.</p>
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // No defensive copy
  private List<TaskPriority> fastGetPriorities() {
    assert Thread.holdsLock(lock);
    if (priorities == null) {
      return TaskPriority.DEFAULT_TASK_PRIORITY_LIST;
    }
    return priorities;
  }

  public void addPriority(Priority priority, DayDuration after) {
    synchronized (lock) {
      checkNotFrozen();
      if (priorities == null) {
        priorities = new ArrayList<>();
      } else {
        boolean isZeroDay = after.getCount() == 0;
        if (isZeroDay) {
          // Must not be another zero-day priority
          for (TaskPriority existing : priorities) {
            if (existing.getAfter().getCount() == 0) {
              throw new IllegalStateException("More than one zero-day priority assigned: " + existing.getPriority() + " and " + priority);
            }
          }
        }
      }
      priorities.add(TaskPriority.getInstance(priority, after));
      checkPriorityAndOn();
    }
  }

  /**
   * Gets the effective priority, which is the priority with the time best matching the given "now" time.
   *
   * @param  from  the date the priority is being determined from.
   * @param  now   the current system timestamp
   */
  public Priority getPriority(Calendar from, long now) {
    long mostFutureMatch = Long.MIN_VALUE;
    Priority mostFuturePriority = null;
    synchronized (lock) {
      for (TaskPriority taskPriority : fastGetPriorities()) {
        // priority "after"
        Calendar effectiveDate = UnmodifiableCalendar.unwrapClone(from);
        taskPriority.getAfter().offset(effectiveDate);
        long effectiveTime = effectiveDate.getTimeInMillis();
        if (now >= effectiveTime && effectiveTime > mostFutureMatch) {
          mostFutureMatch = effectiveTime;
          mostFuturePriority = taskPriority.getPriority();
        }
      }
    }
    if (mostFuturePriority != null) {
      return mostFuturePriority;
    } else {
      // No matches
      return getZeroDayPriority();
    }
  }

  /**
   * Gets the priority that will be used on the zero day.
   */
  public Priority getZeroDayPriority() {
    synchronized (lock) {
      for (TaskPriority taskPriority : fastGetPriorities()) {
        if (taskPriority.getAfter().getCount() == 0) {
          return taskPriority.getPriority();
        }
      }
    }
    throw new AssertionError("There should always be one, and only one, zero-day TaskPriority");
  }

  /**
   * Check the consistency between future status and not scheduled.
   */
  private void checkPriorityAndOn() throws IllegalArgumentException {
    assert Thread.holdsLock(lock);
    boolean hasFuture = false;
    for (TaskPriority taskPriority : fastGetPriorities()) {
      if (taskPriority.getPriority() == Priority.FUTURE) {
        hasFuture = true;
        break;
      }
    }
    if (hasFuture && on != null) {
      throw new IllegalArgumentException("Tasks with Future priority may not be scheduled");
    }
  }

  /**
   * Gets all the doBefores for this task.
   * It is up to the caller to look-up the referenced elements.
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Set<ElementRef> getDoBefores() {
    synchronized (lock) {
      if (doBefores == null) {
        return Collections.emptySet();
      }
      if (frozen) {
        return doBefores;
      }
      return AoCollections.unmodifiableCopySet(doBefores);
    }
  }

  public void addDoBefore(ElementRef doBefore) {
    synchronized (lock) {
      checkNotFrozen();
      if (doBefores == null) {
        doBefores = new LinkedHashSet<>();
      }
      if (!doBefores.add(doBefore)) {
        throw new IllegalArgumentException("Duplicate doBefore: " + doBefore);
      }
      // TODO: Both directions for page links?
      addPageLink(doBefore.getPageRef());
    }
  }

  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Set<String> getCustomLogs() {
    synchronized (lock) {
      if (customLogs == null) {
        return Collections.emptySet();
      }
      if (frozen) {
        return customLogs;
      }
      return AoCollections.unmodifiableCopySet(customLogs);
    }
  }

  public void addCustomLog(String name) {
    name = nullIfEmpty(name);
    NullArgumentException.checkNotNull(name, "name");
    synchronized (lock) {
      checkNotFrozen();
      if (customLogs == null) {
        customLogs = new LinkedHashSet<>();
      }
      if (!customLogs.add(name)) {
        throw new IllegalStateException("Custom log added twice: " + name);
      }
    }
  }

  public PageRef getXmlFile() {
    return xmlFile;
  }

  public void setXmlFile(PageRef xmlFile) {
    checkNotFrozen();
    this.xmlFile = xmlFile;
  }

  public TaskLog getTaskLog() {
    PageRef xf = xmlFile;
    if (xf == null) {
      throw new IllegalStateException("xmlFile not set");
    }
    return TaskLog.getTaskLog(xf);
  }
}
