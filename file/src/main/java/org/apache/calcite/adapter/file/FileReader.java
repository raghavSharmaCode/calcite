/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.adapter.file;

import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.jsoup.select.Elements;

import java.io.File;

import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.Iterator;

/**
 * Scrapes HTML tables from URLs using Jsoup.
 */
public class FileReader implements Iterable<Elements> {

  private static final String DEFAULT_CHARSET = "UTF-8";

  private final URL url;
  private final String selector;
  private final Integer index;
  private final String charset = DEFAULT_CHARSET;
  private Element tableElement;
  private Elements headings;

  public FileReader(String url, String selector, Integer index)
      throws FileReaderException {
    if (url == null) {
      throw new FileReaderException("URL must not be null");
    }

    try {
      this.url = new URL(url);
    } catch (MalformedURLException e) {
      throw new FileReaderException("Malformed URL: '" + url + "'", e);
    }
    this.selector = selector;
    this.index = index;
  }

  public FileReader(String url, String selector) throws FileReaderException {
    this(url, selector, null);
  }

  public FileReader(String url) throws FileReaderException {
    this(url, null, null);
  }

  private void getTable() throws FileReaderException {

    Document doc;
    try {
      String proto = this.url.getProtocol();
      if (proto.equals("file")) {
        doc = Jsoup.parse(new File(this.url.getFile()), this.charset);
      } else {
        doc = Jsoup.connect(this.url.toString()).get();
      }
    } catch (IOException e) {
      throw new FileReaderException("Cannot read " + this.url.toString(), e);
    }

    this.tableElement = (this.selector != null && !this.selector.equals(""))
        ? getSelectedTable(doc, this.selector) : getBestTable(doc);

  }

  private Element getSelectedTable(Document doc, String selector)
      throws FileReaderException {
    // get selected elements
    Elements list = doc.select(selector);

    // get the element
    Element el;

    if (this.index == null) {
      if (list.size() != 1) {
        throw new FileReaderException("" + list.size()
            + " HTML element(s) selected");
      }

      el = list.first();
    } else {
      el = list.get(this.index);
    }

    // verify element is a table
    if (el.tag().getName().equals("table")) {
      return el;
    } else {
      throw new FileReaderException("selected (" + selector + ") element is a "
          + el.tag().getName() + ", not a table");
    }
  }

  private Element getBestTable(Document doc) throws FileReaderException {
    Element bestTable = null;
    int bestScore = -1;

    for (Element t : doc.select("table")) {
      int rows = t.select("tr").size();
      Element firstRow = t.select("tr").get(0);
      int cols = firstRow.select("th,td").size();
      int thisScore = rows * cols;
      if (thisScore > bestScore) {
        bestTable = t;
        bestScore = thisScore;
      }
    }

    if (bestTable == null) {
      throw new FileReaderException("no tables found");
    }

    return bestTable;
  }

  void refresh() throws FileReaderException {
    this.headings = null;
    getTable();
  }

  Elements getHeadings() throws FileReaderException {

    if (this.headings == null) {
      this.iterator();
    }

    return this.headings;
  }

  private String tableKey() {
    return "Table: {url: " + this.url + ", selector: " + this.selector;
  }

  public FileReaderIterator iterator() {
    if (this.tableElement == null) {
      try {
        getTable();
      } catch (Exception e) {
        // TODO: temporary hack
        throw new RuntimeException(e);
      }
    }

    FileReaderIterator iterator =
        new FileReaderIterator(this.tableElement.select("tr"));

    // if we haven't cached the headings, get them
    // TODO: this needs to be reworked to properly cache the headings
    //if (this.headings == null) {
    if (true) {
      // first row must contain headings
      Elements headings = iterator.next("th");
      // if not, generate some default column names
      if (headings.size() == 0) {
        // rewind and peek at the first row of data
        iterator = new FileReaderIterator(this.tableElement.select("tr"));
        Elements firstRow = iterator.next("td");
        int i = 0;
        headings = new Elements();
        for (Element td : firstRow) {
          Element th = td.clone();
          th.tagName("th");
          th.html("col" + i++);
          headings.add(th);
        }
        // rewind, so queries see the first row
        iterator = new FileReaderIterator(this.tableElement.select("tr"));
      }
      this.headings = headings;
    }

    return iterator;
  }

  public void close() {
  }

  /** Iterates over HTML tables, returning an Elements per row. */
  private class FileReaderIterator implements Iterator<Elements> {
    Iterator<Element> rowIterator;

    FileReaderIterator(Elements rows) {
      this.rowIterator = rows.iterator();
    }

    public boolean hasNext() {
      return this.rowIterator.hasNext();
    }

    Elements next(String selector) {
      Element row = this.rowIterator.next();

      return row.select(selector);
    }

    // return th and td elements by default
    public Elements next() {
      return next("th,td");
    }

    public void remove() {
      throw new UnsupportedOperationException("NFW - can't remove!");
    }
  }
}

// End FileReader.java