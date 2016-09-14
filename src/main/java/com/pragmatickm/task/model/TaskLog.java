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

import com.aoindustries.lang.NotImplementedException;
import com.aoindustries.lang.NullArgumentException;
import com.aoindustries.util.AoCollections;
import com.aoindustries.util.CalendarUtils;
import com.aoindustries.util.ComparatorUtils;
import com.aoindustries.util.UnmodifiableCalendar;
import com.aoindustries.util.WrappedException;
import com.aoindustries.util.schedule.Recurring;
import com.semanticcms.core.model.PageRef;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * A TaskLog is a persistent list of entries stored for a task.
 * When the data is first accessed, it is read from an XML file.
 * When the XML file updated, the data is re-read.
 * When the data is changed, the XML file is re-written immediately.
 */
public class TaskLog implements Iterable<TaskLog.Entry> {

	private static final String
		ROOT_NODE_NAME             = "tasklog",
		ENTRY_NODE_NAME            = "entry",
		SCHEDULED_ON_NODE_NAME     = "scheduledOn",
		ON_NODE_NAME               = "on",
		STATUS_NODE_NAME           = "status",
		WHO_NODE_NAME              = "who",
		CUSTOM_NODE_NAME           = "custom",
		CUSTOM_NAME_ATTRIBUTE_NAME = "name",
		COMMENTS_NODE_NAME         = "comments"
	;

	public enum Status {
		/**
		 * Progress has been made, but more action still must be taken.
		 */
		PROGRESS(
			"Progress",
			"Progress waiting for \"Do Before\"",
			false,
			Task.StatusResult.StatusCssClass.task_status_progress,
			Task.StatusResult.StatusCssClass.task_status_progress_waiting_do_after
		),
		/**
		 * The task has been completed.
		 */
		COMPLETED(
			"Completed",
			"Completed", // after \"Do Before\"",
			true,
			Task.StatusResult.StatusCssClass.task_status_completed,
			Task.StatusResult.StatusCssClass.task_status_completed
		),
		/**
		 * The task has been completed.
		 */
		NOTHING_TO_DO(
			"Nothing To Do",
			"Nothing To Do after \"Do Before\"",
			true,
			Task.StatusResult.StatusCssClass.task_status_completed,
			Task.StatusResult.StatusCssClass.task_status_completed
		),
		/**
		 * The task was missed and will not be done.
		 */
		MISSED(
			"Missed",
			"Missed after \"Do Before\"",
			true,
			Task.StatusResult.StatusCssClass.task_status_missed,
			Task.StatusResult.StatusCssClass.task_status_missed
		);

		public static Status getStatusByLabel(String label) {
			// Java 1.8: switch(label) {
			if("Progress".equals(label)) return PROGRESS;
			if("Completed".equals(label)) return COMPLETED;
			if("Nothing To Do".equals(label)) return NOTHING_TO_DO;
			if("Missed".equals(label)) return MISSED;
			throw new IllegalArgumentException("Unexpected status label: " + label);
		}

		private final String label;
		private final String labelDoBefore;
		private final boolean completedSchedule;
		private final Task.StatusResult.StatusCssClass statusClass;
		private final Task.StatusResult.StatusCssClass statusDoBeforeClass;
		
		private Status(
			String label,
			String labelDoBefore,
			boolean completedSchedule,
			Task.StatusResult.StatusCssClass statusClass,
			Task.StatusResult.StatusCssClass statusDoBeforeClass
		) {
			this.label = label;
			this.labelDoBefore = labelDoBefore;
			this.completedSchedule = completedSchedule;
			this.statusClass = statusClass;
			this.statusDoBeforeClass = statusDoBeforeClass;
		}

		@Override
		public String toString() {
			return label;
		}

		public String getLabel() {
			return label;
		}

		public String getLabelDoBefore() {
			return labelDoBefore;
		}

		public boolean isCompletedSchedule() {
			return completedSchedule;
		}

		public Task.StatusResult.StatusCssClass getStatusCssClass() {
			return statusClass;
		}
		
		public Task.StatusResult.StatusCssClass getStatusDoBeforeCssClass() {
			return statusDoBeforeClass;
		}

		/**
		 * JavaBeans compatibility.
		 */
		public String getName() {
			return name();
		}
	}

	private static final Comparator<Calendar> calendarInMilliOrderComparator = new Comparator<Calendar>() {
		@Override
		public int compare(Calendar o1, Calendar o2) {
			long millis1 = o1.getTimeInMillis();
			long millis2 = o2.getTimeInMillis();
			return ComparatorUtils.compare(millis1, millis2);
		}
	};

	private static SortedSet<UnmodifiableCalendar> makeUnmodifiable(Set<? extends Calendar> calendars) {
		SortedSet<UnmodifiableCalendar> result = new TreeSet<UnmodifiableCalendar>(calendarInMilliOrderComparator);
		for(Calendar cal : calendars) {
			if(!result.add(UnmodifiableCalendar.wrap(cal))) throw new AssertionError();
		}
		return AoCollections.optimalUnmodifiableSortedSet(result);
	}

	public static class Entry {
		private final SortedSet<UnmodifiableCalendar> scheduledOns;
		private final UnmodifiableCalendar on;
		private final Status status;
		private final List<User> unmodifiableWho;
		private final Map<String,String> unmodifiableCustom;
		private final String comments;

		public Entry(
			Set<? extends Calendar> scheduledOns,
			Calendar on,
			Status status,
			List<User> who,
			Map<String,String> custom,
			String comments
		) {
			if(scheduledOns==null) {
				this.scheduledOns = AoCollections.emptySortedSet();
			} else {
				this.scheduledOns = makeUnmodifiable(scheduledOns);
			}
			this.on = UnmodifiableCalendar.wrap(
				NullArgumentException.checkNotNull(on, "on")
			);
			this.status = status;
			if(who == null) {
				this.unmodifiableWho = Collections.emptyList();
			} else {
				this.unmodifiableWho = AoCollections.unmodifiableCopyList(who);
				for(User user : this.unmodifiableWho) {
					if(!user.isPerson()) throw new IllegalArgumentException("Not a person: " + user);
				}
			}
			if(custom==null) this.unmodifiableCustom = Collections.emptyMap();
			else this.unmodifiableCustom = AoCollections.unmodifiableCopyMap(custom);
			this.comments = comments;
		}

		/**
		 * The "on" dates of the recurring schedule this entries is for, or
		 * empty set if not applies to any schedules.  These are ordered by
		 * time in milliseconds ascending.
		 */
		public SortedSet<UnmodifiableCalendar> getScheduledOns() {
			return scheduledOns;
		}

		/**
		 * The date this action was actually taken.  This may not necessarily
		 * be on the scheduled date, but still counts status toward the scheduled date.
		 */
		public UnmodifiableCalendar getOn() {
			return on;
		}
		
		public Status getStatus() {
			return status;
		}

		public List<User> getWho() {
			return unmodifiableWho;
		}

		public Map<String,String> getCustom() {
			return unmodifiableCustom;
		}

		public String getComments() {
			return comments;
		}
	}

	private static final Map<PageRef,TaskLog> taskLogCache = new HashMap<PageRef,TaskLog>();

	/**
	 * To avoid repetitive parsing, only one TaskLog is created for each unique PageRef.
	 */
	public static TaskLog getTaskLog(PageRef xmlFile) {
		synchronized(taskLogCache) {
			TaskLog taskLog = taskLogCache.get(xmlFile);
			if(taskLog == null) {
				taskLogCache.put(xmlFile, taskLog = new TaskLog(xmlFile));
			}
			return taskLog;
		}
	}

	private final PageRef xmlFile;
	private final Object entriesLock = new Object();
	private long entriesLastModified;
	private List<Entry> unmodifiableEntries;
	private Map<String,List<Entry>> unmodifiableEntriesByScheduledOn;
	//private Map<String,Set<String>> unmodifiableProgressByScheduledOn;
	private UnmodifiableCalendar firstIncompleteFrom;
	private Recurring firstIncompleteRecurring;
	private UnmodifiableCalendar firstIncompleteResult;

	private TaskLog(PageRef xmlFile) {
		this.xmlFile = xmlFile;
	}

	public PageRef getXmlFile() {
		return xmlFile;
	}

	private static final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

	/**
	 * <p>
	 * Gets the set of all entries.  This is a snapshot view and will not change
	 * even when the log has been updated.  To get a new snapshot, call this method
	 * again.
	 * </p>
	 * <p>
	 * Entries are in order by "on" time.
	 * </p>
	 */
	public List<Entry> getEntries() throws IOException {
		try {
			final File resourceFile = xmlFile.getResourceFile(true, false);
			synchronized(entriesLock) {
				long fileLastModified = resourceFile.lastModified();
				if(
					// First access
					unmodifiableEntries==null
					// File updated externally
					|| entriesLastModified != fileLastModified
				) {
					List<Entry> newEntries = new ArrayList<Entry>();
					Entry lastEntry = null;
					if(resourceFile.exists()) {
						DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
						Document document = builder.parse(resourceFile);
						// http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
						Element root = document.getDocumentElement();
						root.normalize();
						if(!ROOT_NODE_NAME.equals(root.getNodeName())) throw new ParseException("Unexpected root element \"" + root.getNodeName() + "\" in " + resourceFile, 0);
						for(
							Node child = root.getFirstChild();
							child != null;
							child = child.getNextSibling()
						) {
							if(child instanceof Element) {
								if(!ENTRY_NODE_NAME.equals(child.getNodeName())) throw new ParseException("Unexpected element \"" + child.getNodeName() + "\" in " + resourceFile, 0);
								Calendar lastScheduledOn = null;
								Set<Calendar> scheduledOns = null;
								Calendar on = null;
								Status status = null;
								List<User> who = null;
								Map<String,String> custom = null;
								String comments = null;
								for(
									Node grandChild = child.getFirstChild();
									grandChild != null;
									grandChild = grandChild.getNextSibling()
								) {
									if(grandChild instanceof Element) {
										Element elem = (Element)grandChild;
										String content = elem.getTextContent();
										String nodeName = elem.getNodeName();
										// Java 1.8: switch(nodeName) {
										if(SCHEDULED_ON_NODE_NAME.equals(nodeName)) {
											if(scheduledOns == null) {
												scheduledOns = new LinkedHashSet<Calendar>();
											}
											Calendar scheduledOn = CalendarUtils.parseDate(content);
											if(lastScheduledOn != null) {
												// Must be in order
												if(scheduledOn.getTimeInMillis() <= lastScheduledOn.getTimeInMillis()) {
													throw new ParseException("Out of order " + SCHEDULED_ON_NODE_NAME + ": " + CalendarUtils.formatDate(scheduledOn) + " <= " + CalendarUtils.formatDate(lastScheduledOn) + " in " + resourceFile, 0);
												}
											}
											lastScheduledOn = scheduledOn;
											if(!scheduledOns.add(scheduledOn)) {
												throw new ParseException("Duplicate " + SCHEDULED_ON_NODE_NAME + " value \"" + content + "\" in " + resourceFile, 0);
											}
										} else if(ON_NODE_NAME.equals(nodeName)) {
											if(on != null) {
												throw new ParseException("Multiple " + ON_NODE_NAME + " tag in " + resourceFile, 0);
											}
											on = CalendarUtils.parseDate(content);
										} else if(STATUS_NODE_NAME.equals(nodeName)) {
											if(status != null) {
												throw new ParseException("Multiple " + STATUS_NODE_NAME + " tag in " + resourceFile, 0);
											}
											status = Status.getStatusByLabel(content);
										} else if(WHO_NODE_NAME.equals(nodeName)) {
											if(who == null) who = new ArrayList<User>();
											who.add(User.valueOf(content));
										} else if(CUSTOM_NODE_NAME.equals(nodeName)) {
											if(custom==null) custom = new LinkedHashMap<String,String>();
											if(!elem.hasAttribute(CUSTOM_NAME_ATTRIBUTE_NAME)) {
												throw new ParseException(CUSTOM_NAME_ATTRIBUTE_NAME + " attribute missing from " + CUSTOM_NODE_NAME + " tag in " + resourceFile, 0);
											}
											String name = elem.getAttribute(CUSTOM_NAME_ATTRIBUTE_NAME);
											if(custom.containsKey(name)) {
												throw new ParseException("Duplicate " + CUSTOM_NAME_ATTRIBUTE_NAME + " attribute in " + CUSTOM_NODE_NAME + " tag in " + resourceFile + ": " + name, 0);
											}
											custom.put(name, content);
										} else if(COMMENTS_NODE_NAME.equals(nodeName)) {
											if(comments != null) {
												throw new ParseException("Multiple " + COMMENTS_NODE_NAME + " tag in " + resourceFile, 0);
											}
											comments = content;
										} else {
											throw new ParseException("Unexpected child element \"" + nodeName + "\" in " + resourceFile, 0);
										}
									}
								}
								
								Entry newEntry = new Entry(
									scheduledOns,
									on,
									status,
									who,
									custom,
									comments
								);
								// The entries must be in order by "on" value
								if(lastEntry!=null && newEntry.on.before(lastEntry.on)) {
									throw new ParseException("Entry not in order by \"on\": " + CalendarUtils.formatDate(newEntry.on) + " < " + CalendarUtils.formatDate(lastEntry.on) + " in " + resourceFile, 0);
								}
								lastEntry = newEntry;
								newEntries.add(newEntry);
							}
						}
					}
					unmodifiableEntries = Collections.unmodifiableList(newEntries);
					// Clear-out any cached values based on the old entries
					unmodifiableEntriesByScheduledOn = null;
					//unmodifiableProgressByScheduledOn = null;
					firstIncompleteFrom = null;
					firstIncompleteRecurring = null;
					firstIncompleteResult = null;
					// Update last modified time for cache
					entriesLastModified = fileLastModified;
				}
				return unmodifiableEntries;
			}
		} catch(ParserConfigurationException e) {
			throw new IOException(e);
		} catch(SAXException e) {
			throw new IOException(e);
		} catch(ParseException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Iterates through a snapshot of the entries.
	 * 
	 * @see  #getEntries()
	 */
	@Override
	public Iterator<Entry> iterator() {
		try {
			return getEntries().iterator();
		} catch(IOException e) {
			throw new WrappedException(e);
		}
	}

	/**
	 * Gets a snapshot of the entries grouped by "scheduledOn" value.
	 * Has a <code>null</code> key for any entries without a "scheduledOn" date.
	 * The cache key is the date in YYYY-MM-DD format.
	 * 
	 * @see  CalendarUtils#formatDate(java.util.Calendar)  for cache key formatting
	 */
	public Map<String,List<Entry>> getEntriesByScheduledOnDate() throws IOException {
		synchronized(entriesLock) {
			// Call getEntries always because it will refresh data when file changed
			List<Entry> allEntries = getEntries();
			if(unmodifiableEntriesByScheduledOn == null) {
				Map<String,List<Entry>> entriesByScheduledOn = new LinkedHashMap<String,List<Entry>>();
				for(Entry entry : allEntries) {
					for(Calendar scheduledOn : entry.getScheduledOns()) {
						String entryScheduledOnString = CalendarUtils.formatDate(scheduledOn);
						List<Entry> entriesScheduledOn = entriesByScheduledOn.get(entryScheduledOnString);
						if(entriesScheduledOn==null) entriesByScheduledOn.put(entryScheduledOnString, entriesScheduledOn=new ArrayList<Entry>());
						entriesScheduledOn.add(entry);
					}
				}
				// Convert each element to unmodifiable
				for(Map.Entry<String,List<Entry>> entry : entriesByScheduledOn.entrySet()) {
					entry.setValue(AoCollections.optimalUnmodifiableList(entry.getValue()));
				}
				unmodifiableEntriesByScheduledOn = Collections.unmodifiableMap(entriesByScheduledOn);
			}
			return unmodifiableEntriesByScheduledOn;
		}
	}

	/**
	 * Gets a snapshot of the "progress" dates grouped by "scheduledOn" value.
	 * Has a <code>null</code> key for any entries without a "scheduledOn" date.
	 * The cache key is the date in YYYY-MM-DD format.
	 * 
	 * @see  CalendarUtils#formatDate(java.util.Calendar)  for cache key formatting
	 */
	/*
	public Map<String,Set<String>> getProgressByScheduledOnDate() throws IOException {
		synchronized(entriesLock) {
			// Call getEntries always because it will refresh data when file changed
			List<Entry> allEntries = getEntries();
			if(unmodifiableProgressByScheduledOn == null) {
				Map<String,Set<String>> progressByScheduledOn = new LinkedHashMap<>();
				for(Entry entry : allEntries) {
					if(entry.getStatus() == Status.PROGRESS) {
						String entryScheduledOnString = CalendarUtils.formatDate(entry.getScheduledOn());
						Set<String> progressScheduledOn = progressByScheduledOn.get(entryScheduledOnString);
						if(progressScheduledOn==null) progressByScheduledOn.put(entryScheduledOnString, progressScheduledOn=new HashSet<>());
						progressScheduledOn.add(CalendarUtils.formatDate(entry.on));
					}
				}
				// Convert each element to unmodifiable
				for(Map.Entry<String,Set<String>> entry : progressByScheduledOn.entrySet()) {
					entry.setValue(AoCollections.optimalUnmodifiableSet(entry.getValue()));
				}
				unmodifiableProgressByScheduledOn = Collections.unmodifiableMap(progressByScheduledOn);
			}
			return unmodifiableProgressByScheduledOn;
		}
	}*/

	/**
	 * Gets the entries grouped by "scheduledOn" value or empty list if there are none.
	 * Supports <code>null</code> for all entries without a "scheduledOn" date.
	 */
	public List<Entry> getEntries(Calendar scheduledOn) throws IOException {
		String scheduledOnKey = CalendarUtils.formatDate(scheduledOn);
		List<Entry> entriesScheduledOn = getEntriesByScheduledOnDate().get(scheduledOnKey);
		if(entriesScheduledOn==null) return Collections.emptyList();
		return entriesScheduledOn;
	}

	/**
	 * Adds a new entry to the log, writing the new XML file immediately.
	 */
	public void addEntry(Entry entry) throws IOException {
		synchronized(entriesLock) {
			List<Entry> oldEntries = getEntries();
			List<Entry> newEntries = new ArrayList<Entry>(oldEntries.size() + 1);
			newEntries.addAll(oldEntries);
			newEntries.add(entry);
			commitChanges(newEntries);
		}
	}

	/**
	 * When the data has been updated, this is called to update the file on
	 * disk as well as the unmodifiable list of entries.
	 *
	 * @param  newEntries  this does not need to be unmodifiable, it will be wrapped automatically
	 */
	private void commitChanges(List<Entry> newEntries) throws IOException {
		assert Thread.holdsLock(entriesLock);
		if(true) throw new NotImplementedException("TODO: Write to file");
		unmodifiableEntries = Collections.unmodifiableList(newEntries);
		// Clear-out any cached values based on the old entries
		unmodifiableEntriesByScheduledOn = null;
		firstIncompleteFrom = null;
		firstIncompleteRecurring = null;
		firstIncompleteResult = null;
		// Update last modified time for cache
		File resourceFile = xmlFile.getResourceFile(
			true,
			true // Note: File must always exist after it was written
		); 
		entriesLastModified = resourceFile.lastModified();
	}

	/**
	 * Gets the most recent entry from the task log on the given date or <code>null</code> if none.
	 */
	public Entry getMostRecentEntry(Calendar scheduledOn) throws IOException {
		List<Entry> entries = getEntries(scheduledOn);
		int size = entries.size();
		return size==0 ? null : entries.get(size-1);
	}

	/**
	 * Gets the first incomplete scheduled on date.
	 */
	public UnmodifiableCalendar getFirstIncompleteScheduledOn(
		Calendar from,
		Recurring recurring
	) throws IOException {
		synchronized(entriesLock) {
			// Call getEntriesByScheduledOnDate always because it will refresh data when file changed
			Map<String,List<Entry>> entriesByScheduledOnDate = getEntriesByScheduledOnDate();
			if(
				firstIncompleteResult == null
				|| firstIncompleteFrom.getTimeInMillis() != from.getTimeInMillis()
				|| firstIncompleteRecurring == null
				|| !firstIncompleteRecurring.equals(recurring)
			) {
				Iterator<Calendar> scheduledOnIter = recurring.getScheduleIterator(from);
				while(true) {
					String date = CalendarUtils.formatDate(scheduledOnIter.next());
					List<Entry> dateEntries = entriesByScheduledOnDate.get(date);
					if(
						dateEntries==null
						|| !dateEntries.get(dateEntries.size()-1).getStatus().isCompletedSchedule()
					) {
						// Store in cache
						firstIncompleteFrom = UnmodifiableCalendar.wrap(from);
						firstIncompleteRecurring = recurring;
						firstIncompleteResult = UnmodifiableCalendar.wrap(CalendarUtils.parseDate(date));
						break;
					}
				}
			}
			assert firstIncompleteResult != null;
			return firstIncompleteResult;
		}
	}
}
