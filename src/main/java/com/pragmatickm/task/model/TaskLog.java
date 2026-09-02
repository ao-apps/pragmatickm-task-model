/*
 * pragmatickm-task-model - Tasks nested within SemanticCMS pages and elements.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2019, 2020, 2021, 2022, 2024, 2025, 2026  AO Industries, Inc.
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

import com.aoapps.collections.AoCollections;
import com.aoapps.hodgepodge.schedule.Recurring;
import com.aoapps.lang.NullArgumentException;
import com.semanticcms.core.model.PageRef;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.NotImplementedException;
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

  private static final String ROOT_NODE = "tasklog";
  private static final String ENTRY_NODE = "entry";
  private static final String SCHEDULED_ON_NODE = "scheduledOn";
  private static final String ON_NODE = "on";
  private static final String STATUS_NODE = "status";
  private static final String WHO_NODE = "who";
  private static final String CUSTOM_NODE = "custom";
  private static final String CUSTOM_NAME_ATTRIBUTE = "name";
  private static final String COMMENTS_NODE = "comments";

  public enum Status {
    /**
     * Progress has been made, but more action still must be taken.
     */
    PROGRESS(
      "Progress",
      "Progress waiting for \"Do Before\"",
      false
  ),
    /**
     * The task has been completed.
     */
    COMPLETED(
        "Completed",
        "Completed", // after \"Do Before\"",
        true
    ),
    /**
     * The task has been completed.
     */
    NOTHING_TO_DO(
        "Nothing To Do",
        "Nothing To Do after \"Do Before\"",
        true
    ),
    /**
     * The task was missed and will not be done.
     */
    MISSED(
        "Missed",
        "Missed after \"Do Before\"",
        true
    );

    public static Status getStatusByLabel(String label) {
      // Java 1.8: switch (label) {
      if ("Progress".equals(label)) {
        return PROGRESS;
      }
      if ("Completed".equals(label)) {
        return COMPLETED;
      }
      if ("Nothing To Do".equals(label)) {
        return NOTHING_TO_DO;
      }
      if ("Missed".equals(label)) {
        return MISSED;
      }
      throw new IllegalArgumentException("Unexpected status label: " + label);
    }

    private final String label;
    private final String labelDoBefore;
    private final boolean completedSchedule;

    private Status(
        String label,
        String labelDoBefore,
        boolean completedSchedule
    ) {
      this.label = label;
      this.labelDoBefore = labelDoBefore;
      this.completedSchedule = completedSchedule;
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

    /**
     * JavaBeans compatibility.
     */
    public String getName() {
      return name();
    }
  }

  public static class Entry {
    private final SortedSet<LocalDate> scheduledOns;
    private final LocalDate on;
    private final Status status;
    private final List<String> unmodifiableWho;
    private final Map<String, String> unmodifiableCustom;
    private final String comments;

    public Entry(
        SortedSet<LocalDate> scheduledOns,
        LocalDate on,
        Status status,
        List<String> who,
        Map<String, String> custom,
        String comments
    ) {
      if (scheduledOns == null) {
        this.scheduledOns = AoCollections.emptySortedSet();
      } else {
        this.scheduledOns = AoCollections.optimalUnmodifiableSortedSet(scheduledOns);
      }
      this.on = NullArgumentException.checkNotNull(on, "on");
      this.status = status;
      if (who == null) {
        this.unmodifiableWho = Collections.emptyList();
      } else {
        this.unmodifiableWho = AoCollections.unmodifiableCopyList(who);
        for (String user : this.unmodifiableWho) {
          if (!Task.isPerson(user)) {
            throw new IllegalArgumentException("Not a person: " + user);
          }
        }
      }
      if (custom == null) {
        this.unmodifiableCustom = Collections.emptyMap();
      } else {
        this.unmodifiableCustom = AoCollections.unmodifiableCopyMap(custom);
      }
      this.comments = comments;
    }

    /**
     * The "on" dates of the recurring schedule this entries is for, or
     * empty set if not applies to any schedules.  These are ordered by
     * time in milliseconds ascending.
     */
    public SortedSet<LocalDate> getScheduledOns() {
      return scheduledOns;
    }

    /**
     * The date this action was actually taken.  This may not necessarily
     * be on the scheduled date, but still counts status toward the scheduled date.
     */
    public LocalDate getOn() {
      return on;
    }

    public Status getStatus() {
      return status;
    }

    @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
    public List<String> getWho() {
      return unmodifiableWho;
    }

    @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
    public Map<String, String> getCustom() {
      return unmodifiableCustom;
    }

    public String getComments() {
      return comments;
    }
  }

  private static final Map<PageRef, TaskLog> taskLogCache = new HashMap<>();

  /**
   * To avoid repetitive parsing, only one {@link TaskLog} is created for each unique {@link PageRef}.
   */
  public static TaskLog getTaskLog(PageRef xmlFile) {
    synchronized (taskLogCache) {
      TaskLog taskLog = taskLogCache.get(xmlFile);
      if (taskLog == null) {
        taskLogCache.put(xmlFile, taskLog = new TaskLog(xmlFile));
      }
      return taskLog;
    }
  }

  private final PageRef xmlFile;

  private static class EntriesLock {
    // Empty lock class to help heap profile
  }

  private final EntriesLock entriesLock = new EntriesLock();
  private long entriesLastModified;
  private List<Entry> unmodifiableEntries;
  private Map<LocalDate, List<Entry>> unmodifiableEntriesByScheduledOn;
  // private Map<LocalDate, Set<String>> unmodifiableProgressByScheduledOn;
  private LocalDate firstIncompleteFrom;
  private Recurring firstIncompleteRecurring;
  private LocalDate firstIncompleteResult;

  private TaskLog(PageRef xmlFile) {
    this.xmlFile = xmlFile;
  }

  public PageRef getXmlFile() {
    return xmlFile;
  }

  /**
   * Not thread safe, use a different instance per thread.
   */
  private static final ThreadLocal<DocumentBuilderFactory> documentBuilderFactory = ThreadLocal.withInitial(() -> {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    try {
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    } catch (ParserConfigurationException e) {
      throw new AssertionError("All implementations are required to support the javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING feature.", e);
    }
    // See https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md#java
    // See https://rules.sonarsource.com/java/RSPEC-2755
    dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return dbf;
  });

  /**
   * Gets the set of all entries.  This is a snapshot view and will not change
   * even when the log has been updated.  To get a new snapshot, call this method
   * again.
   *
   * <p>Entries are in order by "on" time.</p>
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public List<Entry> getEntries() throws IOException {
    try {
      final File resourceFile = xmlFile.getResourceFile(true, false);
      // TODO: avoid locking and also only check every second (or so) for background changes?
      synchronized (entriesLock) {
        long fileLastModified = resourceFile.lastModified();
        if (
            // First access
            unmodifiableEntries == null
                // File updated externally
                || entriesLastModified != fileLastModified
        ) {
          List<Entry> newEntries = new ArrayList<>();
          Entry lastEntry = null;
          if (resourceFile.exists()) {
            DocumentBuilder builder = documentBuilderFactory.get().newDocumentBuilder();
            Document document = builder.parse(resourceFile);
            // http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
            Element root = document.getDocumentElement();
            root.normalize();
            if (!ROOT_NODE.equals(root.getNodeName())) {
              throw new ParseException("Unexpected root element \"" + root.getNodeName() + "\" in " + resourceFile, 0);
            }
            for (Node child = root.getFirstChild();
                child != null;
                child = child.getNextSibling()
            ) {
              if (child instanceof Element) {
                if (!ENTRY_NODE.equals(child.getNodeName())) {
                  throw new ParseException("Unexpected element \"" + child.getNodeName() + "\" in " + resourceFile, 0);
                }
                LocalDate lastScheduledOn = null;
                SortedSet<LocalDate> scheduledOns = null;
                LocalDate on = null;
                Status status = null;
                List<String> who = null;
                Map<String, String> custom = null;
                String comments = null;
                for (Node grandChild = child.getFirstChild();
                    grandChild != null;
                    grandChild = grandChild.getNextSibling()
                ) {
                  if (grandChild instanceof Element) {
                    Element elem = (Element) grandChild;
                    String content = elem.getTextContent();
                    String nodeName = elem.getNodeName();
                    // Java 1.8: switch (nodeName) {
                    if (SCHEDULED_ON_NODE.equals(nodeName)) {
                      if (scheduledOns == null) {
                        scheduledOns = new TreeSet<>();
                      }
                      LocalDate scheduledOn = content == null ? null : LocalDate.parse(content);
                      if (lastScheduledOn != null) {
                        // Must be in order
                        if (scheduledOn.compareTo(lastScheduledOn) <= 0) {
                          throw new ParseException("Out of order " + SCHEDULED_ON_NODE + ": " + scheduledOn
                              + " <= " + lastScheduledOn + " in " + resourceFile, 0);
                        }
                      }
                      lastScheduledOn = scheduledOn;
                      if (!scheduledOns.add(scheduledOn)) {
                        throw new ParseException("Duplicate " + SCHEDULED_ON_NODE + " value \"" + content + "\" in " + resourceFile, 0);
                      }
                    } else if (ON_NODE.equals(nodeName)) {
                      if (on != null) {
                        throw new ParseException("Multiple " + ON_NODE + " tag in " + resourceFile, 0);
                      }
                      on = content == null ? null : LocalDate.parse(content);
                    } else if (STATUS_NODE.equals(nodeName)) {
                      if (status != null) {
                        throw new ParseException("Multiple " + STATUS_NODE + " tag in " + resourceFile, 0);
                      }
                      status = Status.getStatusByLabel(content);
                    } else if (WHO_NODE.equals(nodeName)) {
                      if (who == null) {
                        who = new ArrayList<>();
                      }
                      who.add(content);
                    } else if (CUSTOM_NODE.equals(nodeName)) {
                      if (custom == null) {
                        custom = new LinkedHashMap<>();
                      }
                      if (!elem.hasAttribute(CUSTOM_NAME_ATTRIBUTE)) {
                        throw new ParseException(CUSTOM_NAME_ATTRIBUTE + " attribute missing from " + CUSTOM_NODE + " tag in " + resourceFile, 0);
                      }
                      String name = elem.getAttribute(CUSTOM_NAME_ATTRIBUTE);
                      if (custom.containsKey(name)) {
                        throw new ParseException("Duplicate " + CUSTOM_NAME_ATTRIBUTE + " attribute in " + CUSTOM_NODE + " tag in " + resourceFile + ": " + name, 0);
                      }
                      custom.put(name, content);
                    } else if (COMMENTS_NODE.equals(nodeName)) {
                      if (comments != null) {
                        throw new ParseException("Multiple " + COMMENTS_NODE + " tag in " + resourceFile, 0);
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
                if (lastEntry != null && newEntry.on.isBefore(lastEntry.on)) {
                  throw new ParseException("Entry not in order by \"on\": " + newEntry.on
                      + " < " + lastEntry.on + " in " + resourceFile, 0);
                }
                lastEntry = newEntry;
                newEntries.add(newEntry);
              }
            }
          }
          unmodifiableEntries = Collections.unmodifiableList(newEntries);
          // Clear-out any cached values based on the old entries
          unmodifiableEntriesByScheduledOn = null;
          // unmodifiableProgressByScheduledOn = null;
          firstIncompleteFrom = null;
          firstIncompleteRecurring = null;
          firstIncompleteResult = null;
          // Update last modified time for cache
          entriesLastModified = fileLastModified;
        }
        return unmodifiableEntries;
      }
    } catch (ParserConfigurationException | SAXException | ParseException e) {
      throw new IOException(e);
    }
  }

  /**
   * Iterates through a snapshot of the entries.
   *
   * @see  TaskLog#getEntries()
   */
  @Override
  public Iterator<Entry> iterator() {
    try {
      return getEntries().iterator();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final Collection<LocalDate> COLLECTION_WITH_ONE_NULL = Collections.singletonList(null);

  /**
   * Gets a snapshot of the entries grouped by "scheduledOn" value.
   * Has a <code>null</code> key for any entries without a "scheduledOn" date.
   */
  @SuppressWarnings("ReturnOfCollectionOrArrayField") // Returning unmodifiable
  public Map<LocalDate, List<Entry>> getEntriesByScheduledOnDate() throws IOException {
    synchronized (entriesLock) {
      // Call getEntries always because it will refresh data when file changed
      List<Entry> allEntries = getEntries();
      if (unmodifiableEntriesByScheduledOn == null) {
        Map<LocalDate, List<Entry>> entriesByScheduledOn = new LinkedHashMap<>();
        for (Entry entry : allEntries) {
          Collection<LocalDate> scheduledOns = entry.getScheduledOns();
          // Must always handle the "null" key for when there are not scheduled ons
          if (scheduledOns.isEmpty()) {
            scheduledOns = COLLECTION_WITH_ONE_NULL;
          }
          for (LocalDate scheduledOn : scheduledOns) {
            List<Entry> entriesScheduledOn = entriesByScheduledOn.get(scheduledOn);
            if (entriesScheduledOn == null) {
              entriesScheduledOn = new ArrayList<>();
              entriesByScheduledOn.put(scheduledOn, entriesScheduledOn);
            }
            entriesScheduledOn.add(entry);
          }
        }
        // Convert each element to unmodifiable
        for (Map.Entry<LocalDate, List<Entry>> entry : entriesByScheduledOn.entrySet()) {
          entry.setValue(AoCollections.optimalUnmodifiableList(entry.getValue()));
        }
        unmodifiableEntriesByScheduledOn = Collections.unmodifiableMap(entriesByScheduledOn);
      }
      return unmodifiableEntriesByScheduledOn;
    }
  }

  // /**
  //  * Gets a snapshot of the "progress" dates grouped by "scheduledOn" value.
  //  * Has a <code>null</code> key for any entries without a "scheduledOn" date.
  //  */
  // public Map<LocalDate, Set<String>> getProgressByScheduledOnDate() throws IOException {
  //   synchronized (entriesLock) {
  //     // Call getEntries always because it will refresh data when file changed
  //     List<Entry> allEntries = getEntries();
  //     if (unmodifiableProgressByScheduledOn == null) {
  //       Map<LocalDate, Set<String>> progressByScheduledOn = new LinkedHashMap<>();
  //       for (Entry entry : allEntries) {
  //         if (entry.getStatus() == Status.PROGRESS) {
  //           LocalDate entryScheduledOn = entry.getScheduledOn();
  //           Set<String> progressScheduledOn = progressByScheduledOn.get(entryScheduledOn);
  //           if (progressScheduledOn == null) {
  //             progressByScheduledOn.put(entryScheduledOn, progressScheduledOn=new HashSet<>());
  //           }
  //           progressScheduledOn.add(entry.on.toString());
  //         }
  //       }
  //       // Convert each element to unmodifiable
  //       for (Map.Entry<LocalDate, Set<String>> entry : progressByScheduledOn.entrySet()) {
  //         entry.setValue(AoCollections.optimalUnmodifiableSet(entry.getValue()));
  //       }
  //       unmodifiableProgressByScheduledOn = Collections.unmodifiableMap(progressByScheduledOn);
  //     }
  //     return unmodifiableProgressByScheduledOn;
  //   }
  // }

  /**
   * Gets the entries grouped by "scheduledOn" value or empty list if there are none.
   * Supports <code>null</code> for all entries without a "scheduledOn" date.
   */
  public List<Entry> getEntries(LocalDate scheduledOn) throws IOException {
    List<Entry> entriesScheduledOn = getEntriesByScheduledOnDate().get(scheduledOn);
    if (entriesScheduledOn == null) {
      return Collections.emptyList();
    }
    return entriesScheduledOn;
  }

  /**
   * Adds a new entry to the log, writing the new XML file immediately.
   */
  public void addEntry(Entry entry) throws IOException {
    synchronized (entriesLock) {
      List<Entry> oldEntries = getEntries();
      List<Entry> newEntries = new ArrayList<>(oldEntries.size() + 1);
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
    if (true) {
      throw new NotImplementedException("TODO: Write to file");
    }
    unmodifiableEntries = Collections.unmodifiableList(newEntries);
    // Clear-out any cached values based on the old entries
    unmodifiableEntriesByScheduledOn = null;
    firstIncompleteFrom = null;
    firstIncompleteRecurring = null;
    firstIncompleteResult = null;
    // Update last modified time for cache
    entriesLastModified = xmlFile.getResourceFile(
        true,
        true // Note: File must always exist after it was written
    ).lastModified();
  }

  /**
   * Gets the most recent entry from the task log on the given date or <code>null</code> if none.
   */
  public Entry getMostRecentEntry(LocalDate scheduledOn) throws IOException {
    List<Entry> entries = getEntries(scheduledOn);
    int size = entries.size();
    return size == 0 ? null : entries.get(size - 1);
  }

  /**
   * Gets the first incomplete scheduled on date.
   */
  public LocalDate getFirstIncompleteScheduledOn(
      LocalDate from,
      Recurring recurring
  ) throws IOException {
    synchronized (entriesLock) {
      // Call getEntriesByScheduledOnDate always because it will refresh data when file changed
      Map<LocalDate, List<Entry>> entriesByScheduledOnDate = getEntriesByScheduledOnDate();
      if (
          firstIncompleteResult == null
              || !firstIncompleteFrom.equals(from)
              || firstIncompleteRecurring == null
              || !firstIncompleteRecurring.equals(recurring)
      ) {
        Iterator<LocalDate> scheduledOnIter = recurring.getScheduleIterator(from);
        while (true) {
          LocalDate date = scheduledOnIter.next();
          List<Entry> dateEntries = entriesByScheduledOnDate.get(date);
          if (
              dateEntries == null
                  || !dateEntries.get(dateEntries.size() - 1).getStatus().isCompletedSchedule()
          ) {
            // Store in cache
            firstIncompleteFrom = from;
            firstIncompleteRecurring = recurring;
            firstIncompleteResult = date;
            break;
          }
        }
      }
      assert firstIncompleteResult != null;
      return firstIncompleteResult;
    }
  }
}
