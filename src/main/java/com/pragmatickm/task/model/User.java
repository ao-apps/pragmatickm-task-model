/*
 * pragmatickm-task-model - Tasks nested within SemanticCMS pages and elements.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2022  AO Industries, Inc.
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

public enum User {

  Unassigned(false),
  Amelia(true),
  Brian(true),
  Dan(true),
  Dennis(true),
  Kaori(true),
  Seth(true),
  Tabatha(true),
  Tyler(true);

  private final boolean isPerson;

  private User(boolean isPerson) {
    this.isPerson = isPerson;
  }

  public boolean isPerson() {
    return isPerson;
  }

  /**
   * JavaBeans compatibility.
   */
  public String getName() {
    return name();
  }
}
