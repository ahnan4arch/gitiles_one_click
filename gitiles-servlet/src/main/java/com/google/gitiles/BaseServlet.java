// Copyright 2012 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gitiles;

import static com.google.gitiles.FormatType.DEFAULT;
import static com.google.gitiles.FormatType.HTML;
import static com.google.gitiles.FormatType.JSON;
import static com.google.gitiles.FormatType.TEXT;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;

import org.joda.time.Instant;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Base servlet class for Gitiles servlets that serve Soy templates. */
public abstract class BaseServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final String DATA_ATTRIBUTE = BaseServlet.class.getName() + "/Data";

  static void setNotCacheable(HttpServletResponse res) {
    res.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate");
    res.setHeader(HttpHeaders.PRAGMA, "no-cache");
    res.setHeader(HttpHeaders.EXPIRES, "Fri, 01 Jan 1990 00:00:00 GMT");
    res.setDateHeader(HttpHeaders.DATE, new Instant().getMillis());
  }

  public static BaseServlet notFoundServlet() {
    return new BaseServlet(null) {
      private static final long serialVersionUID = 1L;
      @Override
      public void service(HttpServletRequest req, HttpServletResponse res) {
        res.setStatus(SC_NOT_FOUND);
      }
    };
  }

  public static Map<String, String> menuEntry(String text, String url) {
    if (url != null) {
      return ImmutableMap.of("text", text, "url", url);
    } else {
      return ImmutableMap.of("text", text);
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    FormatType format;
    try {
      format = FormatType.getFormatType(req);
    } catch (IllegalArgumentException err) {
      res.sendError(SC_BAD_REQUEST);
      return;
    }
    if (format == DEFAULT) {
      format = getDefaultFormat(req);
    }
    switch (format) {
      case HTML:
        doGetHtml(req, res);
        break;
      case TEXT:
        doGetText(req, res);
        break;
      case JSON:
        doGetJson(req, res);
        break;
      default:
        res.sendError(SC_BAD_REQUEST);
        break;
    }
  }

  /**
   * @param req in-progress request.
   * @return the default {@link FormatType} used when {@code ?format=} is not
   *     specified.
   */
  protected FormatType getDefaultFormat(HttpServletRequest req) {
    return HTML;
  }

  /**
   * Handle a GET request when the requested format type was HTML.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   */
  protected void doGetHtml(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    res.sendError(SC_BAD_REQUEST);
  }

  /**
   * Handle a GET request when the requested format type was plain text.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   */
  protected void doGetText(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    res.sendError(SC_BAD_REQUEST);
  }

  /**
   * Handle a GET request when the requested format type was JSON.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   */
  protected void doGetJson(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    res.sendError(SC_BAD_REQUEST);
  }

  protected static Map<String, Object> getData(HttpServletRequest req) {
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) req.getAttribute(DATA_ATTRIBUTE);
    if (data == null) {
      data = Maps.newHashMap();
      req.setAttribute(DATA_ATTRIBUTE, data);
    }
    return data;
  }

  protected final Renderer renderer;

  protected BaseServlet(Renderer renderer) {
    this.renderer = renderer;
  }

  /**
   * Put a value into a request's Soy data map.
   * <p>
   * This method is intended to support a composition pattern whereby a
   * {@link BaseServlet} is wrapped in a different {@link HttpServlet} that can
   * update its data map.
   *
   * @param req in-progress request.
   * @param key key.
   * @param value Soy data value.
   */
  public void put(HttpServletRequest req, String key, Object value) {
    getData(req).put(key, value);
  }

  /**
   * Render data to HTML using Soy.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   * @param key key.
   * @param templateName Soy template name; must be in one of the template files
   *     defined in {@link Renderer#SOY_FILENAMES}.
   */
  protected void renderHtml(HttpServletRequest req, HttpServletResponse res, String templateName,
      Map<String, ?> soyData) throws IOException {
    try {
      res.setContentType(FormatType.HTML.getMimeType());
      res.setCharacterEncoding(Charsets.UTF_8.name());
      setCacheHeaders(req, res);

      Map<String, Object> allData = getData(req);
      allData.putAll(soyData);
      GitilesView view = ViewFilter.getView(req);
      if (!allData.containsKey("repositoryName") && view.getRepositoryName() != null) {
        allData.put("repositoryName", view.getRepositoryName());
      }
      if (!allData.containsKey("breadcrumbs")) {
        allData.put("breadcrumbs", view.getBreadcrumbs());
      }

      res.setStatus(HttpServletResponse.SC_OK);
      renderer.render(res, templateName, allData);
    } finally {
      req.removeAttribute(DATA_ATTRIBUTE);
    }
  }

  /**
   * Render data to JSON using GSON.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   * @param src @see Gson#toJson(Object, Type, Appendable)
   * @param typeOfSrc @see Gson#toJson(Object, Type, Appendable)
   */
  protected void renderJson(HttpServletRequest req, HttpServletResponse res, Object src,
      Type typeOfSrc) throws IOException {
    res.setContentType(JSON.getMimeType());
    res.setCharacterEncoding("UTF-8");
    res.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment");
    setCacheHeaders(req, res);
    res.setStatus(SC_OK);

    PrintWriter writer = res.getWriter();
    new GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .setPrettyPrinting()
      .generateNonExecutableJson()
      .create()
      .toJson(src, typeOfSrc, writer);
    writer.print('\n');
    writer.close();
  }

  /**
   * Prepare the response to render plain text.
   * <p>
   * Unlike
   * {@link #renderHtml(HttpServletRequest, HttpServletResponse, String, Map)}
   * and
   * {@link #renderJson(HttpServletRequest, HttpServletResponse, Object, Type)},
   * which assume the data to render is already completely prepared, this method
   * does not write any data, only headers, and returns the response's
   * ready-to-use writer.
   *
   * @param req in-progress request.
   * @param res in-progress response.
   * @return the response's writer.
   */
  protected PrintWriter startRenderText(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    res.setContentType(TEXT.getMimeType());
    res.setCharacterEncoding("UTF-8");
    res.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment");
    setCacheHeaders(req, res);
    return res.getWriter();
  }

  protected void setCacheHeaders(HttpServletRequest req, HttpServletResponse res) {
    setNotCacheable(res);
  }
}
