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

import com.aoindustries.util.CalendarUtils;
import com.aoindustries.util.UnmodifiableCalendar;
import com.aoindustries.util.schedule.DayDuration;
import com.aoindustries.util.schedule.Recurring;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.PageRef;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Task objects are short-lived, within the scope of a single request and,
 * consequently, a single thread.  They do not need to be thread safe.
 */
public class Task extends Element {

	private String label;
	private Calendar on;
	private Recurring recurring;
	private boolean relative;
	private List<TaskAssignment> assignedTo;
	private String pay;
	private String cost;
	private List<TaskPriority> priorities;
	private List<TaskLookup> doBefores;
	/* TODO: public static class CustomLog {
		private final String name;
		private final EnumSet<TaskLog.Status> required;
		// indicate which status where task log entry must have a value
	}*/
	private Set<String> customLogs;
	private PageRef xmlFile;

	/**
	 * @throws IllegalStateException if the task has been setup in an inconsistent state
	 */
	@Override
	public Task freeze() throws IllegalStateException {
		super.freeze();
		// At least one person must be assigned the "0 days" task.
		{
			boolean found = false;
			if(assignedTo == null) {
				found = true;
			} else {
				for(TaskAssignment assignment : assignedTo) {
					if(assignment.getAfter().getCount() == 0) {
						found = true;
						break;
					}
				}
			}
			if(!found) throw new IllegalStateException("At least one person must be assigned the \"0 days\" task");
		}
		// One and only one priority may have the "0 days" priority.
		{
			boolean found = false;
			if(priorities == null) {
				found = true;
			} else {
				for(TaskPriority priority : priorities) {
					if(priority.getAfter().getCount() == 0) {
						if(found) throw new IllegalStateException("Only one priority may be assigned the \"0 days\" task");
						found = true;
					}
				}
			}
			if(!found) throw new IllegalStateException("At least priority must be assigned the \"0 days\" task");
		}
		return this;
	}

	@Override
	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		checkNotFrozen();
		this.label = label==null || label.isEmpty() ? null : label;
	}

	@Override
	protected String getDefaultIdPrefix() {
		return "task";
	}

	public Calendar getOn() {
		return on;
	}

	public void setOn(Calendar on) {
		checkNotFrozen();
		this.on = on;
		checkPriorityAndOn();
	}

	public Recurring getRecurring() {
		return recurring;
	}

	public void setRecurring(Recurring recurring) {
		checkNotFrozen();
		this.recurring = recurring;
	}

	public String getRecurringDisplay() {
		return recurring==null ? null : recurring.getRecurringDisplay();
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
	public List<TaskAssignment> getAssignedTo() {
		if(assignedTo == null) return Collections.singletonList(TaskAssignment.UNASSIGNED);
		return Collections.unmodifiableList(assignedTo);
	}

	/**
	 * Checks if this task is assigned to the given user.
	 *
	 * @return  The assignment or {@literal null} if not assigned to the given person.
	 */
	public TaskAssignment getAssignedTo(User who) {
		if(assignedTo == null) {
			if(who == User.Unassigned) return TaskAssignment.UNASSIGNED;
		} else {
			// Sequential scan to see that person not already in list.  Sequential
			// expected to be fastest since tasks should rarely be assigned to many people
			for(TaskAssignment assignment : assignedTo) {
				if(assignment.getWho() == who) return assignment;
			}
		}
		return null;
	}

	public void addAssignedTo(User who, DayDuration after) {
		checkNotFrozen();
		if(!who.isPerson()) throw new IllegalArgumentException("Not a person: " + who);
		if(assignedTo == null) {
			assignedTo = new ArrayList<TaskAssignment>();
		} else {
			if(getAssignedTo(who) != null) throw new IllegalStateException("Assigned to person twice: " + who);
		}
		assignedTo.add(TaskAssignment.getInstance(who, after));
	}

	public String getPay() {
		return pay;
	}

	public void setPay(String pay) {
		checkNotFrozen();
		this.pay = pay;
	}

	public String getCost() {
		return cost;
	}

	public void setCost(String cost) {
		checkNotFrozen();
		this.cost = cost;
	}

	/**
	 * Gets the priorities of the task.
	 */
	public List<TaskPriority> getPriorities() {
		if(priorities == null) return Collections.singletonList(TaskPriority.DEFAULT_TASK_PRIORITY);
		return Collections.unmodifiableList(priorities);
	}

	public void addPriority(Priority priority, DayDuration after) {
		checkNotFrozen();
		if(priorities == null) {
			priorities = new ArrayList<TaskPriority>();
		} else {
			boolean isZeroDay = after.getCount() == 0;
			if(isZeroDay) {
				// Must not be another zero-day priority
				for(TaskPriority existing : priorities) {
					if(existing.getAfter().getCount() == 0) {
						throw new IllegalStateException("More than one zero-day priority assigned: " + existing.getPriority() + " and " + priority);
					}
				}
			}
		}
		priorities.add(TaskPriority.getInstance(priority, after));
		checkPriorityAndOn();
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
		for(TaskPriority taskPriority : getPriorities()) {
			// priority "after"
			Calendar effectiveDate = UnmodifiableCalendar.unwrapClone(from);
			taskPriority.getAfter().offset(effectiveDate);
			long effectiveTime = effectiveDate.getTimeInMillis();
			if(now >= effectiveTime && effectiveTime > mostFutureMatch) {
				mostFutureMatch = effectiveTime;
				mostFuturePriority = taskPriority.getPriority();
			}
		}
		if(mostFuturePriority != null) {
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
		for(TaskPriority taskPriority : getPriorities()) {
			if(taskPriority.getAfter().getCount() == 0) return taskPriority.getPriority();
		}
		throw new AssertionError("There should always be one, and only one, zero-day TaskPriority");
	}

	/**
	 * Check the consistency between future status and not scheduled.
	 */
	private void checkPriorityAndOn() throws IllegalArgumentException {
		boolean hasFuture = false;
		for(TaskPriority taskPriority : getPriorities()) {
			if(taskPriority.getPriority() == Priority.FUTURE) {
				hasFuture = true;
				break;
			}
		}
		if(hasFuture && on != null) {
			throw new IllegalArgumentException("Tasks with Future priority may not be scheduled");
		}
	}

	public List<TaskLookup> getDoBefores() {
		if(doBefores == null) return Collections.emptyList();
		return Collections.unmodifiableList(doBefores);
	}

	public void addDoBefore(TaskLookup task) {
		checkNotFrozen();
		if(doBefores == null) doBefores = new ArrayList<TaskLookup>();
		doBefores.add(task);
		// TODO: Both directions for page links?
		addPageLink(task.getPageRef());
	}

	public Set<String> getCustomLogs() {
		if(customLogs==null) return Collections.emptySet();
		return Collections.unmodifiableSet(customLogs);
	}

	public void addCustomLog(String name) {
		checkNotFrozen();
		if(name==null || name.isEmpty()) throw new IllegalArgumentException("Empty name");
		if(customLogs==null) customLogs = new LinkedHashSet<String>();
		if(!customLogs.add(name)) throw new IllegalStateException("Custom log added twice: " + name);
	}

	public PageRef getXmlFile() {
		return xmlFile;
	}

	public void setXmlFile(PageRef xmlFile) {
		checkNotFrozen();
		this.xmlFile = xmlFile;
	}

	public TaskLog getTaskLog() {
		if(xmlFile==null) throw new IllegalStateException("xmlFile not set");
		return TaskLog.getTaskLog(xmlFile);
	}

	public static class StatusResult {

		/**
		 * The CSS classes for different overall statuses.
		 */
		public enum StatusCssClass {
			task_status_new,
			task_status_new_waiting_do_after,
			task_status_in_future,
			task_status_due_today,
			task_status_due_today_waiting_do_after,
			task_status_late,
			task_status_late_waiting_do_after,
			task_status_progress,
			task_status_progress_waiting_do_after,
			task_status_completed,
			task_status_missed
		}

		private final StatusCssClass cssClass;
		private final String description;
		private final String comments;
		private final boolean completedSchedule;
		private final boolean readySchedule;
		private final boolean futureSchedule;
		private final UnmodifiableCalendar date;

		StatusResult(
			StatusCssClass cssClass,
			String description,
			String comments,
			boolean completedSchedule,
			boolean readySchedule,
			boolean futureSchedule,
			Calendar date
		) {
			if(completedSchedule && readySchedule) throw new AssertionError("A task may not be both completed and ready");
			if(readySchedule && futureSchedule) throw new AssertionError("A task may not be both ready and future");
			this.cssClass = cssClass;
			this.description = description;
			this.comments = comments;
			this.completedSchedule = completedSchedule;
			this.readySchedule = readySchedule;
			this.futureSchedule = futureSchedule;
			this.date = UnmodifiableCalendar.wrap(date);
		}
		StatusResult(
			TaskLog.Status taskStatus,
			String comments,
			boolean allDoBeforesCompleted,
			boolean futureSchedule,
			Calendar date
		) {
			if(allDoBeforesCompleted) {
				this.cssClass = taskStatus.getStatusCssClass();
				this.description = taskStatus.getLabel();
			} else {
				this.cssClass = taskStatus.getStatusDoBeforeCssClass();
				this.description = taskStatus.getLabelDoBefore();
			}
			this.comments = comments;
			this.completedSchedule = taskStatus.isCompletedSchedule();
			this.readySchedule = allDoBeforesCompleted && !taskStatus.isCompletedSchedule();
			this.futureSchedule = futureSchedule;
			this.date = UnmodifiableCalendar.wrap(date);
		}

		public StatusCssClass getCssClass() {
			return cssClass;
		}

		public String getDescription() {
			return description;
		}

		public String getComments() {
			return comments;
		}

		public boolean isCompletedSchedule() {
			return completedSchedule;
		}

		public boolean isReadySchedule() {
			return readySchedule;
		}

		public boolean isFutureSchedule() {
			return futureSchedule;
		}

		public UnmodifiableCalendar getDate() {
			return date;
		}
	}

	/**
	 * <p>
	 * Gets a human-readable description of the task status as well as an associated class.
	 * The status of a task, without any specific qualifying date, is:
	 * </p>
	 * <p>
	 * For non-scheduled tasks (with no "on" date and no "recurring"), the status is:
	 * <ol>
	 *   <li>The status of the most recent log entry with a null "scheduledOn" value</li>
	 *   <li>"New"</li>
	 * </ol>
	 * </p>
	 * <p>
	 * For a scheduled, non-recurring task, the status is:
	 * <ol>
	 *   <li>If the status of the most recent log entry with a "scheduledOn" value equaling the task "on" date is of a "completedSchedule" type - use the status.</li>
	 *   <li>If in the past, "Late YYYY-MM-DD"</li>
	 *   <li>If today, "Due Today"</li>
	 *   <li>If there is a status of the most recent log entry with a "scheduledOn" value equaling the task "on" date - use the status.</li>
	 *   <li>Is in the future, "Waiting until YYYY-MM-DD"</li>
	 * </ol>
	 * </p>
	 * <p>
	 * For a recurring task, the status is:
	 * <ol>
	 *   <li>Find the first incomplete scheduledOn date (based on most recent log entries per scheduled date, in time order</li>
	 *   <li>If the first incomplete is in the past, "Late YYYY-MM-DD"</li>
	 *   <li>If the first incomplete is today, "Due Today"</li>
	 *   <li>If the first incomplete is in the future, "Waiting until YYYY-MM-DD"</li>
	 * </ol>
	 * </p>
	 */
	public StatusResult getStatus() throws TaskException, IOException {
		// Check if all dependencies are completed
		boolean allDoBeforesCompleted = true;
		for(TaskLookup doBeforeLookup : getDoBefores()) {
			Task doBefore = doBeforeLookup.getTask();
			StatusResult doBeforeStatus = doBefore.getStatus();
			if(!doBeforeStatus.isCompletedSchedule()) {
				allDoBeforesCompleted = false;
				break;
			}
		}
		final Calendar today = CalendarUtils.getToday();
		final long todayMillis = today.getTimeInMillis();
		TaskLog taskLog = getTaskLog();
		if(on==null && recurring==null) {
			// Non-scheduled task
			TaskLog.Entry entry = taskLog.getMostRecentEntry(null);
			if(entry != null) {
				TaskLog.Status entryStatus = entry.getStatus();
				if(entryStatus==TaskLog.Status.PROGRESS) {
					// If marked with "Progress" on or after today, will be moved to the future list
					long entryOnMillis = entry.getOn().getTimeInMillis();
					boolean future = entryOnMillis >= todayMillis;
					return new StatusResult(
						TaskLog.Status.PROGRESS.getStatusCssClass(),
						entryOnMillis == todayMillis
							? "Progress Today"
							: ("Progress on " + CalendarUtils.formatDate(entry.getOn())),
						entry.getComments(),
						false,
						!future && allDoBeforesCompleted,
						future,
						null
					);
				} else {
					return new StatusResult(
						entryStatus,
						entry.getComments(),
						allDoBeforesCompleted,
						false,
						null
					);
				}
			}
			if(allDoBeforesCompleted) {
				return new StatusResult(
					StatusResult.StatusCssClass.task_status_new,
					"New",
					null,
					false,
					true,
					false,
					null
				);
			} else {
				return new StatusResult(
					StatusResult.StatusCssClass.task_status_new_waiting_do_after,
					"New waiting for \"Do Before\"",
					null,
					false,
					false,
					false,
					null
				);
			}
		} else if(on!=null && recurring==null) {
			// Scheduled, non-recurring task
			TaskLog.Entry entry = taskLog.getMostRecentEntry(on);
			TaskLog.Status entryStatus = entry==null ? null : entry.getStatus();
			if(entryStatus != null) {
				assert entry != null;
				if(entryStatus.isCompletedSchedule()) {
					return new StatusResult(
						entryStatus,
						entry.getComments(),
						allDoBeforesCompleted,
						false,
						on
					);
				} else if(entryStatus==TaskLog.Status.PROGRESS) {
					long entryOnMillis = entry.getOn().getTimeInMillis();
					if(entryOnMillis >= todayMillis) {
						// If marked with "Progress" on or after today, will be moved to the future list
						return new StatusResult(
							TaskLog.Status.PROGRESS.getStatusCssClass(),
							entryOnMillis == todayMillis
								? "Progress Today"
								: ("Progress on " + CalendarUtils.formatDate(entry.getOn())),
							entry.getComments(),
							false,
							false,
							true,
							on
						);
					}
				}
			}
			// Past
			if(on.before(today)) {
				if(allDoBeforesCompleted) {
					return new StatusResult(
						StatusResult.StatusCssClass.task_status_late,
						"Late " + CalendarUtils.formatDate(on),
						entry!=null ? entry.getComments() : null,
						false,
						true,
						false,
						on
					);
				} else {
					return new StatusResult(
						StatusResult.StatusCssClass.task_status_late_waiting_do_after,
						"Late " + CalendarUtils.formatDate(on) + " waiting for \"Do Before\"",
						entry!=null ? entry.getComments() : null,
						false,
						false,
						false,
						on
					);
				}
			}
			// Present
			if(on.getTimeInMillis() == todayMillis) {
				if(allDoBeforesCompleted) {
					return new StatusResult(
						StatusResult.StatusCssClass.task_status_due_today,
						"Due Today",
						entry!=null ? entry.getComments() : null,
						false,
						true,
						false,
						on
					);
				} else {
					return new StatusResult(
						StatusResult.StatusCssClass.task_status_due_today_waiting_do_after,
						"Due Today waiting for \"Do Before\"",
						entry!=null ? entry.getComments() : null,
						false,
						false,
						false,
						on
					);
				}
			}
			// Future
			if(entryStatus != null) {
				assert entry != null;
				return new StatusResult(
					entryStatus,
					entry.getComments(),
					allDoBeforesCompleted,
					!entryStatus.isCompletedSchedule(),
					on
				);
			}
			return new StatusResult(
				StatusResult.StatusCssClass.task_status_in_future,
				"Waiting until " + CalendarUtils.formatDate(on),
				null,
				false, // Was true, but if never done and waiting for future, it isn't completed
				false,
				true,
				on
			);
		} else {
			// Recurring task (possibly with null "on" date)
			final Calendar firstIncomplete;
			if(relative) {
				// Will use "on" or today if no completed tasklog entry
				Calendar recurringFrom = (on != null) ? on : today;
				// Schedule from most recent completed tasklog entry
				List<TaskLog.Entry> entries = taskLog.getEntries();
				for(int i=entries.size()-1; i>=0; i--) {
					TaskLog.Entry entry = entries.get(i);
					if(entry.getStatus().isCompletedSchedule()) {
						Calendar completedOn = entry.getOn();
						Calendar scheduledOn = entry.getScheduledOn();
						//String checkResult = recurring.checkScheduleFrom(completedOn, "relative");
						//if(checkResult != null) throw new TaskException(checkResult);
						Iterator<Calendar> recurringIter = recurring.getScheduleIterator(completedOn);
						// Find the first date that is after both the completedOn and scheduledOn
						do {
							recurringFrom = recurringIter.next();
						} while(
							recurringFrom.getTimeInMillis() <= completedOn.getTimeInMillis()
							|| (scheduledOn != null && recurringFrom.getTimeInMillis() <= scheduledOn.getTimeInMillis())
						);
						break;
					}
				}
				// If "on" is after the determined recurringFrom, use "on"
				if(on != null && on.getTimeInMillis() > recurringFrom.getTimeInMillis()) {
					recurringFrom = on;
				}
				firstIncomplete = recurringFrom;
			} else {
				if(on == null) throw new TaskException("\"on\" date must be provided for non-relative recurring tasks");
				firstIncomplete = taskLog.getFirstIncompleteScheduledOn(on, recurring);
			}
			if(firstIncomplete.before(today)) {
				TaskLog.Entry entry = taskLog.getMostRecentEntry(firstIncomplete);
				if(entry!=null) {
					TaskLog.Status entryStatus = entry.getStatus();
					if(entryStatus == TaskLog.Status.PROGRESS) {
						long entryOnMillis = entry.getOn().getTimeInMillis();
						if(entryOnMillis >= todayMillis) {
							// If marked with "Progress" on or after today, will be moved to the future list
							return new StatusResult(
								TaskLog.Status.PROGRESS.getStatusCssClass(),
								entryOnMillis == todayMillis
									? "Progress Today"
									: ("Progress on " + CalendarUtils.formatDate(entry.getOn())),
								entry.getComments(),
								false,
								false,
								true,
								firstIncomplete
							);
						}
					}
				}
				if(allDoBeforesCompleted) {
					return new StatusResult(
						StatusResult.StatusCssClass.task_status_late,
						"Late " + CalendarUtils.formatDate(firstIncomplete),
						entry!=null ? entry.getComments() : null,
						false,
						true,
						false,
						firstIncomplete
					);
				} else {
					return new StatusResult(
						StatusResult.StatusCssClass.task_status_late_waiting_do_after,
						"Late " + CalendarUtils.formatDate(firstIncomplete) + " waiting for \"Do Before\"",
						entry!=null ? entry.getComments() : null,
						false,
						false,
						false,
						firstIncomplete
					);
				}
			}
			if(firstIncomplete.getTimeInMillis() == todayMillis) {
				TaskLog.Entry entry = taskLog.getMostRecentEntry(firstIncomplete);
				if(entry!=null) {
					TaskLog.Status entryStatus = entry.getStatus();
					if(entryStatus == TaskLog.Status.PROGRESS) {
						long entryOnMillis = entry.getOn().getTimeInMillis();
						if(entryOnMillis >= todayMillis) {
							// If marked with "Progress" on or after today, will be moved to the future list
							return new StatusResult(
								TaskLog.Status.PROGRESS.getStatusCssClass(),
								entryOnMillis == todayMillis
									? "Progress Today"
									: ("Progress on " + CalendarUtils.formatDate(entry.getOn())),
								entry.getComments(),
								false,
								false,
								true,
								firstIncomplete
							);
						}
					}
				}
				if(allDoBeforesCompleted) {
					return new StatusResult(
						StatusResult.StatusCssClass.task_status_due_today,
						"Due Today",
						entry!=null ? entry.getComments() : null,
						false,
						true,
						false,
						firstIncomplete
					);
				} else {
					return new StatusResult(
						StatusResult.StatusCssClass.task_status_due_today_waiting_do_after,
						"Due Today waiting for \"Do Before\"",
						entry!=null ? entry.getComments() : null,
						false,
						false,
						false,
						firstIncomplete
					);
				}
			}
			return new StatusResult(
				StatusResult.StatusCssClass.task_status_in_future,
				"Waiting until " + CalendarUtils.formatDate(firstIncomplete),
				null,
				true,
				false,
				true,
				firstIncomplete
			);
		}
	}
}
